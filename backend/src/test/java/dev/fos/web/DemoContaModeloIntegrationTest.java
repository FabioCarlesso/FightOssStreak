package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.AppUser;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.EmailAuthenticationToken;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * De qual conta a demonstração é cópia (#62).
 *
 * <p>Existe por um cenário só, e ele não é hipotético: <b>duas contas aprovadas com o mesmo
 * e-mail</b>. Qualquer um pode pedir acesso digitando o endereço da conta-modelo, o que grava uma
 * identidade <em>não verificada</em>; se o dono aprovar esse pedido — e ele é plausível, porque o
 * dono vê o próprio endereço na fila —, existem duas contas aprovadas disputando o papel de molde.
 * O que decide é o filtro de e-mail verificado; sem ele, decide a ordem que o banco devolver.
 *
 * <p>Por isso a conta impostora é criada <b>antes</b> da verdadeira aqui: com id menor, ela é a
 * primeira que uma consulta sem o filtro devolveria, e o teste falha de verdade se a guarda cair.
 * Conta-modelo em uma classe própria porque o {@code DemoAccessIntegrationTest} adota a linha
 * semeada (id 1), que sempre teria o menor id e tornaria a asserção vazia.
 */
@SpringBootTest(properties = "fos.demo.template-email=modelo-alt@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoContaModeloIntegrationTest {

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
    @DisplayName("com duas contas aprovadas no mesmo e-mail, o molde é a de e-mail verificado")
    void theTemplateIsTheAccountWithTheVerifiedEmail() throws Exception {
        Long impostora = contaAprovadaComEmailNaoVerificado();
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
    @DisplayName("conta pendente com o e-mail da conta-modelo não serve de molde")
    void aPendingAccountIsNeverTheTemplate() throws Exception {
        pedirAcessoPorEmail();

        // Só a pendente existe: não há conta aprovada nenhuma com este e-mail, e o recurso
        // responde como responderia num ambiente sem configuração.
        mockMvc.perform(post("/api/demo/sessao").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("demo_indisponivel"));
    }

    /** Pede acesso pelo endpoint público e faz o dono aprovar: aprovada, e-mail não verificado. */
    private Long contaAprovadaComEmailNaoVerificado() throws Exception {
        Long id = pedirAcessoPorEmail();
        accounts.decide(id, true, -1L);
        assertThat(users.findById(id).orElseThrow().isApproved()).isTrue();
        assertThat(
                        identities
                                .findByProviderAndProviderSubject(
                                        EmailAuthenticationToken.PROVIDER, EMAIL)
                                .orElseThrow()
                                .isEmailVerified())
                .isFalse();
        return id;
    }

    private Long pedirAcessoPorEmail() throws Exception {
        mockMvc.perform(
                        post("/api/auth/email/solicitar")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());
        return identities
                .findByProviderAndProviderSubject(EmailAuthenticationToken.PROVIDER, EMAIL)
                .orElseThrow()
                .getUserId();
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
