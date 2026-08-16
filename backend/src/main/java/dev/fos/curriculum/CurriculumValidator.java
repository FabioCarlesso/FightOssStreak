package dev.fos.curriculum;

import dev.fos.model.Belt;
import dev.fos.model.UnlockRule;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valida a integridade do currículo <em>antes</em> de ele chegar ao banco.
 *
 * <p>Detecção de ciclo acontece aqui, na ingestão, e nunca em runtime
 * (docs/01-stack-tecnica.md): um ciclo tornaria um conjunto de nós permanentemente
 * inalcançável, e o lugar barato de descobrir isso é no build.
 */
@Component
public class CurriculumValidator {

    /** Id de vídeo do YouTube: 11 caracteres de [A-Za-z0-9_-]. */
    private static final Pattern YOUTUBE_ID = Pattern.compile("[\\w-]{11}");

    /** Executa todas as checagens e lança na primeira violação encontrada. */
    public void validate(List<CurriculumSource.Module> modules) {
        Map<String, CurriculumSource.NodeSpec> byCode = indexNodes(modules);
        validateFields(modules, byCode);
        validatePrereqReferences(byCode);
        validateNoCycles(byCode);
    }

    private Map<String, CurriculumSource.NodeSpec> indexNodes(List<CurriculumSource.Module> modules) {
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
                    validateVideo(node.video(), where);
                }

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
     * não metadado opcional.
     */
    private void validateVideo(CurriculumSource.VideoSpec video, String where) {
        if (!YOUTUBE_ID.matcher(video.youtubeId()).matches()) {
            throw new CurriculumException(
                    where + ": id de vídeo fora do formato do YouTube '" + video.youtubeId() + "'");
        }
        requireText(video.channel(), where + ": vídeo catalogado sem canal creditado (política D7)");
        if (video.startSeconds() != null && video.startSeconds() < 0) {
            throw new CurriculumException(
                    where + ": startSeconds negativo (" + video.startSeconds() + ")");
        }
    }

    private void validateQuiz(CurriculumSource.NodeSpec node, String where) {
        for (int i = 0; i < node.quiz().size(); i++) {
            CurriculumSource.QuestionSpec question = node.quiz().get(i);
            String qWhere = where + ", pergunta " + (i + 1);

            requireText(question.prompt(), qWhere + ": enunciado vazio");
            requireText(question.explanation(), qWhere + ": explicação vazia");

            if (question.options().size() < 2) {
                throw new CurriculumException(qWhere + ": precisa de ao menos duas alternativas");
            }
            long correct = question.options().stream()
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
                    throw new CurriculumException("nó " + node.code() + ": é pré-requisito de si mesmo");
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

    private enum Mark {
        VISITING,
        DONE
    }
}
