-- Gestão de usuários do sistema (#88 → #89 e #90, D49).
--
-- SQL portável (ANSI) pelo mesmo motivo da V1: as mesmas migrations rodam em Postgres e em H2
-- no modo de compatibilidade PostgreSQL.

-- 1) O papel vira dado.
--
-- Até aqui `Role` saía de `fos.auth.owner-emails` a cada requisição (D48), e a consequência era
-- que administrador novo exigia deploy. A coluna nasce com default e é preenchida para TODA linha
-- antes de virar NOT NULL — a armadilha que a V12 documenta é sobre a LEITURA: valor que o enum
-- não conhece (aqui, nulo) estoura quando alguém carrega a conta, não quando a migration roda.
--
-- Ninguém nasce ADMIN por esta migration, e isso é deliberado: quem está em `owner-emails` é
-- promovido na subida da aplicação, onde a lista existe e onde dá para exigir e-mail verificado.
-- SQL não enxerga variável de ambiente, e um backfill que chutasse a lista promoveria errado.
ALTER TABLE app_user ADD COLUMN role VARCHAR(16) DEFAULT 'USUARIO';
UPDATE app_user SET role = 'USUARIO' WHERE role IS NULL;
ALTER TABLE app_user ALTER COLUMN role SET NOT NULL;

-- 2) Trilha de quem mexeu no papel.
--
-- Par próprio, e não reaproveitamento de `decided_at`/`decided_by`: são duas decisões diferentes
-- sobre a mesma conta ("virou administrador" e "foi bloqueada"), e guardá-las na mesma coluna
-- faria a segunda apagar a primeira — justo no dado que existe para responder "quem fez isso?".
ALTER TABLE app_user ADD COLUMN role_changed_at TIMESTAMP;
ALTER TABLE app_user ADD COLUMN role_changed_by BIGINT;

-- 3) Trilha do bloqueio.
--
-- `decided_at` já existe desde a V1 e passa a ter de novo quem o escreva: o bloqueio reativo por
-- `RECUSADO` que a D48 deixou montado sem produtor. `decided_by` diz quem decidiu e
-- `decided_reason` por quê — nas duas direções, porque desbloquear também é decisão.
--
-- Sem FK para `app_user`: quem decidiu pode excluir a própria conta depois (`DELETE /api/me`), e
-- uma FK transformaria a exclusão do administrador em erro de integridade ou apagaria a trilha
-- junto. O id aqui é registro histórico, não referência viva.
ALTER TABLE app_user ADD COLUMN decided_by BIGINT;
ALTER TABLE app_user ADD COLUMN decided_reason VARCHAR(500);
