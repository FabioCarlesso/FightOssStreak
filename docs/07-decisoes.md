# Log de Decisões

Registro de decisões com justificativa, para que o "porquê" não se perca — e para que reversões futuras sejam conscientes.

| # | Decisão | Justificativa | Revisar quando |
|---|---|---|---|
| D1 | **Posicionamento: revisão, não ensino** | Jiu-jitsu é habilidade motora; quiz não constrói habilidade motora. O problema real é esquecer na sexta o que se aprendeu na segunda | — (decisão estruturante) |
| D2 | **React (web) → React Native (mobile)** | Um código-base, reaproveita TypeScript, curva menor que Flutter/nativo | Se surgir necessidade de UI com performance de jogo |
| D3 | **Backend Spring Boot + Postgres** | Stack dominada; sem motivo para experimentar aqui | — |
| D4 | **Web-first, mobile depois** | Elimina fricção de loja na fase de validação; custo zero | Após critérios de sucesso do MVP serem atingidos |
| D5 | **Monorepo** | Projeto solo; `shared/` justifica o formato | Se houver mais de um contribuidor ativo |
| D6 | **Somente Gi nesta versão** | Reduz escopo do currículo pela metade; No-Gi tem árvore parcialmente distinta | Depois do MVP validado |
| D7 | **Vídeo via embed do YouTube** | Elimina custo de CDN, produção e o problema de autoridade técnica | Se o embed limitar a experiência ou se houver conteúdo próprio |
| D8 | **Sem monetização; licença MIT** | Projeto pessoal; não há interesse comercial no momento | Se houver adoção fora do círculo pessoal |
| D9 | **Sem login no MVP** | Usuário único (o autor). Login traz obrigação de deleção de conta (Apple) e complexidade de schema | Ao abrir para outros usuários |
| D10 | **Sobrevivência antes de ataque na árvore** | Versão anterior ensinava armlock antes de fuga de montada — invertia a pedagogia real de faixa branca | Na validação com faixa-preta |
| D11 | **Currículo como dado versionado, não código** | Alterar a árvore vira PR revisável; facilita a futura validação por um graduado | — |
| D12 | **Validação com faixa-preta adiada** | Uso é pessoal; validar antes de existir produto é prematuro | Antes de qualquer distribuição pública |

## Política de uso de vídeo (D7) — limites

**Permitido:** incorporar (embed) vídeos públicos usando o player oficial do YouTube. O criador mantém visualizações e monetização; é o uso previsto pela plataforma.

**Não fazer:**
- Baixar e re-hospedar vídeos de terceiros
- Extrair trechos e recortar em clipes próprios
- Remover ou encobrir a marca do player
- Incorporar vídeos marcados como não-incorporáveis pelo autor

**Boa prática:** creditar canal e autor visivelmente em cada nó. Além de correto, é o que preserva a relação caso o projeto cresça.

## Riscos conhecidos em aberto

| Risco | Impacto | Estado |
|---|---|---|
| Currículo montado sem revisão de graduado | Pode ensinar ordem ou conceito errado | Aceito conscientemente enquanto o uso for pessoal (D12) |
| Vídeos do YouTube saem do ar / ficam privados | Nós ficam sem referência | Não mitigado — considerar checagem periódica de disponibilidade |
| Gamificação sustentar-se sozinha sem gerar aprendizado | Produto vira streak vazio | Coberto pelo critério de falha em `05-mvp-web-plano.md` |
| App ser visto como wrapper de YouTube na review da Apple | Rejeição | Fase futura; mitigação em `02` |
