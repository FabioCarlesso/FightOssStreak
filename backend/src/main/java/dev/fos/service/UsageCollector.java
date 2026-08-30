package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.AppUser;
import dev.fos.model.UsageEvent;
import dev.fos.model.UsageEventType;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.UsageEventRepository;
import dev.fos.web.dto.UsageDtos;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Onde um evento de uso vira linha (#84, D50).
 *
 * <p>Três compromissos, e os três estão no código e não só na doc:
 *
 * <ol>
 *   <li><b>Nada quebra por causa da coleta.</b> Todo caminho público aqui engole a própria falha:
 *       evento que não grava é evento perdido, nunca tela quebrada. A landing (D33) em especial não
 *       pode passar a depender disto para renderizar.
 *   <li><b>O IP é consumido e descartado.</b> Ele entra em dois lugares — derivar país e compor a
 *       chave de visita — e some ao fim do método. Não existe campo, coluna nem log com IP.
 *   <li><b>O cliente não decide nada que dê para derivar.</b> Dispositivo, navegador, sistema,
 *       idioma, país e o tipo dos eventos de funil saem da requisição, não do corpo.
 * </ol>
 */
@Service
public class UsageCollector {

    private static final Logger log = LoggerFactory.getLogger(UsageCollector.class);

    /**
     * Caminho dos eventos que o backend emite.
     *
     * <p>Eles não acontecem "em uma rota" — acontecem quando um cadastro é criado, um e-mail é
     * confirmado. Marcá-los com o caminho da chamada de API misturaria rota de API com rota de tela
     * na mesma coluna, e a dimensão CAMINHO só conta {@link UsageEventType#PAGINA} de qualquer
     * forma.
     */
    public static final String SEM_CAMINHO = "-";

    /**
     * Freio da única rota de escrita que qualquer um alcança sem sessão.
     *
     * <p>Por chave de visita, não por conta: quem abusaria disto não tem conta. O teto é folgado de
     * propósito — navegar rápido pelo app não pode virar evento perdido —, e o que ele impede é um
     * laço enchendo a tabela.
     *
     * <p><b>Ele só vale o que a chave valer, e a chave tem uma metade que o cliente escolhe.</b> A
     * {@link VisitKey} é derivada do IP e do {@code User-Agent}. O IP deixou de ser forjável com a
     * #77 (ver {@link ClientIp}), mas o {@code User-Agent} é do cliente por definição: um diferente
     * por requisição já é chave diferente por requisição. É por isso que existe o {@link
     * #TETO_DIARIO} abaixo: um freio que não dependa de nada que o cliente escolha.
     */
    private static final int TETO_POR_VISITA = 300;

    private static final Duration JANELA = Duration.ofMinutes(10);

    /** Prefixo das chaves desta coleta no freio compartilhado. */
    private static final String PREFIXO_DO_FREIO = "uso:";

    /**
     * Janela do degrau de retorno do funil (#85). Sete dias, contados do primeiro dia com drill.
     */
    private static final int DIAS_DA_JANELA_DE_RETORNO = 7;

    private final UsageEventRepository events;
    private final DrillLogRepository drills;
    private final VisitKey visitKey;
    private final GeoIpDatabase geoIp;
    private final CurrentUserProvider currentUser;
    private final AccessRateLimiter rateLimiter;
    private final ClientIp clientIp;
    private final FosProperties.Usage config;
    private final Clock clock;

    /** Estado do teto diário. Protegido por {@code this}: é escrito em toda navegação. */
    private LocalDate diaDoTeto;

    private int gravadosNoDia;
    private boolean tetoJaAvisado;

    public UsageCollector(
            UsageEventRepository events,
            // As perguntas "este é o primeiro drill desta conta?" e "esta conta voltou?" moram
            // aqui, e não no controller, porque são parte da DEFINIÇÃO dos eventos de funil — quem
            // chama só sabe que um drill acabou de ser registrado.
            DrillLogRepository drills,
            VisitKey visitKey,
            GeoIpDatabase geoIp,
            CurrentUserProvider currentUser,
            AccessRateLimiter rateLimiter,
            ClientIp clientIp,
            FosProperties properties,
            Clock clock) {
        this.events = events;
        this.drills = drills;
        this.visitKey = visitKey;
        this.geoIp = geoIp;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.config = properties.usage();
        this.clock = clock;
    }

    /** Uma mudança de rota, vinda do web. É o único evento que o cliente pode disparar. */
    public void pageView(HttpServletRequest request, UsageDtos.EventRequest body) {
        UsageDtos.EventRequest corpo =
                body == null ? new UsageDtos.EventRequest(null, null, null, null, null) : body;
        registrar(
                request,
                UsageEventType.PAGINA,
                UsagePaths.normalize(corpo.caminho()),
                host(corpo.referrer(), request),
                termo(corpo.utmSource()),
                termo(corpo.utmMedium()),
                termo(corpo.utmCampaign()),
                true);
    }

    /**
     * Um degrau do funil, emitido pelo backend.
     *
     * <p>Sem freio: quem chega aqui já passou pelo freio da ação de verdade (cadastro,
     * demonstração, drill), e perder um evento de funil por causa de um contador de página seria
     * perder justamente o número que a issue existe para produzir.
     */
    public void funnel(UsageEventType tipo) {
        registrar(
                requisicaoAtual(),
                tipo,
                SEM_CAMINHO,
                UsageEvent.DESCONHECIDO,
                UsageEvent.DESCONHECIDO,
                UsageEvent.DESCONHECIDO,
                UsageEvent.DESCONHECIDO,
                false);
    }

    /**
     * Os dois degraus de funil que um drill pode produzir — e cada um sai uma vez só por conta.
     *
     * <p>Chamado depois que o drill foi gravado, então "primeiro" é {@code count == 1}, e "voltou"
     * é o dia distinto de número dois. Sem memória própria nos dois casos, e de propósito: a tabela
     * crua vive 90 dias, e um "já emiti este evento" lido dela reemitiria tudo passados três meses.
     * O estado que decide é o do próprio histórico de drills, que não expira.
     */
    public void drillRegistered(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            if (drills.countByUserId(userId) == 1) {
                funnel(UsageEventType.PRIMEIRO_DRILL);
                return;
            }
            // Da mais nova para a mais antiga (é a ordem da consulta). Exatamente dois dias
            // distintos é o instante em que a conta voltou pela primeira vez: com um não voltou,
            // com três já foi contada.
            List<LocalDate> dias = drills.findDistinctDrillDates(userId);
            if (dias.size() == 2
                    && !dias.get(1).plusDays(DIAS_DA_JANELA_DE_RETORNO).isBefore(dias.get(0))) {
                funnel(UsageEventType.RETORNO_EM_7_DIAS);
            }
        } catch (RuntimeException falha) {
            log.debug("Evento de funil do drill não registrado", falha);
        }
    }

    private void registrar(
            HttpServletRequest request,
            UsageEventType tipo,
            String caminho,
            String referrerHost,
            String utmSource,
            String utmMedium,
            String utmCampaign,
            boolean comFreio) {
        if (!config.enabled() || request == null) {
            return;
        }
        try {
            // O IP nasce e morre neste método: vira país e vira hash, e nenhuma das duas coisas
            // volta a ser IP. Não passe esta variável adiante.
            String ip = clientIp.of(request);
            String userAgent = request.getHeader("User-Agent");
            String chave = visitKey.of(ip, userAgent);

            Instant agora = Instant.now(clock);
            LocalDate hoje = LocalDate.now(clock);
            if (comFreio) {
                // Varrer ANTES de contar, e só as chaves da coleta: sem isto, cada dia deposita um
                // conjunto novo de chaves `uso:` que nunca sai do mapa — a `visit_key` roda por
                // dia, então nem as de ontem voltam a ser tocadas. Com prefixo porque a varredura
                // sem ele apagaria o contador de força bruta de senha (#81), que tem janela maior
                // — ver AccessRateLimiter.
                rateLimiter.evictOlderThan(PREFIXO_DO_FREIO, JANELA, agora);
                if (!rateLimiter.tryAcquire(
                        PREFIXO_DO_FREIO + chave, TETO_POR_VISITA, JANELA, agora)) {
                    return;
                }
                // Depois do freio por visita, e não antes: acesso que aquele já recusou não pode
                // gastar orçamento do dia.
                if (!cabeNoTetoDiario(hoje)) {
                    return;
                }
            }

            events.save(
                    new UsageEvent(
                            agora,
                            hoje,
                            tipo,
                            caminho,
                            chave,
                            usuarioAtual(),
                            UserAgents.device(userAgent),
                            UserAgents.browser(userAgent),
                            UserAgents.os(userAgent),
                            UserAgents.language(request.getHeader("Accept-Language")),
                            geoIp.country(ip),
                            geoIp.region(ip),
                            referrerHost,
                            utmSource,
                            utmMedium,
                            utmCampaign));
        } catch (RuntimeException falha) {
            // `debug` e não `warn`: se a coleta quebrar, ela quebra a cada requisição, e um log
            // por requisição transformaria um problema de métrica em um problema de operação.
            log.debug("Evento de uso {} não registrado", tipo, falha);
        }
    }

    /**
     * O teto do dia — o único limite que não depende de nada que o cliente escolha.
     *
     * <p>O freio por visita acima é chaveado na {@link VisitKey}, e quem varia o {@code User-Agent}
     * tem uma chave nova a cada requisição e passa por ele inteiro — o conserto da #77 tirou o IP
     * das mãos do cliente, e a outra metade da chave continua sendo dele. Este teto não pergunta de
     * quem veio o acesso: conta quantos foram gravados hoje e para. Não é filtro de abuso — quem
     * abusar gasta o orçamento do dia e a coleta legítima para junto —, é <b>teto de estrago</b>: a
     * tabela deixa de crescer sem limite, e o aviso no log diz que houve um dia assim.
     *
     * <p>Só acessos de tela. Evento de funil não passa por aqui: ele já é limitado pela ação de
     * verdade que o produz, e perdê-lo seria perder justamente o número que a issue existe para
     * produzir.
     */
    private synchronized boolean cabeNoTetoDiario(LocalDate hoje) {
        if (!hoje.equals(diaDoTeto)) {
            diaDoTeto = hoje;
            gravadosNoDia = 0;
            tetoJaAvisado = false;
        }
        if (gravadosNoDia >= config.dailyCap()) {
            if (!tetoJaAvisado) {
                // Uma vez por dia, e `warn`: ao contrário da falha de gravação, isto não é ruído de
                // requisição — é o número do dia deixando de ser confiável, e quem lê o painel
                // (fatia 2) precisa saber que aquele dia bateu no teto.
                log.warn(
                        "Coleta de uso: teto de {} acessos atingido em {}; o resto do dia não será"
                                + " gravado",
                        config.dailyCap(),
                        hoje);
                tetoJaAvisado = true;
            }
            return false;
        }
        gravadosNoDia++;
        return true;
    }

    /**
     * A conta da requisição, quando há uma.
     *
     * <p>Nunca propaga falha: a coleta vale igual para quem não tem sessão — é justamente quem
     * ainda não tem conta que a issue quer contar.
     */
    private Long usuarioAtual() {
        try {
            return currentUser.findCurrentUser().map(AppUser::getId).orElse(null);
        } catch (RuntimeException semSessao) {
            return null;
        }
    }

    /** A requisição em curso, para os eventos que o backend emite de dentro de um controller. */
    private static HttpServletRequest requisicaoAtual() {
        return RequestContextHolder.getRequestAttributes()
                        instanceof ServletRequestAttributes atributos
                ? atributos.getRequest()
                : null;
    }

    /**
     * Só o host do referrer, e nunca o próprio app.
     *
     * <p>Navegação interna não é origem: sem este corte, o host do FOS seria a "origem" mais comum
     * do painel e afogaria a única resposta que interessa — de que link a pessoa veio.
     */
    private static String host(String referrer, HttpServletRequest request) {
        String limpo = termo(referrer);
        if (limpo.equals(UsageEvent.DESCONHECIDO)) {
            return UsageEvent.DESCONHECIDO;
        }
        if (limpo.startsWith("www.")) {
            limpo = limpo.substring(4);
        }
        String proprio = request.getServerName();
        if (proprio != null && limpo.equalsIgnoreCase(proprio.toLowerCase(Locale.ROOT))) {
            return UsageEvent.DESCONHECIDO;
        }
        return limpo;
    }

    /**
     * Higiene de todo texto que vem do cliente: minúsculas, sem espaço, alfabeto restrito e curto.
     *
     * <p>O alfabeto restrito não é paranoia com SQL — é o que impede que a dimensão ORIGEM do
     * painel vire campo de texto livre de quem quiser escrever nele.
     */
    private static String termo(String value) {
        if (value == null) {
            return UsageEvent.DESCONHECIDO;
        }
        String limpo = value.trim().toLowerCase(Locale.ROOT);
        if (limpo.isEmpty() || limpo.length() > 60) {
            return UsageEvent.DESCONHECIDO;
        }
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            boolean aceito =
                    (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9')
                            || c == '.'
                            || c == '-'
                            || c == '_';
            if (!aceito) {
                return UsageEvent.DESCONHECIDO;
            }
        }
        return limpo;
    }
}
