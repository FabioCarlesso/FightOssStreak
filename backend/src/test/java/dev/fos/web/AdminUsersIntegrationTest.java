package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.email.EmailSender;
import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.model.Role;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.AdminActionException;
import dev.fos.service.AdminUserService;
import dev.fos.service.DemoAuthenticationToken;
import dev.fos.service.PasswordAuthenticationToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de usuários pela administração do app, ponta a ponta (#89, #90; D49).
 *
 * <p>O que estes testes provam é o que separa esta tela de um CRUD: <b>promover vale na hora</b> —
 * o papel virou dado, e administrador novo não espera deploy —, <b>bloquear derruba as sessões
 * abertas</b> em vez de valer só no próximo login, e as três guardas que impedem alguém de
 * inutilizar a própria administração (e-mail não verificado, ação sobre si, último administrador).
 *
 * <p>Tudo passa pelo MockMvc com sessão de verdade, e não pelo serviço direto: o portão que barra
 * conta bloqueada é o {@code AccessGateInterceptor} e o que exige administração é o {@code
 * OwnerOnlyInterceptor} — nenhum dos dois existe para quem chama o serviço. Testar por baixo deles
 * seria provar exatamente a parte que não corre risco.
 *
 * <p>O {@code EmailSender} aqui guarda a mensagem em vez de enviar, como no {@link
 * PasswordAccessIntegrationTest}: é o que faz o cadastro por senha existir neste contexto, e é dele
 * que sai a <b>sessão registrada</b> sem a qual não dá para provar que o bloqueio derruba quem já
 * está dentro — sessão simulada por post-processor não passa pelo {@code SessionLogin} e não é
 * anotada em lugar nenhum.
 */
@SpringBootTest(properties = "fos.auth.owner-emails=dono@example.test,semente@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminUsersIntegrationTest.Ambiente.class)
@Transactional
class AdminUsersIntegrationTest {

    /** "Agora" no começo de cada caso. Fixo porque a ordem da listagem é por data de criação. */
    private static final Instant INICIO = Instant.parse("2026-08-16T10:00:00Z");

    private static final String DONO = "dono@example.test";

    private static final String SENHA = "tatame-quarta-feira";

    @TestConfiguration
    static class Ambiente {

        /** O e-mail que teria saído. Só o corpo interessa: é dele que sai o link de confirmação. */
        record Mensagem(String para, String corpo) {}

        static final List<Mensagem> ENVIADOS = new ArrayList<>();

        static Instant agora = INICIO;

        @Bean
        EmailSender emailSender() {
            return (para, assunto, corpo) -> ENVIADOS.add(new Mensagem(para, corpo));
        }

        /**
         * Relógio que anda, e não fixo: sem datas de criação distintas, "da mais nova para a mais
         * antiga" passaria por acaso — a ordem de empate é a que o banco quiser dar.
         */
        @Bean
        Clock clock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
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
    @Autowired private AccountService accounts;
    @Autowired private AdminUserService adminUsers;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private SessionRegistry sessionRegistry;
    @Autowired private AccessRateLimiter freio;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void prepararOAmbiente() {
        Ambiente.ENVIADOS.clear();
        Ambiente.agora = INICIO;
        // O freio é singleton e todos os testes vêm do mesmo IP; sem zerar, um caso pagaria pelas
        // tentativas do anterior. Janela zero remove tudo que é passado.
        freio.evictOlderThan(Duration.ZERO, INICIO.plusSeconds(1));
        // A linha semeada pela V2 nasce com CURRENT_TIMESTAMP do momento da migration, que é o
        // relógio de verdade e não o do teste — e é ela que a primeira conta de dono adota. Sem
        // envelhecê-la, a conta do dono apareceria como a mais NOVA da lista e a ordenação
        // dependeria de o relógio do teste estar antes ou depois do dia em que o teste roda.
        jdbc.update(
                "UPDATE app_user SET created_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = 1");
    }

    // ------------------------------------------------------------------ listagem

    @Test
    @DisplayName(
            "a listagem é de quem administra: 200 para ADMIN, 403 para conta comum, 401 sem sessão")
    void onlyAdminsSeeTheUserList() throws Exception {
        dono();
        comum("ana", "ana@example.test", "Ana");

        listar(comoDono(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(2));

        // Conta comum não vê a lista de e-mails de todo mundo — é a primeira resposta do app que
        // carrega dado pessoal de terceiros, e quem decide isso é um lugar só
        // (OwnerOnlyInterceptor).
        listar(como("google", "ana"), "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        mockMvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("nao_autenticado"));
    }

    @Test
    @DisplayName("a listagem pagina e vem da conta mais nova para a mais antiga")
    void theListIsPagedNewestFirst() throws Exception {
        dono();
        comum("ana", "ana@example.test", "Ana");
        avancar(Duration.ofHours(1));
        comum("bruno", "bruno@example.test", "Bruno");
        avancar(Duration.ofHours(1));
        comum("carla", "carla@example.test", "Carla");
        avancar(Duration.ofHours(1));
        comum("diego", "diego@example.test", "Diego");

        // A pergunta que leva alguém a abrir esta tela é "quem entrou agora": a ordem é a resposta,
        // e o total é do filtro inteiro, não do que coube na página.
        listar(comoDono(), "?size=2&page=0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].label").value("Diego"))
                .andExpect(jsonPath("$.items[1].label").value("Carla"));

        listar(comoDono(), "?size=2&page=1")
                .andExpect(jsonPath("$.items[0].label").value("Bruno"))
                .andExpect(jsonPath("$.items[1].label").value("Ana"));

        listar(comoDono(), "?size=2&page=2")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].label").value("Dono"))
                .andExpect(jsonPath("$.items[0].role").value("ADMIN"));
    }

    @Test
    @DisplayName("o tamanho de página tem teto de 100, e default de 20")
    void thePageSizeIsCapped() throws Exception {
        dono();

        // Sem teto, `?size=1000000` transforma a listagem em despejo do banco inteiro — e é o
        // banco que carrega o e-mail de todo mundo que se cadastrou.
        listar(comoDono(), "?size=1000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        listar(comoDono(), "").andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("status, papel, verificado e busca filtram — e valem combinados")
    void theFiltersCombineWithEachOther() throws Exception {
        dono();
        comum("ana", "ana@example.test", "Ana");
        avancar(Duration.ofHours(1));
        AppUser bruno = comum("bruno", "bruno@example.test", "Bruno");
        avancar(Duration.ofHours(1));
        // Cadastro por provedor que não devolveu e-mail verificado: tem endereço e não tem dono do
        // endereço. É a linha que quem administra mais precisa reconhecer, e a que não se promove.
        AppUser carla = naoVerificada("carla", "carla@example.test", "Carla");
        bloquear(bruno.getId(), "spam na fila de feedback").andExpect(status().isOk());

        listar(comoDono(), "?role=ADMIN")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].label").value("Dono"));

        listar(comoDono(), "?status=RECUSADO")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].label").value("Bruno"));

        listar(comoDono(), "?verificado=false")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(carla.getId()))
                .andExpect(jsonPath("$.items[0].emailVerified").value(false))
                // O endereço vem da identidade quando a conta ainda não é dona dele: escondê-lo
                // deixaria a linha sem como ser reconhecida.
                .andExpect(jsonPath("$.items[0].email").value("carla@example.test"))
                .andExpect(jsonPath("$.items[0].providers[0]").value("google"));

        // A busca alcança o e-mail da identidade, e não só o `primary_email` — quem procura pelo
        // endereço que a pessoa digitou procura justamente pela conta que não confirmou nada.
        listar(comoDono(), "?busca=carla@example")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(carla.getId()));

        // Combinados: interseção, e não união. Bloqueada continua sendo conta verificada e comum —
        // um filtro não sabe nada do outro.
        listar(comoDono(), "?role=USUARIO&verificado=true")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].label").value("Bruno"))
                .andExpect(jsonPath("$.items[1].label").value("Ana"));

        listar(comoDono(), "?status=APROVADO&verificado=true&busca=ana@example")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].label").value("Ana"));
    }

    @Test
    @DisplayName("conta de demonstração não aparece na lista e não aceita ação nenhuma")
    void theDemoAccountIsNotAdministered() throws Exception {
        dono();
        comum("ana", "ana@example.test", "Ana");
        AppUser demo = demonstracao();

        // Ela não é de ninguém e vence em duas horas (D39): aparecer na lista só ofereceria botões
        // que respondem 409.
        listar(comoDono(), "")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].label").value("Ana"))
                .andExpect(jsonPath("$.items[1].label").value("Dono"));

        // 409 e não 404: a conta existe, e esconder a diferença entre "não existe" e "não se
        // administra" deixaria quem administra procurando um id que está bem ali.
        promover(demo.getId(), "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admin_conta_de_demonstracao"));
        bloquear(demo.getId(), "por via das dúvidas")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admin_conta_de_demonstracao"));

        assertThat(users.findById(demo.getId()).orElseThrow().isApproved()).isTrue();
    }

    @Test
    @DisplayName("conta que não existe responde 404 nas duas ações")
    void anUnknownAccountIs404() throws Exception {
        dono();

        promover(999_999L, "ADMIN")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("conta_nao_encontrada"));
        bloquear(999_999L, "qualquer motivo")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("conta_nao_encontrada"));
    }

    // ------------------------------------------------------------------ papel (#89)

    @Test
    @DisplayName("promover dá acesso à administração na hora, e rebaixar tira de volta")
    void promotingGrantsAdminRightAway() throws Exception {
        AppUser dono = dono();
        AppUser ana = comum("ana", "ana@example.test", "Ana");

        mockMvc.perform(get("/api/me").with(como("google", "ana")))
                .andExpect(jsonPath("$.role").value("USUARIO"));
        listar(como("google", "ana"), "").andExpect(status().isForbidden());

        avancar(Duration.ofHours(1));
        promover(ana.getId(), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.id").value(ana.getId()));

        // Sem redeploy e sem novo login: o papel virou dado (D49), e é dele que o app responde.
        mockMvc.perform(get("/api/me").with(como("google", "ana")))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        listar(como("google", "ana"), "").andExpect(status().isOk());

        // E a trilha diz quem decidiu — é a pergunta que alguém faz seis meses depois.
        AppUser promovida = users.findById(ana.getId()).orElseThrow();
        assertThat(promovida.getRoleChangedBy()).isEqualTo(dono.getId());
        assertThat(promovida.getRoleChangedAt()).isEqualTo(INICIO.plus(Duration.ofHours(1)));

        promover(ana.getId(), "USUARIO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USUARIO"));
        mockMvc.perform(get("/api/me").with(como("google", "ana")))
                .andExpect(jsonPath("$.role").value("USUARIO"));
        listar(como("google", "ana"), "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));
    }

    @Test
    @DisplayName("não se promove quem nunca confirmou o e-mail")
    void anUnverifiedAccountIsNotPromoted() throws Exception {
        dono();
        AppUser carla = naoVerificada("carla", "carla@example.test", "Carla");

        // Sem esta guarda, digitar o endereço de outra pessoa num provedor que não verifica e-mail
        // seria caminho para a administração do app.
        promover(carla.getId(), "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admin_email_nao_verificado"));

        assertThat(users.findById(carla.getId()).orElseThrow().getRole()).isEqualTo(Role.USUARIO);
        listar(como("google", "carla"), "").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ninguém rebaixa nem bloqueia a própria conta")
    void noOneActsOnTheirOwnAccount() throws Exception {
        AppUser dono = dono();
        AppUser ana = comum("ana", "ana@example.test", "Ana");
        promover(ana.getId(), "ADMIN").andExpect(status().isOk());

        // É o clique de que ninguém se recupera sozinho: quem se rebaixa perde a tela que
        // desfaria o rebaixamento.
        promover(dono.getId(), "USUARIO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admin_acao_sobre_si"));
        bloquear(dono.getId(), "engano")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admin_acao_sobre_si"));

        AppUser intacta = users.findById(dono.getId()).orElseThrow();
        assertThat(intacta.getRole()).isEqualTo(Role.ADMIN);
        assertThat(intacta.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
        assertThat(intacta.getDecidedReason()).isNull();
        listar(comoDono(), "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a última conta de administração não é rebaixada nem bloqueada")
    void theLastAdminSurvives() throws Exception {
        AppUser dono = dono();
        AppUser ana = comum("ana", "ana@example.test", "Ana");
        assertThat(users.countByRoleAndDemoExpiresAtIsNull(Role.ADMIN)).isEqualTo(1);

        // Pela API, o caso que esta guarda pega é o administrador solitário agindo sobre a
        // própria conta — e é por ela vir ANTES da guarda de "sobre si mesmo" que a mensagem
        // específica chega até a tela em vez de virar código morto.
        promover(dono.getId(), "USUARIO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admin_ultimo_admin"));
        bloquear(dono.getId(), "engano")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admin_ultimo_admin"));

        // E direto no serviço, com ator diferente do alvo: é o cinto para o dia em que houver
        // outro caminho (uma conta de serviço, uma migração, um script) mexendo no papel de
        // alguém. Sem ele, o app fica sem ninguém que administre e só um redeploy com
        // FOS_OWNER_EMAILS conserta.
        AdminActionException rebaixar =
                assertThrows(
                        AdminActionException.class,
                        () -> adminUsers.changeRole(ana.getId(), dono.getId(), Role.USUARIO));
        assertThat(rebaixar.code()).isEqualTo("admin_ultimo_admin");

        AdminActionException bloquear =
                assertThrows(
                        AdminActionException.class,
                        () ->
                                adminUsers.changeStatus(
                                        ana.getId(),
                                        dono.getId(),
                                        AccessStatus.RECUSADO,
                                        "tanto faz"));
        assertThat(bloquear.code()).isEqualTo("admin_ultimo_admin");

        AppUser intacta = users.findById(dono.getId()).orElseThrow();
        assertThat(intacta.getRole()).isEqualTo(Role.ADMIN);
        assertThat(intacta.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
    }

    // ------------------------------------------------------------------ bloqueio (#90)

    @Test
    @DisplayName("bloquear barra a aba já aberta na ação seguinte, sem derrubar a sessão")
    void blockingStopsAnOpenSessionOnTheVeryNextRequest() throws Exception {
        dono();
        cadastrarEConfirmar("ana@example.test");
        MockHttpSession aberta = new MockHttpSession();
        entrar("ana@example.test", aberta).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/streak").session(aberta)).andExpect(status().isOk());
        String idDaSessao = aberta.getId();
        Long ana = contaDe("ana@example.test").getId();

        bloquear(ana, "spam na fila de feedback")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessStatus").value("RECUSADO"));

        // A MESMA sessão, que estava aberta e funcionando, é barrada na ação seguinte — e com o
        // código que diz à web qual tela mostrar. Não é preciso derrubar sessão nenhuma para isso:
        // o portão relê `access_status` a cada requisição.
        mockMvc.perform(get("/api/streak").session(aberta))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));

        // E a sessão NÃO é expirada, de propósito. Se fosse, o ConcurrentSessionFilter responderia
        // 401 `sessao_encerrada` ANTES do portão: o 403 acima nunca aconteceria, e a web — que lê
        // 401 como "não há sessão" — devolveria a pessoa bloqueada para a tela de login, que é o
        // looping que o código no corpo existe para evitar. Foi o defeito da primeira versão da
        // #90, e é esta linha que impede a volta dele.
        assertThat(sessionRegistry.getSessionInformation(idDaSessao).isExpired()).isFalse();

        // Sessão nova cai no mesmo lugar — bloqueio não é sobre a sessão, é sobre a conta.
        MockHttpSession nova = new MockHttpSession();
        entrar("ana@example.test", nova).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/streak").session(nova))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));
    }

    @Test
    @DisplayName("conta bloqueada não volta a usar o app nem por senha nem por provedor")
    void aBlockedAccountCannotComeBackByAnyDoor() throws Exception {
        dono();
        cadastrarEConfirmar("ana@example.test");
        // A mesma conta, com as duas portas: o e-mail verificado é o que vincula a identidade nova
        // à conta que já existe (D47). Bloqueio que valesse para uma porta e não para a outra não
        // seria bloqueio.
        AppUser ana = accounts.registerLogin("google", "ana", "ana@example.test", true, "Ana");
        assertThat(identities.findByUserId(ana.getId())).hasSize(2);

        bloquear(ana.getId(), "combinado com a pessoa").andExpect(status().isOk());

        mockMvc.perform(get("/api/streak").with(como("google", "ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));

        // A porta de senha abre — o portão não é o login, é o interceptor —, e a primeira coisa
        // que a sessão tenta fazer já para nele.
        MockHttpSession sessao = new MockHttpSession();
        entrar("ana@example.test", sessao).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/streak").session(sessao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));
    }

    @Test
    @DisplayName("conta bloqueada ainda consegue se excluir")
    void aBlockedAccountCanStillDeleteItself() throws Exception {
        dono();
        cadastrarEConfirmar("ana@example.test");
        Long ana = contaDe("ana@example.test").getId();
        bloquear(ana, "spam na fila de feedback").andExpect(status().isOk());

        MockHttpSession sessao = new MockHttpSession();
        entrar("ana@example.test", sessao).andExpect(status().isNoContent());

        // `DELETE /api/me` fica fora do portão de propósito: bloquear não pode virar sequestro de
        // dado pessoal. Quem foi bloqueado perde o app, não o direito de sumir dele.
        mockMvc.perform(delete("/api/me").session(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(users.findById(ana)).isEmpty();
        assertThat(identities.findByUserId(ana)).isEmpty();
    }

    @Test
    @DisplayName("desbloquear devolve o acesso")
    void unblockingGivesTheAccessBack() throws Exception {
        dono();
        AppUser ana = comum("ana", "ana@example.test", "Ana");

        bloquear(ana.getId(), "spam na fila de feedback").andExpect(status().isOk());
        mockMvc.perform(get("/api/streak").with(como("google", "ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));

        // Reversível, e é isso que faz o bloqueio ser bloqueio e não exclusão da conta alheia.
        desbloquear(ana.getId(), "resolvido com a pessoa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessStatus").value("APROVADO"));
        mockMvc.perform(get("/api/streak").with(como("google", "ana"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("quem decidiu, quando e por quê ficam gravados nas duas direções")
    void theDecisionTrailIsWrittenBothWays() throws Exception {
        AppUser dono = dono();
        AppUser ana = comum("ana", "ana@example.test", "Ana");

        avancar(Duration.ofHours(1));
        bloquear(ana.getId(), "spam na fila de feedback").andExpect(status().isOk());

        AppUser bloqueada = users.findById(ana.getId()).orElseThrow();
        assertThat(bloqueada.getAccessStatus()).isEqualTo(AccessStatus.RECUSADO);
        assertThat(bloqueada.getDecidedAt()).isEqualTo(INICIO.plus(Duration.ofHours(1)));
        assertThat(bloqueada.getDecidedBy()).isEqualTo(dono.getId());
        assertThat(bloqueada.getDecidedReason()).isEqualTo("spam na fila de feedback");

        // Desbloquear também é decisão de alguém: a trilha é reescrita, e não apagada.
        avancar(Duration.ofHours(2));
        desbloquear(ana.getId(), "resolvido com a pessoa").andExpect(status().isOk());

        AppUser liberada = users.findById(ana.getId()).orElseThrow();
        assertThat(liberada.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
        assertThat(liberada.getDecidedAt()).isEqualTo(INICIO.plus(Duration.ofHours(3)));
        assertThat(liberada.getDecidedBy()).isEqualTo(dono.getId());
        assertThat(liberada.getDecidedReason()).isEqualTo("resolvido com a pessoa");

        listar(comoDono(), "?busca=ana@example")
                .andExpect(jsonPath("$.items[0].decidedReason").value("resolvido com a pessoa"))
                .andExpect(jsonPath("$.items[0].decidedAt").exists());
    }

    // ------------------------------------------------------------------ semente (D49)

    @Test
    @DisplayName("fos.auth.owner-emails promove quem tem e-mail verificado, e só esses")
    void theOwnerEmailListSeedsAdmins() throws Exception {
        // No login: quem está na lista e chega com e-mail verificado já entra administrando.
        AppUser dono = dono();
        assertThat(dono.getRole()).isEqualTo(Role.ADMIN);

        // E na subida: conta que já existia antes de a lista ser preenchida é alcançada pela
        // semente — é a saída de emergência de um ambiente que ficou sem quem administre.
        AppUser semente = users.save(AppUser.approved("Semente", Ambiente.agora));
        semente.claimPrimaryEmail("semente@example.test");
        identities.save(
                new UserIdentity(
                        semente.getId(),
                        "google",
                        "semente-sub",
                        "semente@example.test",
                        true,
                        "Semente",
                        Ambiente.agora));
        // Mesmo endereço, sem verificação nenhuma: é a conta que a semente NÃO pode alcançar.
        AppUser impostor =
                accounts.registerLogin(
                        "facebook", "impostor", "semente@example.test", false, "Impostor");
        assertThat(semente.getRole()).isEqualTo(Role.USUARIO);

        accounts.seedAdmins();

        assertThat(users.findById(semente.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(users.findById(impostor.getId()).orElseThrow().getRole())
                .isEqualTo(Role.USUARIO);
        listar(como("google", "semente-sub"), "").andExpect(status().isOk());
        listar(como("facebook", "impostor"), "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));
    }

    // ------------------------------------------------------------------ auxiliares

    private void avancar(Duration quanto) {
        Ambiente.agora = Ambiente.agora.plus(quanto);
    }

    /** A conta que administra: provedor, e-mail verificado, endereço em fos.auth.owner-emails. */
    private AppUser dono() {
        return accounts.registerLogin("google", "dono", DONO, true, "Dono");
    }

    private AppUser comum(String subject, String email, String nome) {
        return accounts.registerLogin("google", subject, email, true, nome);
    }

    /** Conta de provedor que não devolveu e-mail verificado: tem endereço, não é dona dele. */
    private AppUser naoVerificada(String subject, String email, String nome) {
        return accounts.registerLogin("google", subject, email, false, nome);
    }

    private AppUser demonstracao() {
        AppUser demo =
                users.save(
                        AppUser.demo(
                                "Demonstração",
                                Ambiente.agora,
                                Ambiente.agora.plus(Duration.ofHours(2))));
        identities.save(
                new UserIdentity(
                        demo.getId(),
                        DemoAuthenticationToken.PROVIDER,
                        "demo-sub",
                        null,
                        false,
                        "Demonstração",
                        Ambiente.agora));
        return demo;
    }

    private ResultActions listar(RequestPostProcessor quem, String consulta) throws Exception {
        return mockMvc.perform(get("/api/admin/usuarios" + consulta).with(quem));
    }

    private ResultActions promover(Long id, String papel) throws Exception {
        return mockMvc.perform(
                post("/api/admin/usuarios/" + id + "/role")
                        .with(comoDono())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"%s\"}".formatted(papel)));
    }

    private ResultActions bloquear(Long id, String motivo) throws Exception {
        return decidirAcesso(id, "RECUSADO", motivo);
    }

    private ResultActions desbloquear(Long id, String motivo) throws Exception {
        return decidirAcesso(id, "APROVADO", motivo);
    }

    private ResultActions decidirAcesso(Long id, String status, String motivo) throws Exception {
        return mockMvc.perform(
                post("/api/admin/usuarios/" + id + "/status")
                        .with(comoDono())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"status\":\"%s\",\"motivo\":\"%s\"}".formatted(status, motivo)));
    }

    /** Cadastro com senha, confirmado: é o que dá uma conta com sessão de verdade para derrubar. */
    private void cadastrarEConfirmar(String email) throws Exception {
        mockMvc.perform(
                        post("/api/auth/cadastro")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"%s\",\"senha\":\"%s\",\"nome\":\"Ana\"}"
                                                .formatted(email, SENHA)))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/verificar/" + tokenDeVerificacao(email)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private ResultActions entrar(String email, MockHttpSession sessao) throws Exception {
        return mockMvc.perform(
                post("/api/auth/login")
                        .session(sessao)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)));
    }

    /** O token do link que foi para a caixa de quem se cadastrou. */
    private static String tokenDeVerificacao(String email) {
        String corpo =
                Ambiente.ENVIADOS.stream()
                        .filter(mensagem -> mensagem.para().equals(email))
                        .toList()
                        .getLast()
                        .corpo();
        String caminho = corpo.substring(corpo.indexOf("/confirmar-email/")).split("\\s+")[0];
        return caminho.substring(caminho.lastIndexOf('/') + 1);
    }

    private AppUser contaDe(String email) {
        UserIdentity identidade =
                identities
                        .findByProviderAndProviderSubject(
                                PasswordAuthenticationToken.PROVIDER, email)
                        .orElseThrow();
        return users.findById(identidade.getUserId()).orElseThrow();
    }

    private RequestPostProcessor comoDono() {
        return como("google", "dono");
    }

    /** Sessão do par (provedor, subject) — o mesmo que o fluxo real deixa na autenticação. */
    private static RequestPostProcessor como(String provider, String subject) {
        return oauth2Login()
                .clientRegistration(
                        ClientRegistration.withRegistrationId(provider)
                                .clientId("test")
                                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                                .redirectUri("{baseUrl}/api/login/oauth2/code/" + provider)
                                .authorizationUri("https://example.test/authorize")
                                .tokenUri("https://example.test/token")
                                .userInfoUri("https://example.test/userinfo")
                                .userNameAttributeName("sub")
                                .build())
                .attributes(attributes -> attributes.put("sub", subject));
    }
}
