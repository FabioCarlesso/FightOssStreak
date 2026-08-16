# FightOssStreak (FOS)

Ferramenta pessoal de revisão e retenção do que é aprendido no tatame, com mecânicas de gamificação (currículo em árvore, quiz, streak, repetição espaçada).

> ⚠️ Este projeto não ensina jiu-jitsu e não substitui instrução presencial com professor qualificado. Ver aviso completo em [`docs/06-disclaimer-responsabilidade.md`](docs/06-disclaimer-responsabilidade.md).

## O que já funciona

MVP web ponta a ponta: árvore de currículo com desbloqueio progressivo, detalhe do nó com conceito e quiz conceitual corrigido no servidor, registro de drill, streak e agenda de revisão por repetição espaçada.

| Camada | Estado |
|---|---|
| Currículo (46 nós, 9 módulos) | Transcrito como dado versionado em `backend/src/main/resources/curriculum/` |
| Quiz conceitual | Escrito para **M0 e M1** (11 nós, 35 perguntas). Os outros 35 nós estão pendentes de curadoria |
| Vídeos do YouTube | **Nenhum catalogado ainda** — é curadoria humana, ver [instruções](backend/src/main/resources/curriculum/README.md) |
| Backend | Spring Boot + Flyway, API documentada em OpenAPI |
| Web | React + Vite |
| Mobile | Não iniciado (fase posterior, D4) |

## Rodando

Requer **Java 21** e **Node 22**.

```bash
npm install

# terminal 1 — API em :8080 (perfil dev, H2 em memória, sem precisar de Docker)
npm run dev:backend

# terminal 2 — web em :5173, com proxy de /api
npm run dev:web
```

Abra <http://localhost:5173>. Na primeira abertura o app pede aceite do aviso de responsabilidade.

Documentação da API: <http://localhost:8080/swagger-ui.html>

### Com Postgres

O perfil `dev` usa H2 em memória — os dados somem ao reiniciar. Para persistir:

```bash
docker compose up -d db
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Testes

```bash
npm test                    # regras puras de shared/domain (streak, SRS, desbloqueio)
cd backend && ./mvnw test   # regras, integridade do currículo e fluxo de ponta a ponta
npm run typecheck           # todos os workspaces TypeScript
```

O teste de integridade do currículo falha se houver ciclo de pré-requisitos, referência quebrada, código duplicado ou quiz malformado — é o que torna seguro editar a árvore em um PR.

## Estrutura

```
backend/   Spring Boot + Postgres — fonte da verdade de progresso e SRS
  └── src/main/resources/curriculum/   currículo versionado como dado (D11)
web/       React + Vite — MVP
shared/    domain (regras puras), api-client, types (GERADOS do OpenAPI)
docs/      planejamento e log de decisões
```

Detalhes em [`docs/03-estrutura-projeto.md`](docs/03-estrutura-projeto.md) e [`CLAUDE.md`](CLAUDE.md).

## Próximos passos

1. **Catalogar vídeos do YouTube** para os nós de M0 e M1 — é o gargalo real do projeto
2. Escrever o quiz conceitual dos módulos M2–M8
3. Usar por 30 dias e avaliar contra os [critérios de sucesso](docs/05-mvp-web-plano.md)
4. Só então considerar mobile (D4)

## Planejamento

Toda a documentação de planejamento está em [`docs/`](docs/):

- [`00-visao-geral.md`](docs/00-visao-geral.md) — posicionamento e escopo
- [`01-stack-tecnica.md`](docs/01-stack-tecnica.md) — stack e decisões técnicas
- [`02-publicacao-ios-desafios.md`](docs/02-publicacao-ios-desafios.md)
- [`03-estrutura-projeto.md`](docs/03-estrutura-projeto.md)
- [`04-arvore-curriculo-bjj.md`](docs/04-arvore-curriculo-bjj.md) — currículo (46 nós)
- [`05-mvp-web-plano.md`](docs/05-mvp-web-plano.md)
- [`06-disclaimer-responsabilidade.md`](docs/06-disclaimer-responsabilidade.md)
- [`07-decisoes.md`](docs/07-decisoes.md) — log de decisões

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
