package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.email.EmailSender;
import dev.fos.model.AppUser;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.LoginTokenRepository;
import dev.fos.repo.PasswordCredentialRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.PasswordAuthenticationToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro com senha própria, ponta a ponta (#81, D47).
 *
 * <p>O que estes testes cobrem é o caminho <em>inteiro</em> — cadastrar, confirmar, entrar,
 * esquecer, redefinir — e não cada serviço isolado. Foi a costura equivalente que faltou na #51: o
 * login autenticava e o app respondia 401 porque a resolução do usuário não conhecia aquele tipo de
 * autenticação, e nenhum teste de unidade veria isso.
 *
 * <p>O {@code EmailSender} aqui guarda a mensagem em vez de enviar: é ele que faz o cadastro por
 * senha existir neste contexto, do mesmo jeito que a credencial faz em produção. Sem ele a porta
 * não existe — o que o {@link AuthIntegrationTest} confere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PasswordAccessIntegrationTest.CaixaDeSaida.class)
@Transactional
class PasswordAccessIntegrationTest {

    static final String ENDERECO = "aluno@example.test";
    static final String OUTRO = "outra-pessoa@example.test";
    static final String SENHA = "tatame-quarta-feira";
    static final String SENHA_NOVA = "guarda-fechada-2026";

    @TestConfiguration
    static class CaixaDeSaida {

        record Mensagem(String para, String assunto, String corpo) {}

        static final List<Mensagem> ENVIADOS = new ArrayList<>();
        static Instant agora = Instant.parse("2026-08-16T10:00:00Z");

        @Bean
        EmailSender emailSender() {
            return (to, subject, body) -> ENVIADOS.add(new Mensagem(to, subject, body));
        }

        /** Relógio que anda, para os testes de expiração não dependerem de esperar de verdade. */
        @Bean
        Clock clock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneId.of("UTC");
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return agora;
                }
            };
        }
    }

    /** O provedor não precisa existir: o que importa é o par (registrationId, subject). */
    private static final ClientRegistration GOOGLE =
            ClientRegistration.withRegistrationId("google")
                    .clientId("test")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/api/login/oauth2/code/google")
                    .authorizationUri("https://example.test/authorize")
                    .tokenUri("https://example.test/token")
                    .userInfoUri("https://example.test/userinfo")
                    .userNameAttributeName("sub")
                    .build();

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private LoginTokenRepository tokens;
    @Autowired private PasswordCredentialRepository credentials;
    @Autowired private AccessRateLimiter freio;

    @BeforeEach
    void limpar() {
        CaixaDeSaida.ENVIADOS.clear();
        CaixaDeSaida.agora = Instant.parse("2026-08-16T10:00:00Z");
        // O freio é singleton e todos os testes vêm do mesmo IP; sem zerar, o segundo já bateria
        // no limite. Janela zero remove tudo que é passado.
        freio.evictOlderThan(Duration.ZERO, CaixaDeSaida.agora.plusSeconds(1));
    }

    // ------------------------------------------------------------------ cadastro

    @Test
    @DisplayName("cadastrar cria conta não verificada, NÃO abre sessão e manda um e-mail só")
    void signingUpCreatesAnUnverifiedAccountWithoutASession() throws Exception {
        cadastrar(ENDERECO, SENHA).andExpect(status().isAccepted());

        assertThat(identidade(ENDERECO).isEmailVerified()).isFalse();
        // O nome é opcional e, quando vem, é ele que o app mostra — e não o endereço cru.
        assertThat(identidade(ENDERECO).getDisplayName()).isEqualTo("Aluno de teste");
        // Sem sessão: quem digitou o endereço ainda não provou que ele é seu. É o passo cuja
        // ausência deixaria alguém entrar no app com a conta pendurada no e-mail de outra pessoa.
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        assertThat(mensagensPara(ENDERECO)).hasSize(1);
        assertThat(mensagensPara(ENDERECO).getFirst().assunto()).contains("Confirme");
        // Nasce fora de fila nenhuma: com cadastro aberto, aprovação não filtra ninguém (D47).
        assertThat(conta(ENDERECO).isApproved()).isTrue();
    }

    @Test
    @DisplayName("confirmar abre a sessão, e o CurrentUserProvider reconhece o token de senha")
    void confirmingOpensASessionTheAppRecognises() throws Exception {
        cadastrar(ENDERECO, SENHA);
        MvcResult entrada =
                confirmarComToken(tokenDeVerificacao())
                        .andExpect(status().isNoContent())
                        .andReturn();

        // O que de fato prova a costura da #51: a sessão aberta aqui resolve usuário na chamada
        // seguinte. Sem o instanceof novo no CurrentUserProvider, isto responderia 401.
        MockHttpSession sessao = (MockHttpSession) entrada.getRequest().getSession(false);
        mockMvc.perform(get("/api/me").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value(PasswordAuthenticationToken.PROVIDER))
                .andExpect(jsonPath("$.email").value(ENDERECO))
                .andExpect(jsonPath("$.accessStatus").value("APROVADO"));
        mockMvc.perform(get("/api/streak").session(sessao)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("abrir o link não o gasta — quem confirma é o POST do clique")
    void openingTheLinkDoesNotSpendIt() throws Exception {
        cadastrar(ENDERECO, SENHA);
        String token = tokenDeVerificacao();

        // O caso existe por um ataque que não é ataque: varredor de link de e-mail corporativo
        // abre toda URL que chega. Se o GET confirmasse, ele queimaria o link antes do clique e a
        // pessoa receberia "já foi usado" sem nunca ter usado.
        mockMvc.perform(get("/api/auth/verificar/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true));
        mockMvc.perform(get("/api/auth/verificar/" + token))
                .andExpect(jsonPath("$.valido").value(true));
        assertThat(identidade(ENDERECO).isEmailVerified()).isFalse();

        confirmarComToken(token).andExpect(status().isNoContent());
        assertThat(identidade(ENDERECO).isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("o link de confirmação é de uso único")
    void theConfirmationLinkWorksOnlyOnce() throws Exception {
        cadastrar(ENDERECO, SENHA);
        String token = tokenDeVerificacao();

        confirmarComToken(token).andExpect(status().isNoContent());
        // "Usado", e não "inválido": a tela manda entrar, porque a conta já está confirmada.
        mockMvc.perform(get("/api/auth/verificar/" + token))
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("usado"));
        confirmarComToken(token).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o link de confirmação vale 24h — 23h ainda entra, 25h não")
    void theConfirmationLinkLastsADay() throws Exception {
        cadastrar(ENDERECO, SENHA);
        String token = tokenDeVerificacao();

        CaixaDeSaida.agora = CaixaDeSaida.agora.plus(Duration.ofHours(23));
        confirmarComToken(token).andExpect(status().isNoContent());

        CaixaDeSaida.ENVIADOS.clear();
        cadastrar(OUTRO, SENHA);
        String outroToken = tokenDeVerificacao(OUTRO);
        CaixaDeSaida.agora = CaixaDeSaida.agora.plus(Duration.ofHours(25));
        // "Vencido" leva à tela que oferece reenviar — a saída que um erro genérico esconderia.
        mockMvc.perform(get("/api/auth/verificar/" + outroToken))
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("vencido"));
        confirmarComToken(outroToken).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cadastro com e-mail já existente responde igual e não sobrescreve a senha")
    void signingUpWithAKnownEmailChangesNothing() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();
        Long contaOriginal = conta(ENDERECO).getId();
        long contasAntes = users.count();
        CaixaDeSaida.ENVIADOS.clear();

        // Mesmo status e mesmo corpo do cadastro de e-mail novo: qualquer diferença observável
        // transformaria a rota em consulta de quem tem conta no app.
        MvcResult repetido =
                cadastrar(ENDERECO, "senha-do-invasor-999")
                        .andExpect(status().isAccepted())
                        .andReturn();
        assertThat(repetido.getResponse().getContentAsString()).isEmpty();

        assertThat(users.count()).isEqualTo(contasAntes);
        assertThat(conta(ENDERECO).getId()).isEqualTo(contaOriginal);
        // A senha do invasor não vale; a de quem cadastrou continua valendo.
        entrar(ENDERECO, "senha-do-invasor-999").andExpect(status().isUnauthorized());
        entrar(ENDERECO, SENHA).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ entrada

    @Test
    @DisplayName("entrar com a senha certa abre sessão com id rotacionado")
    void signingInRotatesTheSessionId() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();

        MockHttpSession anterior = new MockHttpSession();
        String idAntes = anterior.getId();
        entrar(ENDERECO, SENHA, anterior).andExpect(status().isNoContent());

        // Sem a rotação, um id plantado antes do login continuaria valendo depois dele.
        assertThat(anterior.getId()).isNotEqualTo(idAntes);
        mockMvc.perform(get("/api/me").session(anterior))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ENDERECO));
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente respondem exatamente igual")
    void aWrongPasswordLooksLikeAnUnknownEmail() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();

        String comConta =
                entrar(ENDERECO, "senha-que-nao-e-a-dela")
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String semConta =
                entrar("ninguem@example.test", SENHA)
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(corpoSemData(comConta)).isEqualTo(corpoSemData(semConta));
    }

    @Test
    @DisplayName("senha certa antes de confirmar o e-mail responde 403, não 401")
    void anUnverifiedAccountCannotSignIn() throws Exception {
        cadastrar(ENDERECO, SENHA);

        entrar(ENDERECO, SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("email_nao_verificado"));
    }

    @Test
    @DisplayName("cinco erros travam aquele e-mail, e só ele")
    void tooManyWrongPasswordsBlockThatEmailAlone() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();
        CaixaDeSaida.ENVIADOS.clear();
        cadastrar(OUTRO, SENHA);
        confirmar(OUTRO);

        for (int tentativa = 0; tentativa < 5; tentativa++) {
            entrar(ENDERECO, "errada-de-proposito").andExpect(status().isUnauthorized());
        }
        // A sexta nem chega a conferir senha: o freio responde antes.
        entrar(ENDERECO, SENHA).andExpect(status().isTooManyRequests());

        // E o bloqueio não vaza para outra conta — do contrário, errar a senha de alguém trancaria
        // o app inteiro.
        entrar(OUTRO, SENHA).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------- vínculo de contas

    @Test
    @DisplayName("quem já entra pelo Google e se cadastra com o mesmo e-mail cai na mesma conta")
    void aGoogleAccountAndAPasswordSignupAreTheSameAccount() throws Exception {
        AppUser google = accounts.registerLogin("google", "sub-1", ENDERECO, true, "Aluno");
        // Progresso de verdade na conta do Google: é ele que não pode sumir.
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(
                                        oauth2Login()
                                                .clientRegistration(GOOGLE)
                                                .attributes(
                                                        atributos -> atributos.put("sub", "sub-1")))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().is2xxSuccessful());

        cadastrar(ENDERECO, SENHA);
        confirmar();

        assertThat(conta(ENDERECO).getId()).isEqualTo(google.getId());
        assertThat(identities.findByUserId(google.getId())).hasSize(2);
        // O progresso continua lá, visto agora pela sessão de senha.
        mockMvc.perform(get("/api/streak").session(sessaoDeSenha()))
                .andExpect(jsonPath("$.currentStreak").value(1));
    }

    @Test
    @DisplayName("e o inverso: quem se cadastrou com senha e depois entra pelo Google não duplica")
    void aPasswordAccountAbsorbsALaterGoogleLogin() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();
        Long conta = conta(ENDERECO).getId();

        AppUser google = accounts.registerLogin("google", "sub-2", ENDERECO, true, "Aluno");

        assertThat(google.getId()).isEqualTo(conta);
        assertThat(identities.findByUserId(conta)).hasSize(2);
    }

    // ------------------------------------------------------------------ recuperação

    @Test
    @DisplayName("redefinir troca a senha, queima os links pendentes e derruba as sessões abertas")
    void resettingThePasswordEndsEverythingElse() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();
        MockHttpSession aberta = new MockHttpSession();
        entrar(ENDERECO, SENHA, aberta).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me").session(aberta)).andExpect(status().isOk());

        CaixaDeSaida.ENVIADOS.clear();
        esquecida(ENDERECO).andExpect(status().isAccepted());
        String token = tokenDeRedefinicao();

        mockMvc.perform(get("/api/auth/senha/redefinir/" + token))
                .andExpect(jsonPath("$.valido").value(true));
        redefinir(token, SENHA_NOVA).andExpect(status().isNoContent());

        // A senha velha não entra mais, a nova entra.
        entrar(ENDERECO, SENHA).andExpect(status().isUnauthorized());
        entrar(ENDERECO, SENHA_NOVA).andExpect(status().isNoContent());
        // O link não serve duas vezes, e nada mais ficou pendente na conta.
        mockMvc.perform(get("/api/auth/senha/redefinir/" + token))
                .andExpect(jsonPath("$.valido").value(false));
        assertThat(tokens.findByUserIdAndUsedAtIsNull(conta(ENDERECO).getId())).isEmpty();
        // E a sessão que estava aberta caiu: trocar a senha sem expulsar ninguém não expulsa
        // ninguém.
        mockMvc.perform(get("/api/me").session(aberta)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o link de redefinição vale 1 hora")
    void theResetLinkLastsAnHour() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();
        CaixaDeSaida.ENVIADOS.clear();
        esquecida(ENDERECO);
        String token = tokenDeRedefinicao();

        CaixaDeSaida.agora = CaixaDeSaida.agora.plus(Duration.ofMinutes(61));

        mockMvc.perform(get("/api/auth/senha/redefinir/" + token))
                .andExpect(jsonPath("$.valido").value(false));
        redefinir(token, SENHA_NOVA).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("esquecida e reenviar respondem igual para endereço com e sem conta")
    void recoveryDoesNotLeakWhoHasAnAccount() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();
        CaixaDeSaida.ENVIADOS.clear();

        String comConta = corpoDe(esquecida(ENDERECO).andExpect(status().isAccepted()));
        String semConta =
                corpoDe(esquecida("ninguem@example.test").andExpect(status().isAccepted()));
        assertThat(comConta).isEqualTo(semConta);

        String pendente = corpoDe(reenviar(ENDERECO).andExpect(status().isAccepted()));
        String inexistente =
                corpoDe(reenviar("ninguem@example.test").andExpect(status().isAccepted()));
        assertThat(pendente).isEqualTo(inexistente);

        // Só a caixa de quem tem conta recebeu alguma coisa — e é lá que a diferença pode existir.
        assertThat(mensagensPara("ninguem@example.test")).isEmpty();
    }

    @Test
    @DisplayName("senha curta demais é recusada com o motivo, e nada é criado")
    void aShortPasswordIsRefused() throws Exception {
        cadastrar(ENDERECO, "curta123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        assertThat(
                        identities.findByProviderAndProviderSubject(
                                PasswordAuthenticationToken.PROVIDER, ENDERECO))
                .isEmpty();
        assertThat(CaixaDeSaida.ENVIADOS).isEmpty();
    }

    // ------------------------------------------------------------------ exclusão

    @Test
    @DisplayName("excluir a conta apaga a credencial e os tokens junto")
    void deletingTheAccountRemovesTheCredential() throws Exception {
        cadastrar(ENDERECO, SENHA);
        confirmar();
        Long identidade = identidade(ENDERECO).getId();
        assertThat(credentials.findByIdentityId(identidade)).isPresent();

        mockMvc.perform(delete("/api/me").session(sessaoDeSenha()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(credentials.findByIdentityId(identidade)).isEmpty();
        assertThat(
                        identities.findByProviderAndProviderSubject(
                                PasswordAuthenticationToken.PROVIDER, ENDERECO))
                .isEmpty();
        assertThat(tokens.findAll()).isEmpty();
    }

    // ------------------------------------------------------------------ auxiliares

    private ResultActions cadastrar(String email, String senha) throws Exception {
        return mockMvc.perform(
                post("/api/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"%s\",\"senha\":\"%s\",\"nome\":\"Aluno de teste\"}"
                                        .formatted(email, senha)));
    }

    private ResultActions entrar(String email, String senha) throws Exception {
        return entrar(email, senha, new MockHttpSession());
    }

    private ResultActions entrar(String email, String senha, MockHttpSession sessao)
            throws Exception {
        return mockMvc.perform(
                post("/api/auth/login")
                        .session(sessao)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)));
    }

    private ResultActions esquecida(String email) throws Exception {
        return mockMvc.perform(
                post("/api/auth/senha/esquecida")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions reenviar(String email) throws Exception {
        return mockMvc.perform(
                post("/api/auth/verificacao/reenviar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions redefinir(String token, String senha) throws Exception {
        return mockMvc.perform(
                post("/api/auth/senha/redefinir/" + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"%s\"}".formatted(senha)));
    }

    private void confirmar() throws Exception {
        confirmar(ENDERECO);
    }

    private void confirmar(String email) throws Exception {
        confirmarComToken(tokenDeVerificacao(email)).andExpect(status().isNoContent());
    }

    /** O clique em "confirmar meu e-mail": é o POST que gasta o link, nunca a abertura da URL. */
    private ResultActions confirmarComToken(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/verificar/" + token).with(csrf()));
    }

    /** Sessão já autenticada por senha, para as chamadas que só precisam estar dentro. */
    private MockHttpSession sessaoDeSenha() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        entrar(ENDERECO, SENHA, sessao).andExpect(status().isNoContent());
        return sessao;
    }

    private String tokenDeVerificacao() {
        return tokenDeVerificacao(ENDERECO);
    }

    /** O token do link que foi para a caixa de quem se cadastrou. */
    private String tokenDeVerificacao(String email) {
        String corpo = mensagensPara(email).getLast().corpo();
        int inicio = corpo.indexOf("/confirmar-email/");
        String caminho = corpo.substring(inicio).split("\\s+")[0];
        return caminho.substring(caminho.lastIndexOf('/') + 1);
    }

    private String tokenDeRedefinicao() {
        String corpo = mensagensPara(ENDERECO).getLast().corpo();
        int inicio = corpo.indexOf("/senha/redefinir/");
        String caminho = corpo.substring(inicio).split("\\s+")[0];
        return caminho.substring(caminho.lastIndexOf('/') + 1);
    }

    private static String corpoDe(ResultActions acoes) throws Exception {
        return acoes.andReturn().getResponse().getContentAsString();
    }

    /** O corpo de erro carrega um timestamp; ele é a única diferença legítima entre dois. */
    private static String corpoSemData(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"\"");
    }

    private static List<CaixaDeSaida.Mensagem> mensagensPara(String email) {
        return CaixaDeSaida.ENVIADOS.stream().filter(m -> m.para().equals(email)).toList();
    }

    private UserIdentity identidade(String email) {
        return identities
                .findByProviderAndProviderSubject(PasswordAuthenticationToken.PROVIDER, email)
                .orElseThrow();
    }

    private AppUser conta(String email) {
        return users.findById(identidade(email).getUserId()).orElseThrow();
    }
}
