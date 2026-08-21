package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.email.EmailSender;
import dev.fos.model.AccessStatus;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.EmailAuthenticationToken;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * O aviso de fila (#54) com {@code fos.auth.owner-emails} vazia — que é o <em>default</em>.
 *
 * <p>Contexto próprio porque a lista de donos é lida na construção do serviço: não dá para
 * esvaziá-la no meio do {@code EmailAccessIntegrationTest}. Vale o custo do segundo contexto porque
 * este é o estado em que a aplicação sobe sem configuração nenhuma — dev e CI incluídos —, e um
 * aviso que explodisse aqui quebraria o pedido de acesso justamente onde ninguém configurou nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PedidoSemDonoIntegrationTest.CaixaDeSaida.class)
@Transactional
class PedidoSemDonoIntegrationTest {

    private static final String ENDERECO = "sem-dono@example.test";

    @TestConfiguration
    static class CaixaDeSaida {

        static final List<String> ENVIADOS = new ArrayList<>();

        @Bean
        EmailSender emailSender() {
            return (to, subject, body) -> ENVIADOS.add(to);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;

    @Test
    @DisplayName("sem dono configurado o pedido entra na fila e nenhum e-mail é tentado")
    void withoutOwnersNothingIsSent() throws Exception {
        CaixaDeSaida.ENVIADOS.clear();

        mockMvc.perform(
                        post("/api/auth/email/solicitar")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + ENDERECO + "\"}"))
                .andExpect(status().isAccepted());

        assertThat(CaixaDeSaida.ENVIADOS).isEmpty();
        assertThat(
                        users.findById(
                                        identities
                                                .findByProviderAndProviderSubject(
                                                        EmailAuthenticationToken.PROVIDER, ENDERECO)
                                                .orElseThrow()
                                                .getUserId())
                                .orElseThrow()
                                .getAccessStatus())
                .isEqualTo(AccessStatus.PENDENTE);
    }
}
