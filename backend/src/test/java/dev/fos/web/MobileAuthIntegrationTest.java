package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.model.Role;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.AppleCredentialRepository;
import dev.fos.repo.MobileTokenRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.AppleTokenApi;
import dev.fos.service.MobileTokens;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * O app mobile autenticando por token, ponta a ponta (#139, D68).
 *
 * <p>Pelo mesmo motivo do {@link PasswordAccessIntegrationTest}: o defeito que estes testes
 * procuram é de costura — um login que "funciona" e um app que responde 401, como na #51 —, e só o
 * caminho inteiro o mostra. Os tokens do Google e da Apple são assinados aqui, com uma chave local
 * que substitui a busca das chaves públicas dos provedores, e a Apple é uma implementação em
 * memória: nada aqui toca a rede.
 */
@SpringBootTest(
        properties = {
            "fos.mobile.google-client-ids=app-android,app-ios",
            "fos.mobile.min-version=1.2.0",
            "fos.mobile.apple.bundle-id=dev.fos.app",
            "fos.mobile.apple.team-id=TEAM123",
            "fos.mobile.apple.key-id=KEY123",
            "fos.mobile.apple.private-key=nao-usada-com-a-apple-em-memoria"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
    PasswordAccessIntegrationTest.CaixaDeSaida.class,
    MobileAuthIntegrationTest.Provedores.class
})
@Transactional
class MobileAuthIntegrationTest {

    static final String ENDERECO = "aluno@example.test";
    static final String SENHA = "tatame-quarta-feira";
    static final String NONCE = "nonce-sorteado-pelo-app";

    private static final KeyPair CHAVE = gerarChave();

    @TestConfiguration
    static class Provedores {

        static final List<String> TROCAS = new ArrayList<>();
        static Instant agora = Instant.parse("2026-10-03T10:00:00Z");

        /**
         * Relógio que anda, com nome próprio e {@code @Primary}: com o mesmo nome do {@code
         * ClockConfig}, qual dos dois vence depende da ordem de registro, e aqui venceu o do
         * sistema.
         */
        @Bean
        @Primary
        Clock relogioDoTeste() {
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

        static final List<String> REVOGADOS = new ArrayList<>();

        /** Substitui a busca das chaves públicas do Google: assina e confere com a chave local. */
        @Bean
        JwtDecoder googleIdTokenDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) CHAVE.getPublic()).build();
        }

        @Bean
        JwtDecoder appleIdTokenDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) CHAVE.getPublic()).build();
        }

        @Bean
        @Primary
        AppleTokenApi appleEmMemoria() {
            return new AppleTokenApi() {
                @Override
                public Optional<String> exchange(String authorizationCode) {
                    TROCAS.add(authorizationCode);
                    return Optional.of("refresh-de-" + authorizationCode);
                }

                @Override
                public void revoke(String refreshToken) {
                    REVOGADOS.add(refreshToken);
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private MobileTokens tokens;
    @Autowired private MobileTokenRepository tokenRows;
    @Autowired private AppleCredentialRepository appleCredentials;
    @Autowired private AccessRateLimiter freio;

    @BeforeEach
    void limpar() {
        PasswordAccessIntegrationTest.CaixaDeSaida.ENVIADOS.clear();
        Provedores.agora = Instant.parse("2026-10-03T10:00:00Z");
        Provedores.TROCAS.clear();
        Provedores.REVOGADOS.clear();
        // O freio é singleton e os testes andam o relógio; varrer a partir de bem depois de
        // qualquer data usada aqui zera o que um teste anterior deixou.
        freio.evictOlderThan(Duration.ZERO, Provedores.agora.plus(Duration.ofDays(3650)));
    }

    // ------------------------------------------------------------------ o token

    @Test
    @DisplayName("o token resolve o usuário certo: o CurrentUserProvider reconhece a autenticação")
    void aValidTokenResolvesTheRightUser() throws Exception {
        String token = tokenGoogle("ana", "ana@example.test");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.test"))
                .andExpect(jsonPath("$.provider").value("google"));
        mockMvc.perform(get("/api/streak").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("token inválido, sem prefixo Bearer ou vazio responde 401 token_invalido")
    void anInvalidTokenIsRejected() throws Exception {
        for (String valor : List.of("Bearer nao-existe", "Basic abc", "Bearer ")) {
            mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, valor))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("token_invalido"));
        }
    }

    @Test
    @DisplayName(
            "com Authorization, só o token decide: cookie de sessão válido não salva token ruim")
    void theHeaderAloneDecides() throws Exception {
        cadastrarEConfirmar();
        MockHttpSession sessao = new MockHttpSession();
        mockMvc.perform(
                        post("/api/auth/login")
                                .session(sessao)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(credenciais()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me").session(sessao)).andExpect(status().isOk());

        // Sem a regra, este POST sem CSRF pularia a verificação pelo cabeçalho e cairia na sessão.
        mockMvc.perform(
                        post("/api/feedback")
                                .session(sessao)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer forjado")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"category\":\"OUTRO\",\"message\":\"oi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token_invalido"));
    }

    @Test
    @DisplayName("Bearer dispensa CSRF; cookie de sessão continua exigindo")
    void bearerSkipsCsrfAndCookieStillNeedsIt() throws Exception {
        String token = tokenGoogle("ana", "ana@example.test");
        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        cadastrarEConfirmar();
        MockHttpSession sessao = new MockHttpSession();
        mockMvc.perform(
                        post("/api/auth/login")
                                .session(sessao)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(credenciais()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/me").session(sessao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("csrf_invalido"));
    }

    @Test
    @DisplayName("requisição com token não abre sessão: o app nunca recebe JSESSIONID")
    void aTokenRequestCreatesNoSession() throws Exception {
        String token = tokenGoogle("ana", "ana@example.test");
        ResultActions resposta =
                mockMvc.perform(
                        get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        resposta.andExpect(status().isOk());
        assertThat(resposta.andReturn().getRequest().getSession(false)).isNull();
        assertThat(resposta.andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID"));
    }

    @Test
    @DisplayName("o token vence depois de 90 dias sem uso, e usar renova o prazo")
    void theTokenExpiresByDisuse() throws Exception {
        String token = tokenGoogle("ana", "ana@example.test");
        avancar(Duration.ofDays(80));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 80 dias depois do último uso, e não do login: usar renovou o prazo.
        avancar(Duration.ofDays(80));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        avancar(Duration.ofDays(90));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(tokenRows.count()).isZero();
    }

    @Test
    @DisplayName("sair revoga só aquele token; os outros aparelhos continuam dentro")
    void signingOutRevokesOnlyThatToken() throws Exception {
        String celular = tokenGoogle("ana", "ana@example.test");
        String tablet = tokenGoogle("ana", "ana@example.test");

        mockMvc.perform(
                        post("/api/mobile/auth/sair")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + celular))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + celular))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tablet))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
            "sair com token vencido ou já revogado responde 401, e o api-client trata como saída")
    void signingOutWithADeadTokenIs401() throws Exception {
        String revogado = tokenGoogle("ana", "ana@example.test");
        String vencido = tokenGoogle("ana", "ana@example.test");
        mockMvc.perform(
                        post("/api/mobile/auth/sair")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + revogado))
                .andExpect(status().isNoContent());
        avancar(Duration.ofDays(90));

        // O filtro recusa antes do controller: quem decide é o token (D68), também no "sair". O
        // aparelho já está fora nos dois casos, e o `mobileLogout` do api-client resolve com 401.
        for (String token : List.of(revogado, vencido)) {
            mockMvc.perform(
                            post("/api/mobile/auth/sair")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("token_invalido"));
        }
        assertThat(tokenRows.count()).isZero();
    }

    // ------------------------------------------------------------------ senha

    @Test
    @DisplayName("senha certa troca por token; as respostas de erro são as do login da web")
    void passwordLoginIssuesAToken() throws Exception {
        cadastrarEConfirmar();

        String token =
                entrarComSenha(SENHA)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String valor = json.readTree(token).get("token").asText();
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + valor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ENDERECO));

        entrarComSenha("nao-e-a-senha-certa")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("credencial_invalida"));
    }

    @Test
    @DisplayName("redefinir a senha revoga todo token do app da conta, inclusive o do Google")
    void resettingThePasswordRevokesEveryToken() throws Exception {
        cadastrarEConfirmar();
        String pelaSenha =
                json.readTree(entrarComSenha(SENHA).andReturn().getResponse().getContentAsString())
                        .get("token")
                        .asText();
        // O mesmo e-mail verificado vincula o Google à conta da senha (D47).
        String peloGoogle = tokenGoogle("ana-google", ENDERECO);

        mockMvc.perform(
                        post("/api/auth/senha/esquecida")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"%s\"}".formatted(ENDERECO)))
                .andExpect(status().isAccepted());
        mockMvc.perform(
                        post("/api/auth/senha/redefinir/" + linkDoUltimoEmail("/senha/redefinir/"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"senha\":\"guarda-fechada-2026\"}"))
                .andExpect(status().isNoContent());

        for (String token : List.of(pelaSenha, peloGoogle)) {
            mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ portões

    @Test
    @DisplayName(
            "conta bloqueada: 403 acesso_recusado com o token mantido, e ainda pode se excluir")
    void aBlockedAccountSeesTheReasonAndCanDeleteItself() throws Exception {
        String token = tokenGoogle("ana", "ana@example.test");
        AppUser ana = contaDe("google", "ana");
        ana.decideAccess(AccessStatus.RECUSADO, null, "combinado", Instant.now());
        users.saveAndFlush(ana);

        mockMvc.perform(get("/api/streak").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));
        // Bloquear não revoga: revogar mandaria a pessoa ao login em vez de mostrar o motivo.
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessStatus").value("RECUSADO"));
        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(users.findById(ana.getId())).isEmpty();
        assertThat(tokenRows.count()).isZero();
    }

    @Test
    @DisplayName("o token do app não administra, nem de conta ADMIN; a sessão da mesma conta sim")
    void theAppTokenNeverReachesAdminRoutes() throws Exception {
        String token = tokenGoogle("dona", "dona@example.test");
        AppUser dona = contaDe("google", "dona");
        dona.changeRole(Role.ADMIN, null, Instant.now());
        users.saveAndFlush(dona);

        mockMvc.perform(
                        get("/api/admin/usuarios")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));
        // O papel não mudou: /api/me continua dizendo ADMIN.
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ------------------------------------------------------------------ Google

    @Test
    @DisplayName(
            "Google: mesmo sub cai na mesma identidade da web; e-mail verificado vincula conta")
    void googleLinksByVerifiedEmail() throws Exception {
        cadastrarEConfirmar();
        Long contaDaSenha = contaDe("password", ENDERECO).getId();

        String token =
                tokenDe(
                        entrarComGoogle(
                                idTokenGoogle(
                                        "app-ios",
                                        Map.of(
                                                "sub",
                                                "g-123",
                                                "email",
                                                ENDERECO,
                                                "email_verified",
                                                true))));

        assertThat(contaDe("google", "g-123").getId()).isEqualTo(contaDaSenha);
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.provider").value("google"));
    }

    @Test
    @DisplayName("Google sem email_verified não vincula conta, mesmo com o mesmo endereço (D63)")
    void googleWithoutVerifiedEmailDoesNotLink() throws Exception {
        cadastrarEConfirmar();
        Long contaDaSenha = contaDe("password", ENDERECO).getId();

        entrarComGoogle(idTokenGoogle("app-android", Map.of("sub", "g-456", "email", ENDERECO)))
                .andExpect(status().isOk());

        assertThat(contaDe("google", "g-456").getId()).isNotEqualTo(contaDaSenha);
    }

    @Test
    @DisplayName("Google com audiência de outro app, ou de outro emissor, responde 401")
    void googleChecksAudienceAndIssuer() throws Exception {
        entrarComGoogle(idTokenGoogle("client-id-da-web", Map.of("sub", "g-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token_invalido"));
        entrarComGoogle(
                        assinar(
                                new JWTClaimsSet.Builder()
                                        .issuer("https://outro-emissor.example")
                                        .audience("app-ios")
                                        .subject("g-1")
                                        .expirationTime(daquiAUmaHora())
                                        .build()))
                .andExpect(status().isUnauthorized());
        entrarComGoogle("nem-e-um-jwt").andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ Apple

    @Test
    @DisplayName("Apple: confere o nonce, guarda o refresh token e usa o nome do primeiro login")
    void appleChecksTheNonceAndRemembersTheRefreshToken() throws Exception {
        String token =
                tokenDe(
                        entrarComApple(
                                idTokenApple(
                                        Map.of(
                                                "sub", "a-1",
                                                "email", "ana@example.test",
                                                "email_verified", "true",
                                                "nonce", sha256(NONCE))),
                                NONCE,
                                "codigo-1",
                                "Ana"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andExpect(jsonPath("$.provider").value("apple"));
        assertThat(Provedores.TROCAS).containsExactly("codigo-1");
        assertThat(appleCredentials.count()).isEqualTo(1);

        entrarComApple(
                        idTokenApple(Map.of("sub", "a-1", "nonce", sha256("outro-nonce"))),
                        NONCE,
                        null,
                        null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token_invalido"));
    }

    @Test
    @DisplayName("Apple: e-mail relay não vincula a conta que já existe e não vira ADMIN")
    void appleRelayEmailNeverLinks() throws Exception {
        String relay = "x7k2@privaterelay.appleid.com";
        // A conta que já é dona do relay — o caso que o vínculo por e-mail anexaria.
        AppUser jaExiste = accounts.registerLogin("google", "g-relay", relay, true, "Outra");

        entrarComApple(
                        idTokenApple(
                                Map.of(
                                        "sub", "a-relay",
                                        "email", relay,
                                        "email_verified", "true",
                                        "is_private_email", "true",
                                        "nonce", sha256(NONCE))),
                        NONCE,
                        null,
                        null)
                .andExpect(status().isOk());

        AppUser daApple = contaDe("apple", "a-relay");
        assertThat(daApple.getId()).isNotEqualTo(jaExiste.getId());
        assertThat(daApple.getRole()).isEqualTo(Role.USUARIO);
        assertThat(identities.findByProviderAndProviderSubject("apple", "a-relay"))
                .get()
                .extracting(UserIdentity::isEmailVerified)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("excluir a conta revoga o acesso na Apple e apaga o refresh token")
    void deletingTheAccountRevokesAtApple() throws Exception {
        String token =
                tokenDe(
                        entrarComApple(
                                idTokenApple(Map.of("sub", "a-2", "nonce", sha256(NONCE))),
                                NONCE,
                                "codigo-2",
                                null));

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(Provedores.REVOGADOS).containsExactly("refresh-de-codigo-2");
        assertThat(appleCredentials.count()).isZero();
    }

    // ------------------------------------------------------------------ públicas

    @Test
    @DisplayName("versão mínima e provedores do app respondem sem login")
    void publicRoutesAnswerWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/app/versao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumVersion").value("1.2.0"));
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(jsonPath("$.mobileProviders[0]").value("google"))
                .andExpect(jsonPath("$.mobileProviders[1]").value("apple"));
    }

    // ------------------------------------------------------------------ apoio

    private String tokenGoogle(String subject, String email) {
        accounts.registerLogin("google", subject, email, true, null);
        UserIdentity identity =
                identities.findByProviderAndProviderSubject("google", subject).orElseThrow();
        return tokens.issue(identity);
    }

    private AppUser contaDe(String provider, String subject) {
        Long userId =
                identities
                        .findByProviderAndProviderSubject(provider, subject)
                        .orElseThrow()
                        .getUserId();
        return users.findById(userId).orElseThrow();
    }

    private void cadastrarEConfirmar() throws Exception {
        mockMvc.perform(
                        post("/api/auth/cadastro")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(credenciais()))
                .andExpect(status().isAccepted());
        mockMvc.perform(
                        post("/api/auth/verificar/" + linkDoUltimoEmail("/confirmar-email/"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"senha\":\"%s\"}".formatted(SENHA)))
                .andExpect(status().isNoContent());
    }

    private static String credenciais() {
        return "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(ENDERECO, SENHA);
    }

    private static String linkDoUltimoEmail(String caminho) {
        String corpo = PasswordAccessIntegrationTest.CaixaDeSaida.ENVIADOS.getLast().corpo();
        String link = corpo.substring(corpo.indexOf(caminho)).split("\\s+")[0];
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private ResultActions entrarComSenha(String senha) throws Exception {
        return mockMvc.perform(
                post("/api/mobile/auth/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(ENDERECO, senha)));
    }

    private ResultActions entrarComGoogle(String idToken) throws Exception {
        return mockMvc.perform(
                post("/api/mobile/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idToken", idToken))));
    }

    private ResultActions entrarComApple(
            String identityToken, String nonce, String codigo, String nome) throws Exception {
        java.util.HashMap<String, String> corpo = new java.util.HashMap<>();
        corpo.put("identityToken", identityToken);
        corpo.put("nonce", nonce);
        if (codigo != null) {
            corpo.put("authorizationCode", codigo);
        }
        if (nome != null) {
            corpo.put("nome", nome);
        }
        return mockMvc.perform(
                post("/api/mobile/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(corpo)));
    }

    private String tokenDe(ResultActions resposta) throws Exception {
        String corpo =
                resposta.andExpect(status().isOk())
                        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(corpo).get("token").asText();
    }

    private static String idTokenGoogle(String audiencia, Map<String, Object> claims)
            throws JOSEException {
        JWTClaimsSet.Builder builder =
                new JWTClaimsSet.Builder()
                        .issuer("https://accounts.google.com")
                        .audience(audiencia)
                        .expirationTime(daquiAUmaHora());
        claims.forEach(builder::claim);
        return assinar(builder.build());
    }

    private static String idTokenApple(Map<String, Object> claims) throws JOSEException {
        JWTClaimsSet.Builder builder =
                new JWTClaimsSet.Builder()
                        .issuer("https://appleid.apple.com")
                        .audience("dev.fos.app")
                        .expirationTime(daquiAUmaHora());
        claims.forEach(builder::claim);
        return assinar(builder.build());
    }

    /**
     * O prazo do token é conferido contra o relógio de verdade, e não o do teste: quem confere é o
     * validador do Spring, que não usa o {@code Clock} da aplicação.
     */
    private static Date daquiAUmaHora() {
        return Date.from(Instant.now().plus(Duration.ofHours(1)));
    }

    private static String assinar(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(CHAVE.getPrivate()));
        return jwt.serialize();
    }

    private static String sha256(String valor) throws NoSuchAlgorithmException {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static void avancar(Duration quanto) {
        Provedores.agora = Provedores.agora.plus(quanto);
    }

    private static KeyPair gerarChave() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            return gerador.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
