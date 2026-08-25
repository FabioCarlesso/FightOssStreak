package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.email.EmailSender;
import dev.fos.model.AppUser;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UserIdentity;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.PasswordAuthenticationToken;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * De qual conta a demonstração é cópia (#62).
 *
 * <p>Existe por um cenário só, e ele não é hipotético: <b>duas contas com o mesmo e-mail</b>.
 * Qualquer um pode se cadastrar digitando o endereço da conta-modelo, o que grava uma identidade
 * <em>não verificada</em> numa conta nova — e as duas ficam disputando o papel de molde. O que
 * decide é o filtro de e-mail verificado; sem ele, decide a ordem que o banco devolver.
 *
 * <p>Por isso a conta impostora é criada <b>antes</b> da verdadeira aqui: com id menor, ela é a
 * primeira que uma consulta sem o filtro devolveria, e o teste falha de verdade se a guarda cair.
 * Conta-modelo em uma classe própria porque o {@code DemoAccessIntegrationTest} adota a linha
 * semeada (id 1), que sempre teria o menor id e tornaria a asserção vazia.
 */
@SpringBootTest(properties = "fos.demo.template-email=modelo-alt@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DemoContaModeloIntegrationTest.EnvioDeTeste.class)
@Transactional
class DemoContaModeloIntegrationTest {

    /**
     * O cadastro por senha só existe com provedor de envio (D47), e é por ele que este teste cria a
     * conta homônima não verificada. Um sender que descarta basta: o que interessa aqui é a
     * identidade que fica no banco, não o e-mail que sairia.
     */
    @TestConfiguration
    static class EnvioDeTeste {
        @Bean
        EmailSender emailSender() {
            return (to, subject, body) -> {};
        }
    }

    private static final String EMAIL = "modelo-alt@example.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private AccessRateLimiter freio;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private UserProgressRepository progress;
    @Autowired private NodeRepository nodes;

    @BeforeEach
    void limparFreio() {
        freio.evictOlderThan(Duration.ZERO, Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("com duas contas no mesmo e-mail, o molde é a de e-mail verificado")
    void theTemplateIsTheAccountWithTheVerifiedEmail() throws Exception {
        Long impostora = contaComEmailNaoVerificado();
        Long modelo = contaComEmailVerificadoEProgresso();
        assertThat(impostora)
                .as("a impostora precisa vir primeiro para o teste dizer alguma coisa")
                .isLessThan(modelo);

        MockHttpSession sessao = abrirDemo();

        // Se a guarda cair, a cópia vem da impostora — que não tem nada — e isto fica vazio.
        Long visitante = contaDaSessao(sessao);
        assertThat(progress.findByIdUserId(visitante))
                .as("a demonstração precisa ser cópia da conta verificada, não da homônima")
                .hasSize(1);
        assertThat(progress.findByIdUserId(visitante).getFirst().getPinnedNote())
                .isEqualTo("Anotação da conta-modelo de verdade.");
    }

    @Test
    @DisplayName("conta com e-mail não confirmado não serve de molde")
    void anUnverifiedAccountIsNeverTheTemplate() throws Exception {
        contaComEmailNaoVerificado();

        // Só ela existe: não há conta com este e-mail VERIFICADO, e o recurso responde como
        // responderia num ambiente sem configuração. É o que impede que se cadastrar com o
        // endereço da conta-modelo aponte a demonstração para a conta errada.
        mockMvc.perform(post("/api/demo/sessao").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("demo_indisponivel"));
    }

    /**
     * Conta com e-mail <b>não verificado</b>, criada pelo cadastro com senha (D47).
     *
     * <p>É o caso que interessa aqui: a conta existe, o endereço bate com {@code
     * fos.demo.template-email}, e ainda assim ela não pode servir de conta-modelo — porque ninguém
     * provou ser dono do endereço. Antes da D48 este mesmo estado vinha do pedido de acesso por
     * e-mail; o caminho mudou, a garantia é a mesma.
     */
    private Long contaComEmailNaoVerificado() throws Exception {
        mockMvc.perform(
                        post("/api/auth/cadastro")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + EMAIL
                                                + "\",\"senha\":\"conta-modelo-de-teste\"}"))
                .andExpect(status().isAccepted());
        UserIdentity identity =
                identities
                        .findByProviderAndProviderSubject(
                                PasswordAuthenticationToken.PROVIDER, EMAIL)
                        .orElseThrow();
        assertThat(identity.isEmailVerified()).isFalse();
        assertThat(users.findById(identity.getUserId()).orElseThrow().isApproved()).isTrue();
        return identity.getUserId();
    }

    /** Entrada por provedor: e-mail verificado por um terceiro, e algum estado para copiar. */
    private Long contaComEmailVerificadoEProgresso() {
        AppUser conta = accounts.registerLogin("google", "sub-modelo-alt", EMAIL, true, "Autor");
        UserProgress linha =
                new UserProgress(
                        new UserNodeKey(
                                conta.getId(), nodes.findByCode("M0.1").orElseThrow().getId()),
                        ProgressStatus.COMPLETED,
                        Instant.parse("2026-06-01T09:00:00Z"));
        linha.setPinnedNote("Anotação da conta-modelo de verdade.");
        progress.save(linha);
        return conta.getId();
    }

    private MockHttpSession abrirDemo() throws Exception {
        return (MockHttpSession)
                mockMvc.perform(post("/api/demo/sessao").with(csrf()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getRequest()
                        .getSession(false);
    }

    private Long contaDaSessao(MockHttpSession sessao) {
        var contexto =
                (org.springframework.security.core.context.SecurityContext)
                        sessao.getAttribute("SPRING_SECURITY_CONTEXT");
        return identities
                .findByProviderAndProviderSubject("demo", contexto.getAuthentication().getName())
                .orElseThrow()
                .getUserId();
    }
}
