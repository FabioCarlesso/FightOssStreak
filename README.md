# FightOssStreak (FOS)

Ferramenta pessoal de **revisão e retenção** do que é aprendido no tatame, com mecânicas de
gamificação: currículo em árvore, quiz, streak e repetição espaçada. Você registra o treino; o app
devolve **o que drillar hoje**.

> ⚠️ Este projeto não ensina jiu-jitsu e não substitui instrução presencial com professor
> qualificado. Ver o [aviso completo](#aviso-de-responsabilidade).

## O que já funciona

MVP web ponta a ponta: árvore de currículo com desbloqueio progressivo, quiz conceitual corrigido no
servidor, registro de drill, **diário de treino**, anotações por nó, streak com heatmap e agenda de
revisão por repetição espaçada. A raiz (`/`) é a landing pública; o app começa em `/hoje`.

| Camada | Estado |
|---|---|
| Currículo (46 nós, 9 módulos) | Transcrito como dado versionado em `backend/src/main/resources/curriculum/` |
| Quiz conceitual | Escrito para **M0 a M3** (25 nós, 91 perguntas). Os outros 21 nós, de M4 a M8, estão pendentes de curadoria |
| Vídeos do YouTube | **M0 e M1 catalogados (11/46)** pelo script, pendentes de conferência assistindo ([D21](docs/decisoes/D21-primeira-leva-de-videos-m0-e.md)). M2–M8 seguem sem vídeo — ver [instruções](backend/src/main/resources/curriculum/README.md) |
| Clipes complementares | **7 clipes** da própria academia em M1.3, M1.5 e M1.6 (D32). O canônico ensina, o clipe lembra — no máximo 4 por nó |
| Anotações por nó | Anotação fixada junto ao conceito, mais o histórico do que foi anotado a cada drill (#45) |
| Diário de treino | Sessão como unidade em `/diario`: data, tipo, duração, peso, sensação e textos, com vínculo **opcional** de técnicas do currículo (#114, D56). Só a data é obrigatória. Técnica vinculada é o mesmo `drill_log` de sempre, e o streak passa a contar dia com registro — `DESCANSO` não conta (D58). Peso e sensação são guardados e mostrados, nunca interpretados (D57) |
| Contas | **Cadastro aberto** com e-mail e senha, confirmado por link, mais login por Google e Facebook (D47/D48). Exclusão de conta incluída. Apple pendente |
| Demonstração pública | Um botão na landing abre o app numa **conta temporária** com dados de exemplo, que grava de verdade e some em duas horas (D39). Depende de `FOS_DEMO_TEMPLATE_EMAIL` |
| Backend | Spring Boot + Flyway + Spring Security, API documentada em OpenAPI |
| Web | React + Vite |
| Landing | Pública em `/`, estática e sem chamada de API, com prints das telas reais (D33) |
| Mobile | **MVP em Expo** (D67, D69, #141): login por senha, Hoje, Árvore e Nó (vídeo e quiz), Diário e exclusão de conta, com as mesmas regras do web. Google e Apple depois da #143 — ver [como rodar](docs/desenvolvimento/mobile.md) |

**Stack:** Spring Boot 3 (Java 21) · Postgres + Flyway · React + Vite (TypeScript) · nginx · Docker
— detalhes em [`docs/arquitetura.md`](docs/arquitetura.md).

## Como rodar

**Com Docker** (só precisa de Docker):

```bash
docker compose up --build
```

Abra <http://localhost:8081>. Sobe Postgres, backend e web (nginx), na mesma topologia de produção.
Portas, logs e deploy na Railway em [`docs/deploy.md`](docs/deploy.md).

**Modo dev** (Java 21 + Node 22, com hot reload):

```bash
npm install
npm run dev:backend   # terminal 1 — API em :8080, H2 em memória
npm run dev:web       # terminal 2 — web em :5173, proxy de /api
```

O app exige login, e `localhost` não tem provedor nem envio de e-mail: para entrar, use as contas de
teste (`node scripts/seed-dev-users.mjs` e `node scripts/mint-dev-login.mjs aluno@teste.local`). O
roteiro está em [`docs/desenvolvimento/`](docs/desenvolvimento/README.md).

**Testes e lint:**

```bash
npm test && npm run lint                          # web, shared e scripts
cd backend && ./mvnw spotless:check && ./mvnw test   # backend
```

## Documentação

| Documento | Papel |
|---|---|
| [`docs/README.md`](docs/README.md) | Índice da documentação e **onde documentar cada mudança** |
| [`docs/arquitetura.md`](docs/arquitetura.md) · [`docs/api.md`](docs/api.md) · [`docs/regras-de-negocio.md`](docs/regras-de-negocio.md) | Como o sistema é: componentes, rotas e regras |
| [`docs/autenticacao.md`](docs/autenticacao.md) · [`docs/seguranca.md`](docs/seguranca.md) | Contas, papéis, proxy, cookie e cabeçalhos |
| [`docs/configuracao.md`](docs/configuracao.md) · [`docs/deploy.md`](docs/deploy.md) · [`docs/operacao.md`](docs/operacao.md) | Variáveis, Compose e Railway, monitoramento |
| [`docs/privacidade/`](docs/privacidade/README.md) | O que se guarda de cada pessoa, e como apagar |
| [`docs/produto/`](docs/produto/visao.md) · [`docs/conteudo/`](docs/conteudo/videos.md) | Visão, currículo, MVP, fontes e vídeos |
| [`docs/decisoes/`](docs/decisoes/README.md) | Log de decisões (D1…) com o porquê de cada uma |
| [`CLAUDE.md`](CLAUDE.md) | Regras que não se negociam, para quem (ou o que) mexe no código |

## Próximos passos

1. **Assistir aos 11 vídeos de M0 e M1 e confirmar o encaixe** — a única etapa que não se
   automatiza (D21). Critérios por nó em
   [`docs/conteudo/videos-por-no.md`](docs/conteudo/videos-por-no.md);
   para trocar um vídeo, `node scripts/catalogar-video.mjs <NÓ> <url>`, que verifica e credita o
   canal automaticamente. Os já catalogados são reconferidos semanalmente pelo workflow `videos`
   (`node scripts/verificar-videos.mjs`), que avisa quando um sai do ar
2. Catalogar os vídeos de M2–M8 e escrever o quiz conceitual de M4–M8 (M2 e M3 já têm quiz)
3. Usar por 30 dias e avaliar contra os [critérios de sucesso](docs/produto/mvp-web.md)
4. O mobile começou por objetivo próprio, sem esperar este critério (D67, que revisitou a D4)

## Contribuindo

`main` é protegida: toda mudança entra por pull request, e o merge só libera com os jobs `backend` e
`web` verdes. Fluxo em [`docs/desenvolvimento/`](docs/desenvolvimento/README.md#fluxo-de-pr) e
regras em [`docs/repositorio.md`](docs/repositorio.md).

## Aviso de responsabilidade

> **AVISO IMPORTANTE — LEIA ANTES DE USAR**
>
> O FightOssStreak é uma ferramenta de **organização e revisão de estudos**, destinada a complementar o treino presencial de jiu-jitsu brasileiro em academia, sob supervisão de professor qualificado.
>
> **Este aplicativo não ensina jiu-jitsu e não substitui instrução presencial.** O conteúdo aqui é de caráter estritamente informativo e instrucional de apoio. Jiu-jitsu é uma atividade física de contato que envolve técnicas de imobilização, torção articular e estrangulamento, com risco real de lesão grave.
>
> **Nunca pratique as técnicas referenciadas neste aplicativo:** sem supervisão de um professor qualificado; fora de um ambiente adequado de treino; com parceiro que não tenha consentido e não conheça os riscos; sem aquecimento e condicionamento adequados.
>
> **Técnicas de estrangulamento podem causar perda de consciência, lesão neurológica ou morte.** Técnicas de torção articular podem causar lesão permanente. Sempre respeite o toque (tap) do parceiro imediatamente.
>
> Consulte um médico antes de iniciar qualquer atividade física, especialmente se você tem condição pré-existente ou histórico de lesão.
>
> O autor e os colaboradores deste aplicativo **não se responsabilizam** por qualquer lesão, dano ou prejuízo decorrente do uso das informações aqui contidas. Ao usar este aplicativo, você reconhece que assume integralmente os riscos da prática.
>
> Os vídeos referenciados são conteúdo de terceiros, incorporados a partir do YouTube. Não somos autores desse conteúdo e não temos vínculo com seus criadores.

## Licença

MIT — ver [`LICENSE`](LICENSE).
