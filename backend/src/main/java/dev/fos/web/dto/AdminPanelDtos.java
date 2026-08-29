package dev.fos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * O painel do administrador (#85, D50).
 *
 * <p>Leia a lista de campos procurando o que <b>não</b> está aqui: não há e-mail, não há nome, não
 * há {@code user_id}, não há linha que descreva uma pessoa. Tudo é contagem. Essa ausência é o que
 * mantém verdadeiro o desenho de privacidade da coleta (D50) — um painel que listasse pessoas
 * tornaria inútil todo o cuidado de não guardar IP, e a promessa escrita em {@code
 * docs/11-privacidade.md} teria que ser reescrita de novo.
 *
 * <p>Tudo vem de {@code usage_daily}, com duas exceções que <b>não</b> são a tabela crua de
 * eventos: os totais de contas, que saem de {@code app_user}, e "contas ativas", que sai da
 * contagem de {@code drill_log}. Nenhuma consulta do painel toca {@code usage_event}.
 */
public final class AdminPanelDtos {

    private AdminPanelDtos() {}

    /**
     * @param days o preset pedido: 7, 30 ou 90
     * @param from primeiro dia do período. É sempre um dia <b>fechado</b> — hoje ainda recebe
     *     evento, e publicá-lo daria um número que muda depois de lido
     * @param to último dia do período, isto é, ontem
     * @param previousFrom primeiro dia do período anterior, de mesmo tamanho
     * @param previousTo último dia do período anterior
     * @param aggregatedThrough dia mais recente que já tem contagem, ou {@code null} se a agregação
     *     nunca rodou. É o que separa "ninguém acessou" de "o job ainda não fechou o dia": as duas
     *     coisas dão zero, e só esta linha distingue
     * @param geoIpCredit crédito da base de geolocalização, exigido pela licença CC BY 4.0 (D50).
     *     Vazio quando o ambiente não tem base — o caso de dev e do CI
     */
    @Schema(description = "Acessos, funil, origem e perfil de uso — agregado, e de ninguém.")
    public record PanelView(
            int days,
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo,
            LocalDate aggregatedThrough,
            AccessSeries access,
            List<FunnelStep> funnel,
            List<Slice> origins,
            Profile profile,
            List<Slice> content,
            AccountTotals accounts,
            String geoIpCredit) {}

    /**
     * A série de acessos e o comparativo com o período anterior.
     *
     * @param series um ponto por dia do período, <b>inclusive os dias sem acesso</b>: buraco na
     *     série viraria linha reta ligando duas datas distantes, que é o gráfico mentindo
     * @param visits acessos de tela somados no período
     * @param visitors visitantes somados no período. Soma de contagens diárias, e não pessoas
     *     distintas no período: o sal da chave de visita roda por dia (D50), e ligar a mesma pessoa
     *     entre dois dias é justamente o que a coleta não faz
     */
    public record AccessSeries(
            List<AccessPoint> series,
            long visits,
            long visitors,
            long previousVisits,
            long previousVisitors) {}

    public record AccessPoint(LocalDate day, long visits, long visitors) {}

    /**
     * Um degrau do funil.
     *
     * <p>Os seis degraus vêm <b>sempre</b>, na ordem, mesmo zerados: degrau que some esconde
     * exatamente onde as pessoas desistem, que é a pergunta que o funil existe para responder.
     *
     * <p>O primeiro degrau conta <b>visitantes</b>; os outros contam <b>ocorrências</b>. Não é
     * inconsistência: comparar "quantas pessoas chegaram" com "quantas páginas foram abertas" daria
     * uma conversão sem significado, já que quem chega abre várias telas.
     *
     * @param step nome do degrau, estável para o cliente
     * @param label como o degrau se chama na tela
     * @param percentOfPrevious conversão em relação ao degrau anterior, arredondada. {@code null}
     *     no primeiro degrau e sempre que o anterior for zero — não há divisão a fazer, e mostrar
     *     0% ali seria afirmar uma queda que ninguém mediu
     */
    public record FunnelStep(String step, String label, long total, Integer percentOfPrevious) {}

    /**
     * Uma fatia de uma dimensão: de onde veio, com o quê, em que tela.
     *
     * @param value o valor da dimensão. {@code direto}, {@code desconhecido} e {@code ZZ} são
     *     categorias como as outras, não falhas
     * @param total quantos eventos
     * @param visitors quantas visitas distintas os produziram
     */
    public record Slice(String value, long total, long visitors) {}

    /** Com o que as pessoas chegam. Cada lista já vem ordenada, com o resto somado em "outros". */
    public record Profile(
            List<Slice> devices,
            List<Slice> browsers,
            List<Slice> languages,
            List<Slice> countries) {}

    /**
     * As contas, em número.
     *
     * <p>Três números e nenhuma lista, de propósito: a tela nominal das contas é outra (#89/#91), e
     * ela tem confirmação por ação e registro de quem decidiu. Este painel responde "quantos", e
     * misturar as duas coisas seria transformar uma tela de leitura em uma tela de poder.
     *
     * @param activeInPeriod contas que registraram drill no período — usar o app, e não só abri-lo
     */
    public record AccountTotals(long total, long createdInPeriod, long activeInPeriod) {}
}
