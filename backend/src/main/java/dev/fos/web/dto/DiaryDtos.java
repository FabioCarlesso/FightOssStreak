package dev.fos.web.dto;

import dev.fos.model.Feeling;
import dev.fos.model.Recall;
import dev.fos.model.SessionKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Diário de treino (#114, D56/D57/D58): a sessão é a entrada, o currículo é a saída.
 *
 * <p>Só a data é obrigatória, e isso é desenho de produto e não frouxidão: seis campos no vestiário
 * é o jeito de garantir que ninguém preencha. Sessão incompleta é sessão válida, e não há estado de
 * rascunho — "treinei" agora, completa depois.
 */
public final class DiaryDtos {

    private DiaryDtos() {}

    /**
     * Teto do campo de peso.
     *
     * <p>Existe para barrar dígito trocado, e não para dizer o que é peso saudável: o app guarda e
     * mostra, nunca interpreta (D57). {@code NUMERIC(5,2)} no banco não guardaria mais que isso de
     * qualquer forma.
     *
     * <p>Sem casa decimal no literal, e isso é sobre o CI e não sobre peso: o springdoc emite
     * {@code 300.0} para {@code "300.0"}, e o {@code JSON.stringify} do {@code gen:types} devolve
     * {@code 300} ao reescrever o {@code openapi.json}. O passo que diffa o spec versionado contra
     * a aplicação no ar quebraria por causa de um ponto-zero.
     */
    private static final String PESO_MINIMO = "20";

    private static final String PESO_MAXIMO = "300";

    /**
     * A sessão como a tela a escreve.
     *
     * @param trainedOn o único campo obrigatório; data futura é recusada, como no registro de drill
     * @param kind ausente = {@code AULA}, o caso dominante e o que a tela já vem marcando. Nunca é
     *     nulo no banco: {@code DESCANSO} precisa ser uma escolha explícita, porque é ele que fica
     *     fora do streak (D58)
     * @param weightKg opcional e <b>por sessão</b>, nunca diário (D57)
     * @param tecnicas técnicas do currículo treinadas nesta sessão; cada uma vira um {@code
     *     drill_log} pelo mesmo caminho de código do registro pela tela do nó
     */
    public record SessionRequest(
            @NotNull LocalDate trainedOn,
            SessionKind kind,
            @Min(1) @Max(1440) Integer durationMinutes,
            Feeling feeling,
            @DecimalMin(PESO_MINIMO) @DecimalMax(PESO_MAXIMO) BigDecimal weightKg,
            @Size(max = 2000) String learned,
            @Size(max = 2000) String improve,
            @Valid List<TechniqueRequest> tecnicas) {}

    /**
     * Edição da sessão.
     *
     * <p>{@code PATCH} pelo verbo, substituição pelos campos <b>da sessão</b>: as técnicas
     * vinculadas não são tocadas aqui — elas têm rota própria —, e é isso que faz a operação ser
     * parcial em relação ao recurso. Já entre os campos da sessão o corpo vale como estado novo, e
     * campo ausente volta a vazio: o formulário manda a tela inteira, e peso apagado precisa poder
     * ser apagado.
     */
    public record SessionPatch(
            @NotNull LocalDate trainedOn,
            SessionKind kind,
            @Min(1) @Max(1440) Integer durationMinutes,
            Feeling feeling,
            @DecimalMin(PESO_MINIMO) @DecimalMax(PESO_MAXIMO) BigDecimal weightKg,
            @Size(max = 2000) String learned,
            @Size(max = 2000) String improve) {}

    /**
     * Uma técnica do currículo dentro da sessão.
     *
     * <p>Mesmos campos do {@code ActivityDtos.DrillRequest} menos a data — quem manda a data é a
     * sessão. A auto-avaliação continua obrigatória porque é ela que alimenta o SM-2: vincular sem
     * dizer como saiu agendaria tudo no mesmo ritmo.
     */
    public record TechniqueRequest(
            @NotNull String nodeCode, @NotNull Recall recall, @Size(max = 1000) String note) {}

    /**
     * A sessão como a tela a lê.
     *
     * @param countsAsTrainingDay o que a D58 decide: {@code DESCANSO} fica no diário e não conta
     *     como dia de treino. Vem resolvido do backend para a tela não reimplementar a regra
     */
    public record SessionView(
            Long id,
            LocalDate trainedOn,
            SessionKind kind,
            boolean countsAsTrainingDay,
            Integer durationMinutes,
            Feeling feeling,
            BigDecimal weightKg,
            String learned,
            String improve,
            List<TechniqueView> tecnicas) {}

    /**
     * @param nodeTitle título do nó, para a linha do tempo não precisar carregar a árvore
     */
    public record TechniqueView(
            String nodeCode, String nodeTitle, Recall recall, String note, LocalDate drilledOn) {}

    /**
     * Um dia do diário: as sessões daquele dia e os drills que não pertencem a nenhuma.
     *
     * <p>Os avulsos existem porque o {@code session_id} é anulável (D56a): drill registrado pela
     * tela do nó — inclusive todos os anteriores a esta feature — aparece aqui sem que nenhuma
     * linha tenha sido migrada.
     */
    public record DiaryDay(
            LocalDate day,
            boolean countsAsTrainingDay,
            List<SessionView> sessions,
            List<TechniqueView> avulsos) {}

    /**
     * A linha do tempo do período pedido.
     *
     * @param sessionsInMonth treinos no mês de {@code to} — número <b>descritivo, sem meta</b>
     *     (docs/05). Streak e revisões atendidas continuam sendo o que se persegue. {@code
     *     DESCANSO} fica de fora: é a mesma regra da D58, e contá-lo diria "treino" na tela em que
     *     o dia aparece marcado como não contando no streak
     */
    public record DiaryTimeline(
            LocalDate from, LocalDate to, int sessionsInMonth, List<DiaryDay> days) {}
}
