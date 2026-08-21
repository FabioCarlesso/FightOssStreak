package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.email.EmailSender;
import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.LoginTokenRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.EmailAccessService;
import dev.fos.service.EmailAuthenticationToken;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrada por e-mail, ponta a ponta (#52).
 *
 * <p>O ponto destes testes é o caminho <em>inteiro</em>: pedir, aprovar, receber o link, clicar e
 * ser reconhecido pelo {@code CurrentUserProvider}. Foi a costura equivalente que faltou na #51 — o
 * login autenticava e o app respondia 401 porque a resolução do usuário não conhecia aquele tipo de
 * autenticação. Testar só o serviço não pegaria isso.
 *
 * <p>O {@code EmailSender} aqui é um bean de teste que guarda a mensagem em vez de enviar: é ele
 * que faz a entrada por e-mail existir neste contexto, do mesmo jeito que a credencial faz em
 * produção.
 */
@SpringBootTest(properties = "fos.auth.owner-emails=dono@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmailAccessIntegrationTest.CaixaDeSaida.class)
@Transactional
class EmailAccessIntegrationTest {

    static final String ENDERECO = "sem-provedor@example.test";

    /** Não pode conter {@link #ENDERECO} como substring: o resumo é conferido por `contains`. */
    static final String OUTRO = "segundo-pedido@example.test";

    /** O mesmo de {@code fos.auth.owner-emails}, logo acima. */
    static final String DONO = "dono@example.test";

    @TestConfiguration
    static class CaixaDeSaida {

        record Mensagem(String para, String assunto, String corpo) {}

        static final List<Mensagem> ENVIADOS = new ArrayList<>();
        static Instant agora = Instant.parse("2026-08-16T10:00:00Z");

        /** Ligado, o provedor de envio "cai" — é assim que o teste chega no caminho de falha. */
        static boolean falhar = false;

        @Bean
        EmailSender emailSender() {
            return (to, subject, body) -> {
                if (falhar) {
                    throw new IllegalStateException("provedor de envio fora do ar");
                }
                ENVIADOS.add(new Mensagem(to, subject, body));
            };
        }

        /** Relógio que anda, para o teste de expiração não depender de esperar de verdade. */
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

    @Autowired private MockMvc mockMvc;
    @Autowired private EmailAccessService emailAccess;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private LoginTokenRepository tokens;
    @Autowired private AccessRateLimiter freio;

    @BeforeEach
    void limpar() {
        CaixaDeSaida.ENVIADOS.clear();
        CaixaDeSaida.falhar = false;
        CaixaDeSaida.agora = Instant.parse("2026-08-16T10:00:00Z");
        // O freio é singleton e todos os testes vêm do mesmo IP; sem zerar, o segundo já bateria
        // no limite. Janela zero remove tudo que é passado.
        freio.evictOlderThan(Duration.ZERO, CaixaDeSaida.agora.plusSeconds(1));
    }

    @Test
    @DisplayName("pedir acesso cria conta pendente e NÃO dispara e-mail nenhum")
    void requestingAccessSendsNothing() throws Exception {
        pedir(ENDERECO).andExpect(status().isAccepted());

        assertThat(contaDe(ENDERECO).getAccessStatus()).isEqualTo(AccessStatus.PENDENTE);
        // Nem para quem pediu — seria canal de spam com o domínio do app no remetente — nem para
        // o dono: desde a #54 ele fica sabendo pelo resumo da janela seguinte.
        assertThat(CaixaDeSaida.ENVIADOS).isEmpty();
    }

    @Test
    @DisplayName("o resumo lista a fila inteira, e só sai quando há pedido novo")
    void theDigestCoversTheWholeQueueAndOnlyWhenSomethingIsNew() throws Exception {
        pedir(ENDERECO);

        emailAccess.notifyOwnersOfQueue();
        assertThat(mensagensPara(DONO)).hasSize(1);
        assertThat(mensagensPara(DONO).getFirst().corpo()).contains(ENDERECO);
        assertThat(mensagensPara(DONO).getFirst().assunto()).contains("1 solicitação");

        // Nada mudou: a janela seguinte fica calada, em vez de repetir o mesmo e-mail treze
        // vezes por dia até alguém decidir.
        emailAccess.notifyOwnersOfQueue();
        assertThat(mensagensPara(DONO)).hasSize(1);

        // Chegou um pedido novo: sai o segundo resumo, e ele traz também o que continua pendente.
        pedir(OUTRO);
        emailAccess.notifyOwnersOfQueue();
        assertThat(mensagensPara(DONO)).hasSize(2);
        CaixaDeSaida.Mensagem segundo = mensagensPara(DONO).get(1);
        assertThat(segundo.corpo()).contains(ENDERECO).contains(OUTRO);
        assertThat(segundo.assunto()).contains("2 solicitações");
    }

    @Test
    @DisplayName("o resumo ignora quem já foi decidido")
    void theDigestSkipsDecidedRequests() throws Exception {
        pedir(ENDERECO);
        accounts.decide(contaDe(ENDERECO).getId(), true, -1L);
        CaixaDeSaida.ENVIADOS.clear();

        pedir(OUTRO);
        emailAccess.notifyOwnersOfQueue();

        assertThat(mensagensPara(DONO)).hasSize(1);
        assertThat(mensagensPara(DONO).getFirst().corpo()).contains(OUTRO).doesNotContain(ENDERECO);
    }

    @Test
    @DisplayName("fila vazia não gera resumo")
    void anEmptyQueueSendsNothing() {
        emailAccess.notifyOwnersOfQueue();

        assertThat(CaixaDeSaida.ENVIADOS).isEmpty();
    }

    @Test
    @DisplayName("provedor fora do ar não dá o pedido por avisado: a janela seguinte reenvia")
    void aFailingSenderLeavesTheQueueUnannounced() throws Exception {
        pedir(ENDERECO);

        CaixaDeSaida.falhar = true;
        emailAccess.notifyOwnersOfQueue();
        assertThat(CaixaDeSaida.ENVIADOS).isEmpty();

        // Marcar antes de entregar perderia o pedido em silêncio — o esquecimento que a #54
        // existe para evitar.
        CaixaDeSaida.falhar = false;
        emailAccess.notifyOwnersOfQueue();
        assertThat(mensagensPara(DONO)).hasSize(1);
        assertThat(mensagensPara(DONO).getFirst().corpo()).contains(ENDERECO);
    }

    @Test
    @DisplayName("aprovar dispara o link, e o link entra de verdade no app")
    void approvingSendsTheLinkAndTheLinkWorks() throws Exception {
        pedir(ENDERECO);
        AppUser user = contaDe(ENDERECO);
        accounts.decide(user.getId(), true, -1L);
        emailAccess.notifyApproved(user.getId(), "https://fos.example");

        assertThat(mensagensPara(ENDERECO)).hasSize(1);

        MvcResult entrada =
                mockMvc.perform(get(caminhoDoLink()))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/hoje"))
                        .andReturn();

        // O que de fato prova a costura: a sessão aberta pelo link é reconhecida pelo
        // CurrentUserProvider na requisição seguinte.
        MockHttpSession sessao = (MockHttpSession) entrada.getRequest().getSession(false);
        mockMvc.perform(get("/api/me").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("email"))
                .andExpect(jsonPath("$.email").value(ENDERECO))
                .andExpect(jsonPath("$.accessStatus").value("APROVADO"));
        mockMvc.perform(get("/api/streak").session(sessao)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("o link é de uso único")
    void theLinkWorksOnlyOnce() throws Exception {
        String link = linkAprovado();

        mockMvc.perform(get(link)).andExpect(redirectedUrl("/hoje"));
        mockMvc.perform(get(link)).andExpect(redirectedUrl("/hoje?erro=link_invalido"));
    }

    @Test
    @DisplayName("o link vence")
    void theLinkExpires() throws Exception {
        String link = linkAprovado();

        CaixaDeSaida.agora = CaixaDeSaida.agora.plus(Duration.ofMinutes(16));

        mockMvc.perform(get(link)).andExpect(redirectedUrl("/hoje?erro=link_invalido"));
    }

    @Test
    @DisplayName("o token não sobrevive em claro no banco")
    void theTokenIsNeverStoredInClear() throws Exception {
        String link = linkAprovado();
        String raw = link.substring(link.lastIndexOf('/') + 1);

        assertThat(tokens.findAll()).hasSize(1);
        assertThat(tokens.findByTokenHash(raw)).isEmpty();
    }

    @Test
    @DisplayName("pedir link responde igual para conta inexistente, pendente, recusada e aprovada")
    void loginRequestDoesNotLeakAccountState() throws Exception {
        entrar("nao-existe@example.test").andExpect(status().isAccepted());

        pedir(ENDERECO);
        entrar(ENDERECO).andExpect(status().isAccepted());

        AppUser recusado = contaDe(ENDERECO);
        accounts.decide(recusado.getId(), false, -1L);
        entrar(ENDERECO).andExpect(status().isAccepted());

        // Mesma resposta nos três — e nenhum link saiu, porque nenhuma conta estava liberada.
        // (O único e-mail da caixa é o aviso de fila que o `pedir` mandou para o dono.)
        assertThat(mensagensPara(ENDERECO)).isEmpty();
        assertThat(mensagensPara("nao-existe@example.test")).isEmpty();
    }

    @Test
    @DisplayName("excluir a conta apaga os tokens pendentes dela")
    void deletingTheAccountRemovesItsTokens() throws Exception {
        linkAprovado();
        AppUser user = contaDe(ENDERECO);
        assertThat(tokens.findAll()).isNotEmpty();

        accounts.delete(user.getId());

        assertThat(tokens.findAll()).isEmpty();
        assertThat(users.findById(user.getId())).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions pedir(String email)
            throws Exception {
        return mockMvc.perform(
                post("/api/auth/email/solicitar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions entrar(String email)
            throws Exception {
        return mockMvc.perform(
                post("/api/auth/email/entrar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"));
    }

    /** Deixa uma conta aprovada com link emitido, e devolve o caminho do link. */
    private String linkAprovado() throws Exception {
        pedir(ENDERECO);
        AppUser user = contaDe(ENDERECO);
        accounts.decide(user.getId(), true, -1L);
        emailAccess.notifyApproved(user.getId(), "https://fos.example");
        return caminhoDoLink();
    }

    /** O link é o que foi para quem pediu — o aviso ao dono também cita uma URL. */
    private String caminhoDoLink() {
        String corpo = mensagensPara(ENDERECO).getFirst().corpo();
        int inicio = corpo.indexOf("https://fos.example");
        String url = corpo.substring(inicio).split("\\s+")[0];
        return url.substring("https://fos.example".length());
    }

    private static List<CaixaDeSaida.Mensagem> mensagensPara(String email) {
        return CaixaDeSaida.ENVIADOS.stream().filter(m -> m.para().equals(email)).toList();
    }

    private AppUser contaDe(String email) {
        return users.findById(
                        identities
                                .findByProviderAndProviderSubject(
                                        EmailAuthenticationToken.PROVIDER, email)
                                .orElseThrow()
                                .getUserId())
                .orElseThrow();
    }
}
