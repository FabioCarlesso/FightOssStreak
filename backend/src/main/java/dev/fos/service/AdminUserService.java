package dev.fos.service;

import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.model.Role;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.web.dto.AdminUserDtos;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As contas do sistema, do lado de quem administra (#88 → #89, #90; D49).
 *
 * <p>Três ações e nenhuma a mais: <b>listar</b>, <b>mudar o papel</b> e
 * <b>bloquear/desbloquear</b>. O que está fora é tão decidido quanto o que está dentro — não se
 * apaga a conta de outra pessoa (exclusão é do titular, por {@code DELETE /api/me}, e bloqueio já
 * resolve abuso sendo reversível), não se edita dado de conta alheia, e não voltou fila de
 * aprovação nenhuma (D48).
 *
 * <p>Quem pode chamar isto é decidido <b>fora</b> daqui, pelo {@code OwnerOnlyInterceptor} sobre
 * {@code /api/admin/**}: nenhum método desta classe pergunta quem é o chamador, só quem é o
 * <em>alvo</em>. Duas perguntas sobre a mesma coisa em dois lugares acabam discordando.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    /**
     * Teto de tamanho de página, explícito e não negociável pelo cliente.
     *
     * <p>Sem ele, {@code ?size=1000000} transforma a listagem em despejo do banco inteiro numa
     * requisição — e esta listagem carrega o e-mail de todo mundo que se cadastrou.
     */
    public static final int MAX_PAGE_SIZE = 100;

    public static final int DEFAULT_PAGE_SIZE = 20;

    private final AppUserRepository users;
    private final UserIdentityRepository identities;
    private final Clock clock;

    public AdminUserService(
            AppUserRepository users, UserIdentityRepository identities, Clock clock) {
        this.users = users;
        this.identities = identities;
        this.clock = clock;
    }

    /**
     * Uma página de contas, da mais nova para a mais antiga.
     *
     * <p>Filtros combinam entre si — quem só quer "bloqueadas e não verificadas" recebe a
     * interseção, e não a união. A ordem por criação decrescente é a que responde a pergunta que
     * leva alguém a abrir esta tela: quem entrou agora.
     */
    @Transactional(readOnly = true)
    public AdminUserDtos.AdminUserPage list(
            AccessStatus status, Role role, Boolean verificado, String busca, int page, int size) {
        int tamanho = Math.clamp(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1, MAX_PAGE_SIZE);
        int pagina = Math.max(page, 0);
        Page<AppUser> encontrado =
                users.findAll(
                        filtro(status, role, verificado, busca),
                        PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "createdAt")));

        // Uma consulta para as identidades da página inteira, e não uma por linha: a listagem
        // mostra provedores vinculados em toda linha, e o N+1 aqui cresce com o número de contas
        // do app.
        Map<Long, List<UserIdentity>> porConta =
                identities
                        .findByUserIdIn(
                                encontrado.getContent().stream().map(AppUser::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(UserIdentity::getUserId));

        List<AdminUserDtos.AdminUserView> items =
                encontrado.getContent().stream()
                        .map(user -> toView(user, porConta.getOrDefault(user.getId(), List.of())))
                        .toList();
        return new AdminUserDtos.AdminUserPage(
                items, pagina, tamanho, encontrado.getTotalElements(), encontrado.getTotalPages());
    }

    /**
     * Promove ou rebaixa (#89).
     *
     * <p>Três guardas, e cada uma é um estrago diferente evitado: promover conta que nunca provou o
     * endereço daria administração a quem digitou o e-mail de outra pessoa; rebaixar a si mesmo é o
     * clique de que ninguém se recupera sozinho; e chegar a zero {@code ADMIN} tranca a
     * administração do app até um redeploy com {@code FOS_OWNER_EMAILS}.
     */
    @Transactional
    public AdminUserDtos.AdminUserView changeRole(Long actorId, Long targetId, Role role) {
        AppUser alvo = administrable(targetId);
        if (role == Role.ADMIN && !alvo.hasVerifiedEmail()) {
            throw new AdminActionException(AdminActionException.Motivo.EMAIL_NAO_VERIFICADO);
        }
        if (role != Role.ADMIN) {
            refuseIfLastAdmin(alvo);
            refuseIfSelf(actorId, alvo);
        }
        if (alvo.getRole() != role) {
            alvo.changeRole(role, actorId, Instant.now(clock));
            log.info("Conta {} passou a {} por decisão da conta {}", alvo.getId(), role, actorId);
        }
        return toView(alvo);
    }

    /**
     * Bloqueia ou desbloqueia (#90).
     *
     * <p>Uma linha do banco, e nada mais — <b>de propósito</b>. Quem barra é o {@code
     * AccessGateInterceptor}, que lê {@code access_status} <em>a cada requisição</em> e existe
     * desde a D48 esperando por este produtor; nenhum portão novo foi escrito, e a sessão aberta da
     * conta-alvo já é barrada na ação seguinte sem que ninguém precise encostar nela.
     *
     * <p><b>Este método não derruba as sessões abertas, e a primeira versão derrubava.</b> A
     * intenção era boa e o efeito era o contrário do pretendido: {@code SessionLogin.endSessionsOf}
     * marca a sessão como expirada, e aí quem responde a requisição seguinte é o {@code
     * ConcurrentSessionFilter} com <b>401 {@code sessao_encerrada}</b> — antes de qualquer
     * interceptor. O {@code 403 acesso_recusado} nunca acontecia, a web lia o 401 como "não há
     * sessão" e mandava a pessoa de volta para o login, que é precisamente o looping que o código
     * no corpo existe para evitar. Derrubar a sessão não adiantava bloqueio nenhum (o portão já
     * barrava) e custava a única tela que explica o que houve.
     *
     * <p>A redefinição de senha continua derrubando sessão, e ali é outro problema: lá o ponto é
     * expulsar quem entrou com a senha antiga, e 401 é a resposta certa porque a pessoa
     * <em>deve</em> entrar de novo.
     */
    @Transactional
    public AdminUserDtos.AdminUserView changeStatus(
            Long actorId, Long targetId, AccessStatus status, String motivo) {
        AppUser alvo = administrable(targetId);
        if (status == AccessStatus.RECUSADO) {
            refuseIfLastAdmin(alvo);
            refuseIfSelf(actorId, alvo);
        }
        alvo.decideAccess(status, actorId, normalize(motivo), Instant.now(clock));
        log.info(
                "Acesso da conta {} decidido como {} pela conta {}", alvo.getId(), status, actorId);
        return toView(alvo);
    }

    // ------------------------------------------------------------------ internos

    /** A conta existe e é administrável — demonstração existe e não é (D39). */
    private AppUser administrable(Long id) {
        AppUser user = users.findById(id).orElseThrow(() -> new AdminUserNotFoundException(id));
        if (user.isDemo()) {
            throw new AdminActionException(AdminActionException.Motivo.CONTA_DE_DEMONSTRACAO);
        }
        return user;
    }

    private static void refuseIfSelf(Long actorId, AppUser alvo) {
        if (alvo.getId().equals(actorId)) {
            throw new AdminActionException(AdminActionException.Motivo.ACAO_SOBRE_SI);
        }
    }

    /**
     * Recusa a ação que deixaria o app sem administrador.
     *
     * <p>Contado no banco, e não em memória: a pergunta é sobre o conjunto, e a resposta muda
     * enquanto a tela de quem administra está aberta.
     *
     * <p><b>Roda antes da guarda de "sobre si mesmo", e a ordem é a diferença entre uma mensagem
     * útil e uma inalcançável.</b> Quem chama já passou pelo {@code OwnerOnlyInterceptor} e é
     * {@code ADMIN}: se o alvo é outra conta de administração, então existem duas e esta guarda
     * nunca bate. O único caso em que ela tem o que dizer é o administrador solitário agindo sobre
     * a própria conta — e nesse caso "esta é a última conta de administração" explica por que não
     * dá, enquanto "não mexa na sua própria conta" deixaria a pessoa achando que bastaria pedir a
     * outro administrador, que não existe. Invertida, a mensagem específica vira código morto.
     */
    private void refuseIfLastAdmin(AppUser alvo) {
        if (alvo.isAdmin() && users.countByRoleAndDemoExpiresAtIsNull(Role.ADMIN) <= 1) {
            throw new AdminActionException(AdminActionException.Motivo.ULTIMO_ADMIN);
        }
    }

    private static String normalize(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        String limpo = motivo.trim();
        return limpo.length() > 500 ? limpo.substring(0, 500) : limpo;
    }

    private AdminUserDtos.AdminUserView toView(AppUser user) {
        return toView(user, identities.findByUserId(user.getId()));
    }

    private static AdminUserDtos.AdminUserView toView(AppUser user, List<UserIdentity> identities) {
        // O endereço verificado manda; sem ele, o que a identidade trouxe. Cadastro criado e nunca
        // confirmado tem e-mail e não tem dono do endereço — e é justamente a linha que quem
        // administra precisa reconhecer.
        String email =
                user.getPrimaryEmail() != null
                        ? user.getPrimaryEmail()
                        : identities.stream()
                                .map(UserIdentity::getEmail)
                                .filter(value -> value != null && !value.isBlank())
                                .findFirst()
                                .orElse(null);
        return new AdminUserDtos.AdminUserView(
                user.getId(),
                user.getLabel(),
                email,
                user.hasVerifiedEmail(),
                identities.stream().map(UserIdentity::getProvider).toList(),
                user.getRole(),
                user.getAccessStatus(),
                user.getCreatedAt(),
                user.getDecidedAt(),
                user.getDecidedReason());
    }

    /**
     * Os filtros, montados só com o que veio.
     *
     * <p>Demonstração fica de fora sempre, e não por filtro: ela não é de ninguém, vence em duas
     * horas e não aceita ação nenhuma (D39) — aparecer na lista só ofereceria botões que respondem
     * 409.
     */
    private static Specification<AppUser> filtro(
            AccessStatus status, Role role, Boolean verificado, String busca) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.isNull(root.get("demoExpiresAt")));

            // Conta sem identidade nenhuma também fica de fora, e há exatamente uma: a linha
            // semeada pela V2, que é o progresso do autor de antes de existir login, esperando ser
            // adotada. Sem identidade ela não autentica por caminho nenhum — não é pessoa, é
            // progresso órfão —, e numa tela que lista pessoas apareceria como conta fantasma
            // (`usuario-local`, sem e-mail) em todo ambiente onde o primeiro login não for de um
            // endereço em `owner-emails`.
            Subquery<Long> comIdentidade = query.subquery(Long.class);
            var qualquerIdentidade = comIdentidade.from(dev.fos.model.UserIdentity.class);
            comIdentidade
                    .select(qualquerIdentidade.get("userId"))
                    .where(cb.equal(qualquerIdentidade.get("userId"), root.get("id")));
            predicados.add(cb.exists(comIdentidade));
            if (status != null) {
                predicados.add(cb.equal(root.get("accessStatus"), status));
            }
            if (role != null) {
                predicados.add(cb.equal(root.get("role"), role));
            }
            if (verificado != null) {
                predicados.add(
                        verificado
                                ? cb.isNotNull(root.get("primaryEmail"))
                                : cb.isNull(root.get("primaryEmail")));
            }
            String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
            if (!termo.isEmpty()) {
                String like = "%" + termo + "%";
                // O e-mail da identidade entra na busca por subconsulta: quem ainda não confirmou o
                // cadastro não tem `primary_email`, e é exatamente essa conta que alguém procura
                // pelo endereço que digitou.
                Subquery<Long> comEmail = query.subquery(Long.class);
                var identidade = comEmail.from(dev.fos.model.UserIdentity.class);
                comEmail.select(identidade.get("userId"))
                        .where(
                                cb.equal(identidade.get("userId"), root.get("id")),
                                cb.like(cb.lower(identidade.get("email")), like));
                predicados.add(
                        cb.or(
                                cb.like(cb.lower(root.get("label")), like),
                                cb.like(cb.lower(root.get("primaryEmail")), like),
                                cb.exists(comEmail)));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
