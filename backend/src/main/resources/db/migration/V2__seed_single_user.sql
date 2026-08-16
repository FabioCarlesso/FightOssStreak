-- Usuário único do MVP (D9: sem login nesta fase).
-- Quando houver contas de verdade, esta linha vira apenas o primeiro registro da tabela
-- e o CurrentUserProvider passa a resolver o usuário pela autenticação.
INSERT INTO app_user (id, label, created_at)
VALUES (1, 'usuario-local', CURRENT_TIMESTAMP);
