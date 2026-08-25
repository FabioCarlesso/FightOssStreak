#!/usr/bin/env node
/**
 * Emite um link de entrada válido para uma conta semeada por scripts/seed-dev-users.mjs, sem
 * precisar de provedor OAuth nem de FOS_EMAIL_API_KEY configurada.
 *
 *   node scripts/mint-dev-login.mjs aluno@teste.local
 *   node scripts/mint-dev-login.mjs dono@teste.local
 *
 * Fala só com o Postgres LOCAL do docker-compose (`fos-db`, via `docker exec`) — nunca alcança
 * produção, que vive num Postgres gerenciado à parte, sem rede nem credencial em comum com este.
 *
 * É um token de **confirmação de e-mail** (D47): o mesmo caminho que o cadastro real percorre, só
 * que sem o e-mail no meio. Ele abre a sessão e vale 24h, como o de produção. Rodar de novo emite
 * outro; o que expira é o link, nunca a conta.
 *
 * Códigos de saída:
 *   0  link emitido, impresso em stdout
 *   1  conta inexistente (rode seed-dev-users.mjs primeiro)
 *   2  pré-requisito faltando (container fora do ar)
 */
import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';

const CONTAINER = 'fos-db';
const email = process.argv[2];

if (!email) {
  console.error('uso: node scripts/mint-dev-login.mjs <email>');
  process.exit(2);
}

function psql(sql) {
  return execFileSync(
    'docker',
    [
      'exec',
      '-i',
      CONTAINER,
      'psql',
      '-U',
      'fos',
      '-d',
      'fos',
      '-v',
      'ON_ERROR_STOP=1',
      '-tA',
      '-c',
      sql,
    ],
    { encoding: 'utf8' },
  );
}

try {
  execFileSync('docker', ['exec', CONTAINER, 'true']);
} catch {
  console.error(`Container ${CONTAINER} não está no ar. Rode: docker compose up -d db`);
  process.exit(2);
}

const userId = psql(
  `SELECT user_id FROM user_identity WHERE provider = 'password' AND provider_subject = '${email}'`,
).trim();

if (!userId) {
  console.error(`Nenhuma conta com e-mail ${email}. Rode scripts/seed-dev-users.mjs primeiro.`);
  process.exit(1);
}

const token = randomBytes(24).toString('base64url');
const hash = createHash('sha256').update(token).digest('hex');

psql(
  `INSERT INTO login_token (user_id, token_hash, purpose, created_at, expires_at) ` +
    `VALUES (${Number(userId)}, '${hash}', 'VERIFICACAO', now(), now() + interval '24 hours');`,
);

console.log(`http://localhost:5173/api/auth/verificar/${token}`);
