package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.UsageDaily;
import dev.fos.model.UsageDimension;
import dev.fos.model.UsageEvent;
import dev.fos.model.UsageEventType;
import dev.fos.repo.UsageDailyRepository;
import dev.fos.repo.UsageEventRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * De linha crua a contagem por dia (#84, D50).
 *
 * <p>Existe por dois motivos que puxam para o mesmo lado: o painel (fatia 2) não pode varrer a
 * tabela grande a cada abertura, e a tabela grande é apagada aos 90 dias — sem o agregado, o
 * histórico morreria junto com ela. O agregado não guarda chave de visita nem id de conta, e é por
 * isso que ele pode ficar para sempre.
 *
 * <p>Agrupamento em Java, e não em SQL: são cinco recortes sobre o mesmo dia, o volume de um dia
 * cabe folgado em memória nesta escala, e a alternativa seriam cinco {@code GROUP BY} com {@code
 * COUNT(DISTINCT)} que ninguém consegue testar com dado plantado. Se um dia o dia não couber em
 * memória, é aqui que se mexe — e aí o teste já existe para dizer se o SQL novo conta o mesmo.
 */
@Service
public class UsageAggregator {

    private static final Logger log = LoggerFactory.getLogger(UsageAggregator.class);

    private final UsageEventRepository events;
    private final UsageDailyRepository daily;
    private final FosProperties.Usage config;
    private final Clock clock;

    public UsageAggregator(
            UsageEventRepository events,
            UsageDailyRepository daily,
            FosProperties properties,
            Clock clock) {
        this.events = events;
        this.daily = daily;
        this.config = properties.usage();
        this.clock = clock;
    }

    /**
     * Agrega todo dia fechado que ainda não tem contagem, e devolve quantos dias foram fechados.
     *
     * <p>Só dias <b>anteriores</b> a hoje: o dia corrente ainda recebe evento, e fechá-lo daria um
     * número que muda depois de publicado. E só os que ainda não têm contagem, para que rodar o job
     * duas vezes no mesmo dia não refaça 90 dias de trabalho.
     */
    @Transactional
    public int aggregatePending() {
        LocalDate hoje = LocalDate.now(clock);
        List<LocalDate> pendentes = events.diasCrusAntesDe(hoje);
        int fechados = 0;
        for (LocalDate dia : pendentes) {
            if (!daily.existsByOccurredOn(dia)) {
                aggregate(dia);
                fechados++;
            }
        }
        return fechados;
    }

    /**
     * Refaz a contagem de um dia do zero.
     *
     * <p>Apaga antes de escrever para ser idempotente: reagregar um dia por causa de importação
     * corrigida ou de um bug do próprio agregador não pode somar em cima do que já estava lá.
     */
    @Transactional
    public void aggregate(LocalDate dia) {
        List<UsageEvent> doDia = events.findByOccurredOn(dia);
        daily.deleteByOccurredOn(dia);
        if (doDia.isEmpty()) {
            return;
        }

        List<UsageEvent> paginas =
                doDia.stream().filter(e -> e.getEventType() == UsageEventType.PAGINA).toList();

        List<UsageDaily> linhas = new ArrayList<>();
        // Caminho e origem contam só acesso de tela: evento de funil não acontece "em uma rota", e
        // a origem dele seria sempre "direto" — o que afogaria a única resposta que ORIGEM tem de
        // dar, que é de qual link a pessoa veio.
        linhas.addAll(contar(dia, UsageDimension.CAMINHO, paginas, UsageEvent::getPath));
        linhas.addAll(contar(dia, UsageDimension.ORIGEM, paginas, UsageEvent::origin));
        linhas.addAll(contar(dia, UsageDimension.PAIS, doDia, UsageEvent::getCountry));
        linhas.addAll(contar(dia, UsageDimension.DISPOSITIVO, doDia, e -> e.getDevice().name()));
        linhas.addAll(contar(dia, UsageDimension.EVENTO, doDia, e -> e.getEventType().name()));
        daily.saveAll(linhas);
    }

    /**
     * O expurgo dos 90 dias.
     *
     * <p>Só o cru. O agregado não tem chave de visita nem id de conta — não há o que expirar nele,
     * e apagá-lo destruiria o histórico que a retenção curta existe para preservar.
     */
    @Transactional
    public int purge() {
        LocalDate limite = LocalDate.now(clock).minusDays(config.retentionDays());
        int apagados = events.deleteByOccurredOnBefore(limite);
        if (apagados > 0) {
            log.info("Expurgo de uso: {} eventos crus anteriores a {} apagados", apagados, limite);
        }
        return apagados;
    }

    /**
     * Uma linha por valor distinto, com quantos eventos e quantas visitas distintas o produziram.
     */
    private static List<UsageDaily> contar(
            LocalDate dia,
            UsageDimension dimensao,
            List<UsageEvent> eventos,
            Function<UsageEvent, String> valor) {
        Map<String, long[]> totais = new HashMap<>();
        Map<String, Set<String>> visitas = new HashMap<>();
        for (UsageEvent evento : eventos) {
            String chave = valor.apply(evento);
            totais.computeIfAbsent(chave, ignorado -> new long[1])[0]++;
            visitas.computeIfAbsent(chave, ignorado -> new HashSet<>()).add(evento.getVisitKey());
        }
        List<UsageDaily> linhas = new ArrayList<>(totais.size());
        totais.forEach(
                (chave, total) ->
                        linhas.add(
                                new UsageDaily(
                                        dia,
                                        dimensao,
                                        chave,
                                        total[0],
                                        visitas.get(chave).size())));
        return linhas;
    }
}
