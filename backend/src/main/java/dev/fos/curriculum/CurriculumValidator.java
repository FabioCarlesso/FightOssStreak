package dev.fos.curriculum;

import dev.fos.model.Belt;
import dev.fos.model.PositionFamily;
import dev.fos.model.PositionPerspective;
import dev.fos.model.UnlockRule;
import dev.fos.model.VideoOrientation;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valida a integridade do currículo <em>antes</em> de ele chegar ao banco.
 *
 * <p>Detecção de ciclo acontece aqui, na ingestão, e nunca em runtime (docs/arquitetura.md): um
 * ciclo tornaria um conjunto de nós permanentemente inalcançável, e o lugar barato de descobrir
 * isso é no build.
 */
@Component
public class CurriculumValidator {

    /**
     * Id de vídeo do YouTube: 11 caracteres de [A-Za-z0-9_-].
     *
     * <p>Público para que os testes afirmem a <em>mesma</em> regra que a ingestão aplica, em vez de
     * uma cópia que pode divergir. {@code scripts/catalogar-video.mjs} carrega a única outra cópia,
     * inevitável por ser JavaScript.
     */
    public static final String YOUTUBE_ID_REGEX = "[\\w-]{11}";

    private static final Pattern YOUTUBE_ID = Pattern.compile(YOUTUBE_ID_REGEX);

    /**
     * Teto de complementares por nó.
     *
     * <p>Não é limite técnico — é o que impede a faixa de complementares de virar catálogo, que é o
     * risco central de D1: quanto mais vídeo por tela, menos a tela é ferramenta de revisão. Regra
     * que mora só em documento é regra que se esquece na hora de catalogar o quinto clipe, então
     * ela mora aqui.
     */
    public static final int MAX_EXTRA_VIDEOS = 4;

    /**
     * Teto de parágrafos do {@code concept}. Linha em branco vira parágrafo na tela do nó
     * (`NodePage.tsx`); mais que isso é o passo a passo voltando pela porta dos fundos (D1, issue
     * #58).
     */
    public static final int MAX_CONCEPT_PARAGRAPHS = 3;

    /**
     * Faixa de tamanho do {@code concept}, em caracteres. O piso é o que M0/M1 já entregam com o
     * padrão de três movimentos (problema, mecanismo, erro comum); o teto é o que ainda cabe numa
     * tela de celular sem rolagem longa.
     *
     * <p>A checagem só roda para os módulos em {@link #CONCEPT_LENGTH_CURATED_MODULES} — ver o
     * javadoc de lá para o porquê de não valer para o currículo inteiro ainda (issue #58, D41 em
     * {@code docs/decisoes/README.md}).
     */
    public static final int MIN_CONCEPT_LENGTH = 450;

    public static final int MAX_CONCEPT_LENGTH = 900;

    /**
     * Módulos cujo {@code concept} já foi reescrito no padrão de três movimentos e cabe na faixa de
     * {@link #MIN_CONCEPT_LENGTH}–{@link #MAX_CONCEPT_LENGTH}. Os 9 módulos (46 nós) foram
     * reescritos juntos (issue #58, D42 em {@code docs/decisoes/README.md}), então o conjunto cobre
     * o currículo inteiro — mas o desenho por módulo continua aqui, e não uma constante booleana,
     * porque um nó novo em módulo futuro (ex.: um M9) nasceria fora da faixa por padrão, e é assim
     * que deve ser até ser escrito no padrão e o módulo entrar no conjunto.
     */
    private static final Set<String> CONCEPT_LENGTH_CURATED_MODULES =
            Set.of("M0", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8");

    /**
     * Mínimo de perguntas no banco de um nó que tem quiz (issue #59, D44).
     *
     * <p>Duas tentativas sem repetir — {@code QuizRotation.QUESTIONS_PER_ATTEMPT} vezes dois — é o
     * menor banco em que a rotação já entrega alguma coisa nova na segunda vez que o nó é revisado.
     * Nó com {@code quiz: []} continua aceito (D15): o mínimo só vale para quem já tem alguma
     * pergunta escrita.
     */
    public static final int MIN_QUIZ_QUESTIONS = 8;

    /**
     * Faixa de tamanho do {@code identify} de uma posição (#194, D76). O piso barra o rótulo que
     * não descreve nada ("posição de guarda"); o teto é o de uma ou duas frases — mais que isso é o
     * texto deixando de dizer <i>o que se vê</i> para começar a dizer <i>o que se faz</i>, e isso é
     * o que a D1 proíbe.
     */
    public static final int MIN_IDENTIFY_LENGTH = 60;

    public static final int MAX_IDENTIFY_LENGTH = 280;

    /** Teto de um apelido, de posição ou de nó: é o nome que se ouve no tatame, não uma frase. */
    public static final int MAX_ALIAS_LENGTH = 60;

    private static final Pattern POSITION_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");

    /** Executa as checagens de um currículo sem mapa de posições. */
    public void validate(List<CurriculumSource.Module> modules) {
        validate(modules, List.of());
    }

    /** Executa todas as checagens e lança na primeira violação encontrada. */
    public void validate(
            List<CurriculumSource.Module> modules, List<CurriculumSource.PositionSpec> positions) {
        Map<String, CurriculumSource.NodeSpec> byCode = indexNodes(modules);
        Set<String> positionCodes = validatePositions(positions);
        validateFields(modules, byCode);
        validateNodeTaxonomy(byCode, positionCodes);
        validatePrereqReferences(byCode);
        validateNoCycles(byCode);
    }

    /**
     * O mapa de posições (#194, D76): código único, família e perspectiva válidas, {@code identify}
     * na faixa e sem quebra de linha, e nenhum apelido que leve a duas posições.
     *
     * <p>Apelido repetido é comparado sem acento e sem caixa, e contra o nome das outras posições
     * também: "Lateral" como apelido de duas posições, ou como apelido de uma e nome de outra,
     * faria a busca do diário devolver as duas para quem digitou uma.
     *
     * @return os códigos válidos, para conferir as referências dos nós
     */
    private Set<String> validatePositions(List<CurriculumSource.PositionSpec> positions) {
        Set<String> codes = new HashSet<>();
        Map<String, String> termOwner = new HashMap<>();

        for (CurriculumSource.PositionSpec position : positions) {
            String code = position.code();
            if (code == null || !POSITION_CODE.matcher(code).matches()) {
                throw new CurriculumException("Posição com código inválido: '" + code + "'");
            }
            if (!codes.add(code)) {
                throw new CurriculumException("Código de posição duplicado: " + code);
            }
            String where = "posição " + code;
            requireText(position.name(), where + ": nome vazio");

            try {
                PositionFamily.valueOf(position.family());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new CurriculumException(
                        where + ": família inválida '" + position.family() + "'");
            }
            try {
                PositionPerspective.valueOf(position.perspective());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new CurriculumException(
                        where + ": perspectiva inválida '" + position.perspective() + "'");
            }

            requireText(position.identify(), where + ": identify vazio");
            String identify = position.identify().strip();
            if (identify.length() < MIN_IDENTIFY_LENGTH
                    || identify.length() > MAX_IDENTIFY_LENGTH) {
                throw new CurriculumException(
                        where
                                + ": identify com "
                                + identify.length()
                                + " caracteres, fora da faixa de "
                                + MIN_IDENTIFY_LENGTH
                                + " a "
                                + MAX_IDENTIFY_LENGTH);
            }
            if (identify.contains("\n")) {
                throw new CurriculumException(where + ": identify é um parágrafo só");
            }

            claimTerm(termOwner, position.name(), code, where + ": nome");
            for (String alias : position.aliases()) {
                validateAlias(alias, where);
                claimTerm(termOwner, alias, code, where + ": apelido '" + alias + "'");
            }
        }
        return codes;
    }

    /**
     * Um termo de busca (nome ou apelido) aponta para uma posição só. O mesmo termo repetido
     * <i>dentro</i> da mesma posição também é erro: é digitação duplicada no JSON.
     */
    private void claimTerm(Map<String, String> owner, String term, String code, String where) {
        String previous = owner.putIfAbsent(searchKey(term), code);
        if (previous != null) {
            throw new CurriculumException(
                    where + " repete um termo de busca da posição " + previous);
        }
    }

    /** Posições e apelidos de cada nó (#194, D76). */
    private void validateNodeTaxonomy(
            Map<String, CurriculumSource.NodeSpec> byCode, Set<String> positionCodes) {
        for (CurriculumSource.NodeSpec node : byCode.values()) {
            String where = "nó " + node.code();
            Set<String> seen = new HashSet<>();
            for (String position : node.positions()) {
                if (!positionCodes.contains(position)) {
                    throw new CurriculumException(
                            where + ": posição inexistente '" + position + "'");
                }
                if (!seen.add(position)) {
                    throw new CurriculumException(where + ": posição repetida '" + position + "'");
                }
            }
            Set<String> aliases = new HashSet<>();
            for (String alias : node.aliases()) {
                validateAlias(alias, where);
                if (!aliases.add(searchKey(alias))) {
                    throw new CurriculumException(where + ": apelido repetido '" + alias + "'");
                }
            }
        }
    }

    private void validateAlias(String alias, String where) {
        requireText(alias, where + ": apelido vazio");
        if (alias.strip().length() > MAX_ALIAS_LENGTH) {
            throw new CurriculumException(
                    where + ": apelido com mais de " + MAX_ALIAS_LENGTH + " caracteres");
        }
    }

    /**
     * A chave com que a busca compara termos: sem acento, sem caixa, hífen valendo espaço e sem
     * espaço sobrando — "Meia-guarda" e "meia guarda" são o mesmo termo. Espelha {@code searchKey}
     * de {@code shared/domain/src/posicoes.ts} (D17), que é quem busca de fato.
     */
    static String searchKey(String term) {
        String semAcento =
                Normalizer.normalize(term.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento
                .toLowerCase(Locale.ROOT)
                .replaceAll("[-_]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private Map<String, CurriculumSource.NodeSpec> indexNodes(
            List<CurriculumSource.Module> modules) {
        Map<String, CurriculumSource.NodeSpec> byCode = new LinkedHashMap<>();
        Set<String> moduleCodes = new HashSet<>();

        for (CurriculumSource.Module module : modules) {
            if (module.code() == null || module.code().isBlank()) {
                throw new CurriculumException("Módulo sem código");
            }
            if (!moduleCodes.add(module.code())) {
                throw new CurriculumException("Código de módulo duplicado: " + module.code());
            }
            if (module.nodes() == null || module.nodes().isEmpty()) {
                throw new CurriculumException("Módulo sem nós: " + module.code());
            }
            for (CurriculumSource.NodeSpec node : module.nodes()) {
                if (node.code() == null || node.code().isBlank()) {
                    throw new CurriculumException("Nó sem código no módulo " + module.code());
                }
                if (byCode.putIfAbsent(node.code(), node) != null) {
                    throw new CurriculumException("Código de nó duplicado: " + node.code());
                }
            }
        }
        return byCode;
    }

    private void validateFields(
            List<CurriculumSource.Module> modules, Map<String, CurriculumSource.NodeSpec> byCode) {
        for (CurriculumSource.Module module : modules) {
            for (CurriculumSource.NodeSpec node : module.nodes()) {
                String where = "nó " + node.code();

                requireText(node.title(), where + ": título vazio");
                requireText(node.concept(), where + ": conceito vazio");
                validateConceptParagraphs(node.concept(), where);
                if (CONCEPT_LENGTH_CURATED_MODULES.contains(module.code())) {
                    validateConceptLength(node.concept(), where);
                }

                try {
                    Belt.valueOf(node.belt());
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw new CurriculumException(where + ": faixa inválida '" + node.belt() + "'");
                }

                UnlockRule rule;
                try {
                    rule = UnlockRule.valueOf(node.unlockRule());
                } catch (IllegalArgumentException e) {
                    throw new CurriculumException(
                            where + ": unlockRule inválida '" + node.unlockRule() + "'");
                }
                if (rule == UnlockRule.ANY && node.prereqs().size() < 2) {
                    throw new CurriculumException(
                            where + ": unlockRule ANY exige ao menos dois pré-requisitos");
                }

                if (node.video() != null && node.video().youtubeId() != null) {
                    validateVideo(
                            node.video().youtubeId(),
                            node.video().title(),
                            node.video().channel(),
                            node.video().startSeconds(),
                            where);
                }

                validateExtraVideos(node, where);

                validateQuiz(node, where);
            }
        }
        if (byCode.isEmpty()) {
            throw new CurriculumException("Currículo vazio");
        }
    }

    /**
     * Um vídeo catalogado precisa ser <em>utilizável</em>: id no formato que o player aceita e
     * canal creditado.
     *
     * <p>O id é conferido aqui porque um id inválido não quebra nada na ingestão — ele vira um
     * iframe vazio na tela do nó, semanas depois. Crédito ao canal é política de uso de vídeo (D7),
     * não metadado opcional; e sem título o crédito na tela começa com um travessão solto ("— canal
     * X"), que parece defeito.
     */
    private void validateVideo(
            String youtubeId, String title, String channel, Integer startSeconds, String where) {
        if (!YOUTUBE_ID.matcher(youtubeId).matches()) {
            throw new CurriculumException(
                    where + ": id de vídeo fora do formato do YouTube '" + youtubeId + "'");
        }
        requireText(title, where + ": vídeo catalogado sem título");
        requireText(channel, where + ": vídeo catalogado sem canal creditado (política D7)");
        if (startSeconds != null && startSeconds < 0) {
            throw new CurriculumException(where + ": startSeconds negativo (" + startSeconds + ")");
        }
    }

    /**
     * Complementares passam pelas mesmas regras do canônico, mais três que só existem aqui.
     *
     * <p><strong>Duplicata dentro do nó</strong> é sempre erro de catalogação: o mesmo id duas
     * vezes no mesmo nó renderiza o mesmo clipe duas vezes na tira. Já o mesmo id em nós
     * <em>diferentes</em> é deliberadamente permitido — uma reposição de guarda contra joelho na
     * barriga é pista de memória tanto do nó de recuperação quanto do de joelho na barriga, e
     * proibir isso não protegeria nada.
     *
     * <p><strong>Teto</strong>: ver {@link #MAX_EXTRA_VIDEOS}.
     *
     * <p>Um nó pode ter complementar <em>sem</em> ter canônico. Exigir o canônico como pré-condição
     * encodaria a hierarquia no dado, mas travaria a catalogação de complementares atrás da
     * curadoria inteira de M2–M8 — e a tela já é honesta nesse caso, porque o estado vazio do
     * canônico continua aparecendo acima da tira.
     */
    private void validateExtraVideos(CurriculumSource.NodeSpec node, String where) {
        List<CurriculumSource.ExtraVideoSpec> extras = node.extraVideos();
        if (extras.size() > MAX_EXTRA_VIDEOS) {
            throw new CurriculumException(
                    where
                            + ": "
                            + extras.size()
                            + " vídeos complementares, o máximo é "
                            + MAX_EXTRA_VIDEOS
                            + " (D32)");
        }

        String canonicalId =
                node.video() == null || node.video().youtubeId() == null
                        ? null
                        : node.video().youtubeId();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < extras.size(); i++) {
            CurriculumSource.ExtraVideoSpec extra = extras.get(i);
            String eWhere = where + ", complementar " + (i + 1);

            if (extra.youtubeId() == null || extra.youtubeId().isBlank()) {
                throw new CurriculumException(eWhere + ": sem id de vídeo");
            }
            validateVideo(
                    extra.youtubeId(),
                    extra.title(),
                    extra.channel(),
                    extra.startSeconds(),
                    eWhere);

            if (extra.youtubeId().equals(canonicalId)) {
                throw new CurriculumException(
                        eWhere + ": repete o vídeo canônico do nó (" + extra.youtubeId() + ")");
            }
            if (!seen.add(extra.youtubeId())) {
                throw new CurriculumException(
                        eWhere + ": complementar repetido no mesmo nó (" + extra.youtubeId() + ")");
            }
            if (extra.orientation() != null) {
                try {
                    VideoOrientation.valueOf(extra.orientation());
                } catch (IllegalArgumentException e) {
                    throw new CurriculumException(
                            eWhere + ": orientação inválida '" + extra.orientation() + "'");
                }
            }
        }
    }

    private void validateQuiz(CurriculumSource.NodeSpec node, String where) {
        List<CurriculumSource.QuestionSpec> quiz = node.quiz();
        if (!quiz.isEmpty() && quiz.size() < MIN_QUIZ_QUESTIONS) {
            throw new CurriculumException(
                    where
                            + ": banco de quiz com "
                            + quiz.size()
                            + " perguntas, o mínimo é "
                            + MIN_QUIZ_QUESTIONS
                            + " (D44)");
        }

        Set<String> prompts = new HashSet<>();
        for (int i = 0; i < quiz.size(); i++) {
            CurriculumSource.QuestionSpec question = quiz.get(i);
            String qWhere = where + ", pergunta " + (i + 1);

            requireText(question.prompt(), qWhere + ": enunciado vazio");
            if (!prompts.add(question.prompt().strip())) {
                throw new CurriculumException(qWhere + ": enunciado repetido no mesmo nó");
            }
            requireText(question.explanation(), qWhere + ": explicação vazia");

            if (question.options().size() < 2) {
                throw new CurriculumException(qWhere + ": precisa de ao menos duas alternativas");
            }
            long correct =
                    question.options().stream()
                            .filter(CurriculumSource.OptionSpec::correct)
                            .count();
            if (correct != 1) {
                throw new CurriculumException(
                        qWhere + ": precisa de exatamente uma alternativa correta, tem " + correct);
            }
            for (CurriculumSource.OptionSpec option : question.options()) {
                requireText(option.text(), qWhere + ": alternativa com texto vazio");
            }
        }
    }

    private void validatePrereqReferences(Map<String, CurriculumSource.NodeSpec> byCode) {
        for (CurriculumSource.NodeSpec node : byCode.values()) {
            Set<String> seen = new HashSet<>();
            for (String prereq : node.prereqs()) {
                if (!byCode.containsKey(prereq)) {
                    throw new CurriculumException(
                            "nó " + node.code() + ": pré-requisito inexistente '" + prereq + "'");
                }
                if (prereq.equals(node.code())) {
                    throw new CurriculumException(
                            "nó " + node.code() + ": é pré-requisito de si mesmo");
                }
                if (!seen.add(prereq)) {
                    throw new CurriculumException(
                            "nó " + node.code() + ": pré-requisito repetido '" + prereq + "'");
                }
            }
        }
    }

    /** Busca em profundidade com marcação tri-estado; reporta o caminho do ciclo encontrado. */
    private void validateNoCycles(Map<String, CurriculumSource.NodeSpec> byCode) {
        Map<String, Mark> marks = new HashMap<>();
        Deque<String> path = new ArrayDeque<>();

        for (String code : byCode.keySet()) {
            visit(code, byCode, marks, path);
        }
    }

    private void visit(
            String code,
            Map<String, CurriculumSource.NodeSpec> byCode,
            Map<String, Mark> marks,
            Deque<String> path) {

        Mark mark = marks.get(code);
        if (mark == Mark.DONE) {
            return;
        }
        if (mark == Mark.VISITING) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(code);
            throw new CurriculumException("Ciclo de pré-requisitos: " + String.join(" -> ", cycle));
        }

        marks.put(code, Mark.VISITING);
        path.addLast(code);
        for (String prereq : byCode.get(code).prereqs()) {
            visit(prereq, byCode, marks, path);
        }
        path.removeLast();
        marks.put(code, Mark.DONE);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CurriculumException(message);
        }
    }

    /**
     * No máximo {@link #MAX_CONCEPT_PARAGRAPHS} parágrafos, separados por linha em branco. Regra
     * ativa desde já: nenhum conceito hoje usa linha em branco, então não há currículo existente
     * para quebrar.
     */
    private void validateConceptParagraphs(String concept, String where) {
        int paragraphs = countParagraphs(concept);
        if (paragraphs > MAX_CONCEPT_PARAGRAPHS) {
            throw new CurriculumException(
                    where
                            + ": conceito com "
                            + paragraphs
                            + " parágrafos, o máximo é "
                            + MAX_CONCEPT_PARAGRAPHS);
        }
    }

    /**
     * Faixa de tamanho do conceito. Só chamado para módulos em {@link
     * #CONCEPT_LENGTH_CURATED_MODULES}.
     */
    void validateConceptLength(String concept, String where) {
        int length = concept.strip().length();
        if (length < MIN_CONCEPT_LENGTH || length > MAX_CONCEPT_LENGTH) {
            throw new CurriculumException(
                    where
                            + ": conceito com "
                            + length
                            + " caracteres, fora da faixa de "
                            + MIN_CONCEPT_LENGTH
                            + " a "
                            + MAX_CONCEPT_LENGTH);
        }
    }

    private int countParagraphs(String concept) {
        String[] parts = concept.strip().split("\\n\\s*\\n+");
        int count = 0;
        for (String part : parts) {
            if (!part.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private enum Mark {
        VISITING,
        DONE
    }
}
