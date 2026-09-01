package dev.fos.service;

import dev.fos.model.AppStart;
import dev.fos.model.HttpStatHourly;
import dev.fos.repo.AppStartRepository;
import dev.fos.repo.HttpStatHourlyRepository;
import dev.fos.web.dto.AdminHealthDtos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A seção de saúde do painel (#86).
 *
 * <p>Mesma disciplina do {@link UsagePanelService}: ele lê o <b>agregado</b>, e só o agregado. Aqui
 * não existe nem tabela crua para ser lida — a medição já nasce somada por (hora, rota), e a
 * pergunta "quem fez esta requisição?" não tem onde ser respondida. Se um dia ela precisar de
 * resposta, o caminho é reabrir a D50 e reescrever {@code docs/11-privacidade.md}, não acrescentar
 * uma coluna.
 *
 * <p>Ao contrário do painel de uso, o período <b>inclui a hora corrente</b>. Lá o dia fechado é
 * regra porque um número que muda depois de lido engana a leitura de tendência; aqui a leitura é
 * operacional — o incidente que interessa é o de agora, e escondê-lo até a hora virar seria
 * publicar sempre a notícia de ontem.
 *
 * <p>O que este serviço <b>não</b> consegue dizer, e a tela precisa deixar claro: se o site esteve
 * fora do ar. Aplicação parada não escreve linha, e hora sem linha é igual a hora sem visita. Essa
 * pergunta é da verificação de fora.
 */
@Service
public class SiteHealthService {

    /** Os três recortes aceitos, em horas: um dia, três dias, uma semana. */
    private static final Set<Integer> PERIODOS = Set.of(24, 72, 168);

    /** Quantas rotas cada ranking mostra. */
    private static final int ROTAS = 10;

    /** Quantas subidas a lista traz. Mais que isto vira log, e log tem outro lugar. */
    private static final int SUBIDAS = 10;

    /**
     * Requisições mínimas para uma rota entrar no ranking de lentidão.
     *
     * <p>Sem o piso, a rota chamada duas vezes no período — uma delas fria, logo depois da subida —
     * seria eternamente "a mais lenta do app", e o ranking apontaria para o lugar errado toda vez.
     */
    private static final int MINIMO_PARA_RANKING = 10;

    private final HttpStatHourlyRepository stats;
    private final AppStartRepository starts;
    private final Clock clock;

    public SiteHealthService(
            HttpStatHourlyRepository stats, AppStartRepository starts, Clock clock) {
        this.stats = stats;
        this.starts = starts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminHealthDtos.HealthView saude(int horas) {
        if (!PERIODOS.contains(horas)) {
            throw new IllegalArgumentException("horas precisa ser 24, 72 ou 168");
        }

        Instant fim = Instant.now(clock).truncatedTo(ChronoUnit.HOURS);
        Instant inicio = fim.minus(Duration.ofHours(horas - 1L));

        List<HttpStatHourly> linhas =
                stats.findByHourStartGreaterThanEqualOrderByHourStartAsc(inicio);

        long requisicoes = 0;
        long erros5xx = 0;
        long erros4xx = 0;
        long[] histograma = new long[HttpStats.FAIXAS];
        for (HttpStatHourly linha : linhas) {
            requisicoes += linha.getRequests();
            erros5xx += linha.getServerErrors();
            erros4xx += linha.getClientErrors();
            long[] faixas = linha.histograma();
            for (int i = 0; i < faixas.length; i++) {
                histograma[i] += faixas[i];
            }
        }

        List<AdminHealthDtos.RouteHealth> porRota = porRota(linhas);
        return new AdminHealthDtos.HealthView(
                horas,
                inicio,
                fim,
                linhas.isEmpty() ? null : linhas.get(linhas.size() - 1).getHourStart(),
                requisicoes,
                erros5xx,
                erros4xx,
                // Sem requisição não há disponibilidade medida. 100 é a resposta menos enganosa:
                // zero afirmaria uma queda que ninguém observou — e a tela mostra o total ao lado.
                requisicoes == 0 ? 100.0 : umaCasa((requisicoes - erros5xx) * 100.0 / requisicoes),
                HttpStats.percentil(histograma, HttpStats.PERCENTIL),
                HttpStats.ESCADA[HttpStats.ESCADA.length - 1],
                serie(linhas, inicio, fim),
                comErro(porRota),
                maisLentas(porRota),
                starts.countByStartedAtGreaterThanEqual(inicio),
                subidas());
    }

    /**
     * Um ponto por hora, inclusive as horas sem linha nenhuma.
     *
     * <p>E vale repetir o que o zero de uma hora vazia quer dizer, porque são três coisas
     * diferentes: ninguém chamou, a descarga ainda não rodou, ou o app não estava de pé. Esta série
     * não distingue as três — a tela diz isso em texto, e a verificação de fora responde a
     * terceira.
     */
    private static List<AdminHealthDtos.HealthPoint> serie(
            List<HttpStatHourly> linhas, Instant inicio, Instant fim) {
        Map<Instant, long[]> porHora = new HashMap<>();
        for (HttpStatHourly linha : linhas) {
            long[] soma = porHora.computeIfAbsent(linha.getHourStart(), ignorado -> new long[3]);
            soma[0] += linha.getRequests();
            soma[1] += linha.getServerErrors();
            soma[2] += linha.getClientErrors();
        }
        List<AdminHealthDtos.HealthPoint> serie = new ArrayList<>();
        for (Instant hora = inicio; !hora.isAfter(fim); hora = hora.plus(Duration.ofHours(1))) {
            long[] soma = porHora.getOrDefault(hora, new long[3]);
            serie.add(new AdminHealthDtos.HealthPoint(hora, soma[0], soma[1], soma[2]));
        }
        return serie;
    }

    /** Cada rota somada no período, com o histograma dela virado percentil. */
    private static List<AdminHealthDtos.RouteHealth> porRota(List<HttpStatHourly> linhas) {
        Map<String, long[]> totais = new HashMap<>();
        Map<String, long[]> histogramas = new HashMap<>();
        for (HttpStatHourly linha : linhas) {
            long[] soma = totais.computeIfAbsent(linha.getPath(), ignorado -> new long[4]);
            soma[0] += linha.getRequests();
            soma[1] += linha.getServerErrors();
            soma[2] += linha.getTotalMs();
            soma[3] = Math.max(soma[3], linha.getMaxMs());
            long[] acumulado =
                    histogramas.computeIfAbsent(
                            linha.getPath(), ignorado -> new long[HttpStats.FAIXAS]);
            long[] faixas = linha.histograma();
            for (int i = 0; i < faixas.length; i++) {
                acumulado[i] += faixas[i];
            }
        }

        List<AdminHealthDtos.RouteHealth> rotas = new ArrayList<>(totais.size());
        totais.forEach(
                (path, soma) ->
                        rotas.add(
                                new AdminHealthDtos.RouteHealth(
                                        path,
                                        soma[0],
                                        soma[1],
                                        soma[0] == 0 ? 0 : umaCasa(soma[1] * 100.0 / soma[0]),
                                        HttpStats.percentil(
                                                histogramas.get(path), HttpStats.PERCENTIL),
                                        soma[0] == 0 ? 0 : Math.round((double) soma[2] / soma[0]),
                                        soma[3])));
        return rotas;
    }

    /**
     * As rotas que erraram, da que mais errou para a que menos errou.
     *
     * <p>Ordenado por <b>taxa</b> e desempatado por volume: a rota que errou 3 de 3 é a que
     * quebrou, e ela nunca apareceria num ranking por número absoluto ao lado de uma rota
     * movimentada com 1% de erro. Rota sem erro nenhum fica de fora — a lista existe para ser curta
     * e, no dia bom, vazia.
     */
    private static List<AdminHealthDtos.RouteHealth> comErro(
            List<AdminHealthDtos.RouteHealth> rotas) {
        return rotas.stream()
                .filter(rota -> rota.serverErrors() > 0)
                .sorted(
                        Comparator.comparingDouble(AdminHealthDtos.RouteHealth::errorPercent)
                                .reversed()
                                .thenComparing(
                                        Comparator.comparingLong(
                                                        AdminHealthDtos.RouteHealth::serverErrors)
                                                .reversed())
                                .thenComparing(AdminHealthDtos.RouteHealth::path))
                .limit(ROTAS)
                .toList();
    }

    /**
     * As mais lentas por p95, com piso de volume.
     *
     * <p>Empate desempatado pela média e depois pelo nome, e não deixado ao acaso do {@code
     * HashMap}: o p95 é o teto de uma faixa, então empate é o caso comum aqui — metade das rotas
     * cai na mesma faixa —, e uma ordem que muda sozinha entre dois carregamentos do mesmo dado
     * parece defeito para quem está lendo.
     */
    private static List<AdminHealthDtos.RouteHealth> maisLentas(
            List<AdminHealthDtos.RouteHealth> rotas) {
        return rotas.stream()
                .filter(rota -> rota.requests() >= MINIMO_PARA_RANKING)
                // A faixa de cima vem como 0 (`acima de 2500 ms`) e é a MAIS lenta de todas:
                // ordenar pelo número cru a mandaria para o fim da lista.
                .sorted(
                        Comparator.comparingLong(
                                        (AdminHealthDtos.RouteHealth rota) ->
                                                rota.p95Ms() == 0 ? Long.MAX_VALUE : rota.p95Ms())
                                .reversed()
                                .thenComparing(
                                        Comparator.comparingLong(AdminHealthDtos.RouteHealth::avgMs)
                                                .reversed())
                                .thenComparing(AdminHealthDtos.RouteHealth::path))
                .limit(ROTAS)
                .toList();
    }

    private List<AdminHealthDtos.StartView> subidas() {
        return starts.findByOrderByStartedAtDesc(PageRequest.of(0, SUBIDAS)).stream()
                .map(
                        (AppStart subida) ->
                                new AdminHealthDtos.StartView(
                                        subida.getStartedAt(), subida.getProfiles()))
                .toList();
    }

    /** Uma casa decimal: "99,9%" é informação, "99,93478%" é ruído com aparência de precisão. */
    private static double umaCasa(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
