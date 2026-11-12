-- Próximo grau (#193, D75): aula com ou sem kimono, e a meta de aulas desde o último grau.
--
-- SQL portável (ANSI) pelo mesmo motivo da V1: as mesmas migrations rodam em Postgres e em H2 no
-- modo de compatibilidade PostgreSQL.

-- 1) Com ou sem kimono, por sessão.
--
-- ANULÁVEL, e é o que dispensa backfill: sessão anterior a esta coluna fica "não informado", e
-- continua contando como aula no total. GI | NOGI. Só existe para o que acontece no tatame (AULA,
-- DRILL, ROLA, OUTRO); FISICO e DESCANSO ficam sempre nulos, e quem garante é o serviço.
--
-- Não é conteúdo No-Gi (D6 segue de pé): o currículo continua só Gi. É o registro de como foi a
-- aula, porque academia conta aula com e sem kimono separado.
ALTER TABLE training_session ADD COLUMN attire VARCHAR(8);

-- 2) A meta do próximo grau, uma linha por conta.
--
-- Os números vêm de FORA: são o que a academia de quem usa exige, digitados pela própria pessoa.
-- O app não traz lista nem mínimo de academia nenhuma — material de academia costuma ser
-- protegido, e o repositório é público (D75). Todos os mínimos são opcionais: há academia que
-- não separa com e sem kimono, e há quem só queira contar.
--
-- Dado da conta e de mais ninguém: `DELETE /api/me` leva junto, e a coleta da D50 e o painel da
-- D52 nunca leem esta tabela.
CREATE TABLE graduation_goal (
    user_id            BIGINT      NOT NULL PRIMARY KEY REFERENCES app_user (id),
    -- O começo da contagem. Inclusivo: a aula do dia em que a faixa foi amarrada conta para o
    -- próximo grau, e não para o que acabou de ser dado.
    last_graduation_on DATE        NOT NULL,
    current_label      VARCHAR(60),
    next_label         VARCHAR(60),
    min_total          INTEGER,
    min_gi             INTEGER,
    min_nogi           INTEGER,
    updated_at         TIMESTAMP   NOT NULL
);
