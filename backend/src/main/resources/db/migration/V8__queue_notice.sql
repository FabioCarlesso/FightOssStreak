-- Resumo horário da fila de solicitações (#54).
--
-- SQL portável (ANSI) pelo mesmo motivo da V1: as mesmas migrations rodam em Postgres e em H2
-- no modo de compatibilidade PostgreSQL.

-- Quando esta conta pendente já entrou num resumo enviado ao dono. NULL significa "ainda não
-- anunciada", e é essa coluna — e não um relógio global — que decide se a janela da hora tem
-- alguma novidade para mandar. Marcar por linha sobrevive a reinício e a deploy: o estado do
-- aviso vive junto do pedido que ele anuncia.
--
-- Fica nula para conta de provedor, que nunca passa pela fila (D37), e nula nas pendentes que
-- já existirem no dia deste deploy — de propósito: elas entram no primeiro resumo, que é o
-- comportamento certo para uma fila que ninguém tinha visto ainda.
ALTER TABLE app_user ADD COLUMN queue_notice_sent_at TIMESTAMP;
