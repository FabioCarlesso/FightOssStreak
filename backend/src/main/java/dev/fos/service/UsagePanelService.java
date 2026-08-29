package dev.fos.service;

import dev.fos.model.UsageDaily;
import dev.fos.model.UsageDimension;
import dev.fos.model.UsageEvent;
import dev.fos.model.UsageEventType;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.UsageDailyRepository;
import dev.fos.web.dto.AdminPanelDtos;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O painel do administrador (#85, D50).
 *
 * <p>Uma regra manda em todo este arquivo: <b>ele lê o agregado, e só o agregado</b>. {@code
 * usage_event} tem chave de visita e às vezes {@code user_id}; {@code usage_daily} não tem nada que
 * aponte para ninguém. Ler o cru daria respostas mais finas — funil por pessoa, sessão, retorno
 * entre dias — e é exatamente por isso que não se lê: cada uma dessas respostas é a D50 sendo
 * revertida em silêncio. Se um dia o painel precisar de uma delas, o caminho é reabrir a decisão e
 * reescrever {@code docs/11-privacidade.md}, não acrescentar uma consulta aqui.
 *
 * <p>Duas leituras não vêm do agregado, e nenhuma das duas é o cru: os totais de contas saem de
 * {@code app_user} e "contas ativas" sai da contagem de dias com drill. As duas devolvem
 * <em>número</em>, nunca linha.
 *
 * <p>O período é sempre de dias <b>fechados</b> — termina ontem. Hoje ainda recebe evento, e o
 * agregador não fecha o dia corrente de propósito (ver {@link UsageAggregator}): publicar um número
 * que muda depois de lido é pior que não publicá-lo.
 */
@Service
public class UsagePanelService {

    /** Os três recortes que o painel aceita. Período livre está fora de escopo por decisão. */
    private static final Set<Integer> PERIODOS = Set.of(7, 30, 90);

    /**
     * Quantas fatias uma dimensão mostra antes de o resto virar "outros".
     *
     * <p>A cauda é somada, e não descartada: sem a linha de "outros" os percentuais não fecham em
     * 100 e quem lê não tem como saber o quanto ficou de fora.
     */
    private static final int FATIAS = 8;

    /** O rótulo da cauda somada. Não colide com valor de dimensão: nenhum deles tem espaço. */
    private static final String OUTROS = "outros";

    /**
     * Os degraus, na ordem, com o nome de cada um na tela.
     *
     * <p>É a lista inteira que vira resposta, e não só os degraus que tiveram evento: um funil que
     * omite o degrau zerado esconde justamente onde as pessoas param.
     */
    private static final List<Degrau> DEGRAUS =
            List.of(
                    new Degrau("VISITA", "Visitaram"),
                    new Degrau(UsageEventType.DEMONSTRACAO_ABERTA.name(), "Abriram a demonstração"),
                    new Degrau(UsageEventType.CADASTRO_CRIADO.name(), "Criaram conta"),
                    new Degrau(UsageEventType.EMAIL_VERIFICADO.name(), "Confirmaram o e-mail"),
                    new Degrau(
                            UsageEventType.PRIMEIRO_DRILL.name(), "Registraram o primeiro drill"),
                    new Degrau(UsageEventType.RETORNO_EM_7_DIAS.name(), "Voltaram em 7 dias"));

    private record Degrau(String nome, String rotulo) {}

    /**
     * Crédito da base de geolocalização, exigido pela licença CC BY 4.0 (D50).
     *
     * <p>Vai na resposta, e não só no README, porque é <b>nesta tela</b> que o dado derivado dela
     * aparece — e some quando não há base carregada, que é o caso de dev e do CI: creditar uma base
     * que não foi usada seria crédito errado.
     */
    private static final String CREDITO_GEOIP = "Dados de país por IP: DB-IP Lite (CC BY 4.0)";

    private final UsageDailyRepository daily;
    private final AppUserRepository users;
    private final DrillLogRepository drills;
    private final GeoIpDatabase geoIp;
    private final Clock clock;

    public UsagePanelService(
            UsageDailyRepository daily,
            AppUserRepository users,
            DrillLogRepository drills,
            GeoIpDatabase geoIp,
            Clock clock) {
        this.daily = daily;
        this.users = users;
        this.drills = drills;
        this.geoIp = geoIp;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminPanelDtos.PanelView painel(int dias) {
        if (!PERIODOS.contains(dias)) {
            throw new IllegalArgumentException("dias precisa ser 7, 30 ou 90");
        }

        LocalDate fim = LocalDate.now(clock).minusDays(1);
        LocalDate inicio = fim.minusDays(dias - 1L);
        LocalDate fimAnterior = inicio.minusDays(1);
        LocalDate inicioAnterior = fimAnterior.minusDays(dias - 1L);

        // Uma consulta para os dois períodos: o comparativo precisa dos dois de qualquer forma.
        List<UsageDaily> tudo = daily.findByOccurredOnBetween(inicioAnterior, fim);
        List<UsageDaily> periodo =
                tudo.stream().filter(linha -> !linha.getOccurredOn().isBefore(inicio)).toList();
        List<UsageDaily> anterior =
                tudo.stream().filter(linha -> linha.getOccurredOn().isBefore(inicio)).toList();

        Map<UsageDimension, List<UsageDaily>> porDimensao = agrupar(periodo);

        return new AdminPanelDtos.PanelView(
                dias,
                inicio,
                fim,
                inicioAnterior,
                fimAnterior,
                daily.ultimoDiaAgregado(),
                acessos(periodo, anterior, inicio, fim),
                funil(porDimensao.getOrDefault(UsageDimension.EVENTO, List.of())),
                fatias(porDimensao.get(UsageDimension.ORIGEM)),
                new AdminPanelDtos.Profile(
                        fatias(porDimensao.get(UsageDimension.DISPOSITIVO)),
                        fatias(porDimensao.get(UsageDimension.NAVEGADOR)),
                        fatias(porDimensao.get(UsageDimension.IDIOMA)),
                        fatias(porDimensao.get(UsageDimension.PAIS))),
                fatias(porDimensao.get(UsageDimension.CAMINHO)),
                contas(inicio, fim),
                geoIp.isEmpty() ? "" : CREDITO_GEOIP);
    }

    private static Map<UsageDimension, List<UsageDaily>> agrupar(List<UsageDaily> linhas) {
        Map<UsageDimension, List<UsageDaily>> mapa = new EnumMap<>(UsageDimension.class);
        for (UsageDaily linha : linhas) {
            mapa.computeIfAbsent(linha.getDimension(), ignorado -> new ArrayList<>()).add(linha);
        }
        return mapa;
    }

    /**
     * A série diária, com um ponto por dia — inclusive os dias sem acesso nenhum.
     *
     * <p>Dia faltando não é o mesmo que dia zerado: a linha do gráfico ligaria duas datas distantes
     * como se nada tivesse acontecido no meio, e o que aconteceu no meio foi ninguém aparecer.
     */
    private static AdminPanelDtos.AccessSeries acessos(
            List<UsageDaily> periodo, List<UsageDaily> anterior, LocalDate inicio, LocalDate fim) {
        Map<LocalDate, long[]> porDia = new HashMap<>();
        for (UsageDaily linha : paginas(periodo)) {
            long[] soma = porDia.computeIfAbsent(linha.getOccurredOn(), ignorado -> new long[2]);
            soma[0] += linha.getEvents();
            soma[1] += linha.getVisits();
        }

        List<AdminPanelDtos.AccessPoint> serie = new ArrayList<>();
        long visitas = 0;
        long visitantes = 0;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            long[] soma = porDia.getOrDefault(dia, new long[2]);
            serie.add(new AdminPanelDtos.AccessPoint(dia, soma[0], soma[1]));
            visitas += soma[0];
            visitantes += soma[1];
        }

        long visitasAntes = paginas(anterior).stream().mapToLong(UsageDaily::getEvents).sum();
        long visitantesAntes = paginas(anterior).stream().mapToLong(UsageDaily::getVisits).sum();
        return new AdminPanelDtos.AccessSeries(
                serie, visitas, visitantes, visitasAntes, visitantesAntes);
    }

    /**
     * As linhas de acesso de tela.
     *
     * <p>Pela dimensão {@code EVENTO} e o valor {@code PAGINA}, e não pela soma da dimensão {@code
     * CAMINHO}: as duas dariam o mesmo total de eventos, mas os visitantes não — a mesma visita que
     * abre três telas conta uma vez em {@code EVENTO} e três em {@code CAMINHO}.
     */
    private static List<UsageDaily> paginas(List<UsageDaily> linhas) {
        return linhas.stream()
                .filter(linha -> linha.getDimension() == UsageDimension.EVENTO)
                .filter(linha -> UsageEventType.PAGINA.name().equals(linha.getValue()))
                .toList();
    }

    private static List<AdminPanelDtos.FunnelStep> funil(List<UsageDaily> eventos) {
        Map<String, long[]> porTipo = new HashMap<>();
        for (UsageDaily linha : eventos) {
            long[] soma = porTipo.computeIfAbsent(linha.getValue(), ignorado -> new long[2]);
            soma[0] += linha.getEvents();
            soma[1] += linha.getVisits();
        }

        List<AdminPanelDtos.FunnelStep> degraus = new ArrayList<>(DEGRAUS.size());
        long anterior = 0;
        for (int i = 0; i < DEGRAUS.size(); i++) {
            Degrau degrau = DEGRAUS.get(i);
            long[] soma = porTipo.getOrDefault(chaveDoDegrau(degrau), new long[2]);
            // O primeiro degrau conta visitantes; os outros contam ocorrências. Comparar "quantas
            // pessoas chegaram" com "quantas telas foram abertas" daria conversão sem significado.
            long total = i == 0 ? soma[1] : soma[0];
            Integer conversao =
                    i == 0 || anterior == 0 ? null : Math.round(total * 100f / anterior);
            degraus.add(
                    new AdminPanelDtos.FunnelStep(
                            degrau.nome(), degrau.rotulo(), total, conversao));
            anterior = total;
        }
        return degraus;
    }

    /** O primeiro degrau é o acesso de tela; os outros já se chamam como o evento. */
    private static String chaveDoDegrau(Degrau degrau) {
        return "VISITA".equals(degrau.nome()) ? UsageEventType.PAGINA.name() : degrau.nome();
    }

    /**
     * Uma dimensão virada em ranking, com a cauda somada em "outros".
     *
     * <p>Empate desempatado pelo valor, e não deixado ao acaso do {@code HashMap}: o painel
     * recarrega a cada troca de período, e uma ordem que muda sozinha entre dois carregamentos do
     * mesmo dado parece bug para quem está lendo.
     */
    private static List<AdminPanelDtos.Slice> fatias(List<UsageDaily> linhas) {
        if (linhas == null || linhas.isEmpty()) {
            return List.of();
        }
        Map<String, long[]> porValor = new HashMap<>();
        for (UsageDaily linha : linhas) {
            long[] soma = porValor.computeIfAbsent(linha.getValue(), ignorado -> new long[2]);
            soma[0] += linha.getEvents();
            soma[1] += linha.getVisits();
        }

        List<AdminPanelDtos.Slice> ordenadas =
                porValor.entrySet().stream()
                        .map(
                                entrada ->
                                        new AdminPanelDtos.Slice(
                                                rotulo(entrada.getKey()),
                                                entrada.getValue()[0],
                                                entrada.getValue()[1]))
                        .sorted(
                                Comparator.comparingLong(AdminPanelDtos.Slice::total)
                                        .reversed()
                                        .thenComparing(AdminPanelDtos.Slice::value))
                        .toList();
        if (ordenadas.size() <= FATIAS) {
            return ordenadas;
        }

        List<AdminPanelDtos.Slice> topo = new ArrayList<>(ordenadas.subList(0, FATIAS));
        long total = 0;
        long visitantes = 0;
        for (AdminPanelDtos.Slice fatia : ordenadas.subList(FATIAS, ordenadas.size())) {
            total += fatia.total();
            visitantes += fatia.visitors();
        }
        topo.add(new AdminPanelDtos.Slice(OUTROS, total, visitantes));
        return topo;
    }

    /**
     * País sem base de geolocalização é categoria, não erro — e a tela precisa poder dizer isso.
     */
    private static String rotulo(String valor) {
        return UsageEvent.PAIS_DESCONHECIDO.equals(valor) ? "desconhecido" : valor;
    }

    private AdminPanelDtos.AccountTotals contas(LocalDate inicio, LocalDate fim) {
        Instant de = inicio.atStartOfDay(clock.getZone()).toInstant();
        Instant ate = fim.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return new AdminPanelDtos.AccountTotals(
                users.countByDemoExpiresAtIsNull(),
                users.countByDemoExpiresAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        de, ate),
                drills.countDistinctUsersBetween(inicio, fim));
    }
}
