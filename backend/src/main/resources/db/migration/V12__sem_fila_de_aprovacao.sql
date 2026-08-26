-- Fim do portão de aprovação (#83, D48).
--
-- SQL portável (ANSI) pelo mesmo motivo da V1: as mesmas migrations rodam em Postgres e em H2
-- no modo de compatibilidade PostgreSQL.

-- 1) Ninguém mais espera na fila.
--
-- Com o cadastro aberto (D47), aprovação não filtra ninguém — só atrasa. As contas que estavam
-- pendentes no dia do deploy são de gente que pediu acesso e ficou esperando o autor decidir:
-- liberar todas é a leitura certa da decisão nova, e deixá-las pendentes as trancaria para fora de
-- um app que já não tem quem as libere. `decided_at` recebe a hora da migration porque foi ela
-- que decidiu.
UPDATE app_user
   SET access_status = 'APROVADO',
       decided_at    = CURRENT_TIMESTAMP
 WHERE access_status = 'PENDENTE';

-- 2) A marca do resumo horário (D38) não tem mais resumo.
--
-- A coluna existia para uma pergunta só — "esta pendente já saiu num e-mail para o dono?" — e as
-- duas metades da pergunta saíram: não há pendentes e não há resumo. Sai a coluna, e não só o
-- código que a lia: coluna órfã num caminho de autenticação é a que alguém reaproveita para outra
-- coisa daqui a um ano.
ALTER TABLE app_user DROP COLUMN queue_notice_sent_at;

-- O que NÃO sai, e por quê:
--
-- `access_status` fica, agora só com 'APROVADO' em circulação. A D48 assume por escrito o risco de
-- um app de cadastro aberto sem forma de barrar conta abusiva; se isso incomodar, o bloqueio
-- reativo entra por esta coluna e pelo portão que já a lê, e não por uma fila de volta.
--
-- `requested_at` e `decided_at` ficam como registro de quando cada conta entrou e foi decidida.
-- São dado histórico de contas que existem, não estado de um fluxo que acabou.
--
-- Já os `login_token` de propósito 'ENTRADA' (V11) SAEM, e não por arrumação: o valor sumiu do
-- enum `LoginTokenPurpose`, e uma linha com valor que o enum não conhece explode na leitura, não na
-- escrita — o erro apareceria dias depois, quando alguém pedisse redefinição de senha e o app
-- carregasse os tokens pendentes daquela conta. Apagar não tira nada de ninguém: são links de um
-- fluxo que não existe mais e que já não autenticam.
DELETE FROM login_token WHERE purpose = 'ENTRADA';
