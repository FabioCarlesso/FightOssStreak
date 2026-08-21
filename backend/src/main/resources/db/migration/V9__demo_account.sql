-- Conta de demonstração descartável, clonada da conta-modelo (#62).
--
-- SQL portável (ANSI) pelo mesmo motivo da V1: as mesmas migrations rodam em Postgres e em H2
-- no modo de compatibilidade PostgreSQL.

-- Prazo de vida da conta. NULL é o estado de toda conta que já existe e de toda conta de
-- gente de verdade: "isto não é uma demonstração". Uma coluna nova em `app_user`, e não uma
-- tabela de sessões de demonstração, porque o que expira é a CONTA inteira — passado o prazo
-- não sobra sessão, progresso nem identidade para guardar em lugar nenhum.
--
-- Sem índice de propósito: a varredura roda no momento de criar uma demonstração nova e o
-- universo são as poucas contas de demonstração vivas, não a tabela toda.
ALTER TABLE app_user ADD COLUMN demo_expires_at TIMESTAMP;
