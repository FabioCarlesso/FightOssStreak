#!/usr/bin/env node
/**
 * Semeia duas contas de teste (comum e dona) no Postgres LOCAL do docker-compose.
 *
 *   docker compose up -d db
 *   cd backend && FOS_DB_URL=jdbc:postgresql://localhost:5432/fos FOS_DB_USER=fos \
 *     FOS_DB_PASSWORD=fos ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
 *   # deixa aplicar as migrations do Flyway, derruba com Ctrl+C — só precisa rodar uma vez
 *   node scripts/seed-dev-users.mjs
 *   node scripts/mint-dev-login.mjs aluno@teste.local
 *
 * Por que não em backend/src/main/resources/db/migration: qualquer arquivo ali roda automático em
 * todo startup do Flyway, em todo ambiente — inclusive produção no próximo deploy. Este script só
 * roda quando alguém o invoca à mão contra o container `fos-db`, que só existe na sua máquina —
 * produção usa um Postgres gerenciado à parte, sem rede nem credencial em comum com ele.
 *
 * Idempotente: rodar de novo não duplica conta nem identidade.
 *
 * Contas criadas, já com acesso APROVADO (sem senha, sem provedor real):
 *   aluno@teste.local — conta comum
 *   dono@teste.local  — conta dona (bata com FOS_OWNER_EMAILS pra herdar o poder de dono)
 *
 * O login em si é emitido à parte por scripts/mint-dev-login.mjs — o token de entrada expira em
 * 15 minutos (mesma regra da entrada por e-mail de verdade), então não faz sentido fixá-lo aqui.
 *
 * Códigos de saída:
 *   0  contas prontas (novas ou já existentes)
 *   2  pré-requisito faltando (container fora do ar, ou schema ainda não migrado)
 */
import { execFileSync } from 'node:child_process';

const CONTAINER = 'fos-db';
const CONTAS = [
  { email: 'aluno@teste.local', label: 'Aluno Teste' },
  { email: 'dono@teste.local', label: 'Dono Teste' },
];

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

function containerNoAr() {
  try {
    execFileSync('docker', ['exec', CONTAINER, 'true']);
    return true;
  } catch {
    return false;
  }
}

function schemaMigrado() {
  return psql("SELECT to_regclass('public.app_user')").trim() !== '';
}

function seedConta({ email, label }) {
  const jaExiste = psql(
    `SELECT 1 FROM user_identity WHERE provider = 'email' AND provider_subject = '${email}'`,
  ).trim();
  if (jaExiste) {
    console.log(`já existia: ${email}`);
    return;
  }
  psql(`
    WITH nova_conta AS (
      INSERT INTO app_user (label, created_at, access_status, requested_at, decided_at)
      VALUES ('${label}', now(), 'APROVADO', now(), now())
      RETURNING id
    )
    INSERT INTO user_identity
      (user_id, provider, provider_subject, email, email_verified, display_name, created_at, last_login_at)
    SELECT id, 'email', '${email}', '${email}', true, '${label}', now(), now() FROM nova_conta;
  `);
  console.log(`criada: ${email}`);
}

if (!containerNoAr()) {
  console.error(`Container ${CONTAINER} não está no ar. Rode: docker compose up -d db`);
  process.exit(2);
}

if (!schemaMigrado()) {
  console.error(
    'Tabela app_user não existe ainda. Suba o backend uma vez com o perfil postgres para o ' +
      'Flyway migrar o schema (ver o cabeçalho deste arquivo) e rode de novo.',
  );
  process.exit(2);
}

for (const conta of CONTAS) {
  seedConta(conta);
}
