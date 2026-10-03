# Banco de dados

Postgres em produção e no Compose; H2 em memória no perfil `dev` e nos testes, em modo de
compatibilidade PostgreSQL. O schema é do **Flyway**, em
`backend/src/main/resources/db/migration/`, e o mesmo conjunto de migrations roda nos dois bancos —
por isso o SQL é deliberadamente portável (ANSI + `IDENTITY`). Cada migration abre com um comentário
dizendo o porquê; este arquivo é só o mapa.

## Migrations

| Migration | O que entra | Contexto |
|---|---|---|
| `V1__schema` | `app_user`, currículo (`curriculum_module`, `node`, `node_prereq`, `quiz_question`, `quiz_option`), `user_progress`, `srs_review`, `drill_log`, `disclaimer_acceptance` | Esquema inicial |
| `V2__seed_single_user` | o usuário único do MVP | D9 |
| `V3__metrics` | `quiz_attempt` e as colunas de aderência ao SRS do drill | D20 |
| `V4__node_extra_video` | `node_extra_video` | D32, #41 |
| `V5__user_progress_pinned_note` | anotação fixada em `user_progress` | #45 |
| `V6__user_identity` | `user_identity` e o estado de acesso | D36, #24 |
| `V7__login_token` | `login_token` | #52 |
| `V8__queue_notice` | resumo horário da fila (desmontado depois) | D38, #54 |
| `V9__demo_account` | conta de demonstração descartável | D39, #62 |
| `V10__feedback` | `feedback` | D46 |
| `V11__password_credential` | `password_credential` | D47, #81 |
| `V12__sem_fila_de_aprovacao` | fim do portão de aprovação | D48, #83 |
| `V13__gestao_de_usuarios` | `app_user.role` e o registro de quem decidiu cada mudança | D49 |
| `V14__usage_events` | `usage_event` (cru) e `usage_daily` (agregado) | D50, #84 |
| `V15__saude_do_site` | `http_stat_hourly` e `app_start` | D54, #86 |
| `V16__streak_freeze` | `streak_freeze` | D55, #99 |
| `V17__training_session` | `training_session` e `drill_log.session_id` | D56–D58, #114 |
| `V18__facebook_email_nao_verificado` | e-mail de provedor só é verificado quando o provedor afirma | D63 |

## Regras que valem para toda migration

- **Nenhuma coluna de IP, em tabela nenhuma** (D50). O `UsageSemIpTest` reprova o build se uma
  coluna com cara de IP aparecer em qualquer migration. A promessa está em
  [`privacidade/coleta-de-uso.md`](privacidade/coleta-de-uso.md).
- **Senha só em `password_credential`**, como hash do `DelegatingPasswordEncoder` — nunca em
  `user_identity`.
- **Dimensão nova do painel de uso não é migration**: é linha no `UsageAggregator`. Já a escada do
  histograma de latência (`HttpStats`) é — trocá-la muda o que `http_stat_hourly` significa.
- **Migration aplicada não se edita — nem comentário.** O Flyway guarda o checksum do arquivo
  inteiro, e a subida em produção recusaria o schema. Por isso as migrations antigas ainda citam
  `docs/` pelos nomes de antes da #135, e o `verificar-links-docs.mjs` as deixa de fora.
- O currículo não entra por migration: é JSON ingerido na subida (D11).
- Tabela nova que guarda dado de alguém precisa entrar na exclusão de conta (`DELETE /api/me`) e no
  [quadro de privacidade](privacidade/README.md#o-que-é-coletado).

## Perfis

| Perfil | Banco | Quando |
|---|---|---|
| `dev` (padrão) | H2 em memória — some ao reiniciar | `npm run dev:backend` |
| `postgres` | Postgres de `FOS_DB_URL` | Compose, Railway, `./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres` |
| `test` | H2 em memória | `./mvnw test` |

`SPRING_PROFILES_ACTIVE` esquecida em produção cai no `dev` sem erro nenhum — ver
[`deploy.md`](deploy.md#detalhes-que-não-são-óbvios).
