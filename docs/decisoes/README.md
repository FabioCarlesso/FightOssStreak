# Log de Decisões

Registro de decisões com justificativa, para que o "porquê" não se perca — e para que reversões
futuras sejam conscientes. Cada decisão tem o seu arquivo, com a justificativa completa e o
critério de revisão; a tabela abaixo é o índice.

**Decisão nova** ganha o próximo número, um arquivo `DNN-titulo-curto.md` nesta pasta e uma linha
nesta tabela, no mesmo PR.

| # | Decisão |
|---|---|
| [D1](D01-posicionamento-revisao-nao-ensino.md) | Posicionamento: revisão, não ensino |
| [D2](D02-react-web-react-native-mobile.md) | React (web) → React Native (mobile) |
| [D3](D03-backend-spring-boot-postgres.md) | Backend Spring Boot + Postgres |
| [D4](D04-web-first-mobile-depois.md) | Web-first, mobile depois |
| [D5](D05-monorepo.md) | Monorepo |
| [D6](D06-somente-gi-nesta-versao.md) | Somente Gi nesta versão |
| [D7](D07-video-via-embed-do-youtube.md) | Vídeo via embed do YouTube |
| [D8](D08-sem-monetizacao-licenca-mit.md) | Sem monetização; licença MIT |
| [D9](D09-sem-login-no-mvp.md) | Sem login no MVP |
| [D10](D10-sobrevivencia-antes-de-ataque-na-arvore.md) | Sobrevivência antes de ataque na árvore |
| [D11](D11-curriculo-como-dado-versionado-nao-codigo.md) | Currículo como dado versionado, não código |
| [D12](D12-validacao-com-faixa-preta-adiada.md) | Validação com faixa-preta adiada |
| [D13](D13-unlockrule-por-no-all-padrao-ou.md) | `unlockRule` por nó: `ALL` (padrão) ou `ANY` |
| [D14](D14-o-curriculo-tem-46-nos-nao.md) | O currículo tem 46 nós, não 43 |
| [D15](D15-no-sem-quiz-e-concluido-pelo.md) | Nó sem quiz é concluído pelo registro de drill |
| [D16](D16-alternativas-do-quiz-sao-embaralhadas-ao.md) | Alternativas do quiz são embaralhadas ao servir |
| [D17](D17-regras-puras-duplicadas-em-java-e.md) | Regras puras duplicadas em Java e TypeScript |
| [D18](D18-main-protegida-default-fixa-so-muda.md) | `main` protegida: default fixa, só muda por PR, merge só com CI verde |
| [D19](D19-ci-sem-filtro-por-caminho-em.md) | CI sem filtro por caminho em pull request |
| [D20](D20-aderencia-ao-srs-e-gravada-no.md) | Aderência ao SRS é gravada no momento do drill, não inferida depois |
| [D21](D21-primeira-leva-de-videos-m0-e.md) | Primeira leva de vídeos (M0 e M1) catalogada com verificação automática, mas sem ninguém ter assistido aos 11 do começo ao fim |
| [D22](D22-railway-como-plataforma-de-deploy.md) | Railway como plataforma de deploy |
| [D23](D23-dois-servicos-nginx-api-nao-o.md) | Dois serviços (nginx + API), não o `dist/` empacotado dentro do JAR |
| [D24](D24-so-o-web-tem-dominio-publico.md) | Só o `web` tem domínio público; o backend só existe na rede privada |
| [D25](D25-o-nginx-zera-o-origin-quando.md) | O nginx zera o `Origin` quando ele aponta para o próprio app, e só nesse caso |
| [D26](D26-ajuste-de-mobile-concentrado-em-dois.md) | Ajuste de mobile concentrado em dois breakpoints; alvo de toque decidido por `pointer: coarse`, não por largura |
| [D27](D27-quiz-de-m2-e-m3-com.md) | Quiz de M2 e M3 com quatro perguntas por nó, e não três como em M0/M1 |
| [D28](D28-o-ci-passa-a-ser-portao.md) | O CI passa a ser portão de estilo, e a formatação inicial entra em um commit só |
| [D29](D29-teste-de-ui-cobre-tres-fluxos.md) | Teste de UI cobre três fluxos, e só três |
| [D30](D30-a-regra-7-vira-verificacao-o.md) | A regra 7 vira verificação; o CodeQL fica de fora da ruleset de propósito |
| [D31](D31-modo-demonstracao-percorre-o-curriculo-inteiro.md) | Modo demonstração: percorre o currículo inteiro, e não grava nada |
| [D32](D32-video-complementar-por-no-o-canonico.md) | Vídeo complementar por nó: o canônico ensina, o clipe lembra |
| [D33](D33-landing-publica-na-raiz-e-o.md) | Landing pública na raiz, e o app passa a viver em `/hoje` |
| [D34](D34-anotacao-do-no-em-duas-formas.md) | Anotação do nó em duas formas: a do drill é histórico, a fixada é releitura |
| [D35](D35-print-do-no-passa-a-ser.md) | Print do nó passa a ser enquadrado de forma diferente em cada largura |
| [D36](D36-login-social-com-acesso-sob-aprovacao.md) | Login social com acesso sob aprovação — a D9 cai, mas não vira autocadastro |
| [D37](D37-provedor-externo-entra-direto-a-fila.md) | Provedor externo entra direto; a fila passa a valer para quem não tem provedor |
| [D38](D38-o-dono-e-avisado-por-um.md) | O dono é avisado por um resumo horário da fila, das 10h às 22h — não a cada pedido |
| [D39](D39-acesso-demonstrativo-cada-visita-ganha-uma.md) | Acesso demonstrativo: cada visita ganha uma cópia descartável de uma conta-modelo, e não uma conta de demonstração compartilhada |
| [D40](D40-fontes-de-conteudo-registradas-em-docs.md) | Fontes de conteúdo registradas em `docs/conteudo/fontes.md`, curadoria e não campo de produto |
| [D41](D41-padrao-de-escrita-do-concept-tres.md) | Padrão de escrita do `concept` (três movimentos) entra por partes: doc e parágrafo já, faixa de tamanho módulo a módulo |
| [D42](D42-a-faixa-de-tamanho-do-concept.md) | A faixa de tamanho do `concept` liga por conjunto de módulos curados, não por "sim/não" global — e M0/M1 entram reescritos juntos, no mesmo PR |
| [D43](D43-m2m8-35-nos-entraram-no-padrao.md) | M2–M8 (35 nós) entraram no padrão de três movimentos no mesmo PR de M0/M1, contra a recomendação da própria issue #58 de um PR por módulo — e sem fonte externa nova, só reestruturação do texto já existente |
| [D44](D44-o-quiz-do-no-vira-banco.md) | O quiz do nó vira banco rotativo: 8 perguntas por nó, 4 servidas por tentativa, escolhidas por rotação estável — e revisita a D27, não a derruba |
| [D45](D45-video-canonico-entre-candidatos-equivalentes-ganha.md) | Vídeo canônico: entre candidatos equivalentes, ganha o em português — desempate, não passe livre |
| [D46](D46-feedback-de-usuario-fila-propria-no.md) | Feedback de usuário: fila própria no app, nó opcional, demo bloqueada |
| [D47](D47-o-cadastro-abre-qualquer-um-cria.md) | O cadastro abre: qualquer um cria conta com e-mail e senha, confirmada por link — a fila de aprovação da D36/D37 perde a razão de existir |
| [D48](D48-a-fila-de-aprovacao-e-desmontada.md) | A fila de aprovação é desmontada, e `fos.auth.owner-emails` passa a significar "conta de administração" — sem tabela de papéis |
| [D49](D49-o-papel-vira-app-user-role.md) | O papel vira `app_user.role` e o bloqueio reativo ganha quem o produza — dois critérios da D48 puxados de uma vez, sem tabela de permissão e sem a fila de volta |
| [D50](D50-analytics-proprio-sem-cookie-de-rastreio.md) | Analytics próprio, sem cookie de rastreio, sem terceiro e sem guardar IP — e a promessa de `docs/privacidade/README.md` reescrita em vez de contornada |
| [D51](D51-o-endereco-de-quem-chama-passa.md) | O endereço de quem chama passa a vir da infraestrutura, e a topologia vira variável de ambiente |
| [D52](D52-o-painel-de-uso-e-agregado.md) | O painel de uso é agregado e de ninguém — e é essa restrição, não a tela, que é a decisão |
| [D53](D53-a-topologia-observada-e-conferida-contra.md) | A topologia observada é conferida contra a declarada em toda requisição proxiada, e a divergência vira `WARN` |
| [D54](D54-o-monitoramento-tem-duas-metades-e.md) | O monitoramento tem duas metades, e a que avisa que o site caiu vive fora do site |
| [D55](D55-o-streak-perdoa-ate-dois-dias.md) | O streak perdoa até dois dias por mês, e o perdão é livro-caixa e não cache |
| [D56](D56-o-diario-de-treino-vira-pilar.md) | O diário de treino vira pilar do MVP: a sessão é a entrada, o currículo é a saída |
| [D57](D57-peso-e-sensacao-sao-guardados-e.md) | Peso e sensação são guardados e mostrados, nunca interpretados |
| [D58](D58-o-streak-passa-a-contar-dia.md) | O streak passa a contar dia com registro, e não dia com drill — revisita a D55 sem derrubá-la |
| [D59](D59-o-heatmap-e-uma-leitura-do.md) | O heatmap é uma leitura do mesmo conjunto de dias do streak, e não uma segunda contagem |
| [D60](D60-o-secure-do-cookie-de-sessao.md) | O `Secure` do cookie de sessão é variável de ambiente, e a razão é a mesma que prende `forward-headers-strategy: framework` |
| [D61](D61-confirmar-o-e-mail-exige-a.md) | Confirmar o e-mail exige a senha do cadastro, e recadastro pendente troca a senha |
| [D62](D62-link-de-e-mail-sai-de.md) | Link de e-mail sai de `fos.public-url`, nunca da requisição; e o nginx só atende o `Host` do app |
| [D63](D63-e-mail-de-provedor-so-e.md) | E-mail de provedor só é verificado quando o provedor afirma; o Facebook passa a entrar sem vínculo |
| [D64](D64-exclusao-de-conta-apaga-o-feedback.md) | Exclusão de conta apaga o feedback do autor e esquece quem decidiu |
| [D65](D65-cabecalhos-de-seguranca-no-nginx-com.md) | Cabeçalhos de segurança no nginx, com CSP bloqueante sem `'unsafe-inline'` |
| [D66](D66-o-mobile-e-adiado-os-criterios.md) | O mobile é adiado: os critérios do MVP web não foram atingidos — a D4 segue valendo |
| [D67](D67-o-mobile-comeca-aprender-a-plataforma.md) | O mobile começa: aprender a plataforma é objetivo próprio, e a D66 cai — os critérios do MVP não afrouxam |

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
| Currículo montado sem revisão de graduado | Pode ensinar ordem ou conceito errado | Aceito conscientemente enquanto o uso for pessoal (D12) — desde D40, `docs/conteudo/fontes.md` torna a proveniência rastreável, sem fechar o risco |
| Vídeos do YouTube saem do ar / ficam privados | Nós ficam sem referência | Detectado, não evitado — `scripts/verificar-videos.mjs` roda semanalmente pelo workflow `videos` e abre issue com os nós afetados (`docs/conteudo/videos.md`). A substituição segue humana |
| Gamificação sustentar-se sozinha sem gerar aprendizado | Produto vira streak vazio | Coberto pelo critério de falha em `docs/produto/mvp-web.md` |
| App ser visto como wrapper de YouTube na review da Apple | Rejeição | Em aberto desde a D67; mitigação em `docs/produto/publicacao-ios.md` |
