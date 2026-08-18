-- Vídeos complementares por nó (issue #41, D32).
--
-- Por que tabela e não mais colunas em `node`: `video_*` é um `@Embeddable` de colunas fixas, que
-- comporta exatamente um vídeo. Complementar é lista — o material que motivou o campo são clipes
-- curtos da própria academia, e um mesmo nó recebe dois ou três ângulos do mesmo golpe.
--
-- `position` existe porque a ordem é curatorial: ela vem do JSON versionado (D11) e é o que decide
-- qual clipe aparece primeiro na tira. Sem coluna de ordem, `@ElementCollection` não garante nada.
CREATE TABLE node_extra_video (
    node_id       BIGINT       NOT NULL REFERENCES node (id),
    position      INT          NOT NULL,
    youtube_id    VARCHAR(32)  NOT NULL,
    title         VARCHAR(300) NOT NULL,
    -- Crédito ao canal é política de uso de vídeo (D7), não metadado opcional.
    channel       VARCHAR(200) NOT NULL,
    start_seconds INT,
    -- VERTICAL | HORIZONTAL. Preenchido pelo script a partir do formato real do vídeo, nunca à
    -- mão: clipe de celular é 9:16 e entra torto num frame 16:9.
    orientation   VARCHAR(16)  NOT NULL DEFAULT 'HORIZONTAL',
    -- Anotação do curador, nas palavras dele. É o único campo do bloco que NÃO vem do YouTube:
    -- os títulos são taquigrafia de aula ("aula do dia 09/05") e não dizem por que o clipe importa.
    note          VARCHAR(300),
    PRIMARY KEY (node_id, position)
);
