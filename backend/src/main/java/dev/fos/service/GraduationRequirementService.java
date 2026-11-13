package dev.fos.service;

import dev.fos.model.GraduationChecklist;
import dev.fos.model.GraduationRequirement;
import dev.fos.model.Node;
import dev.fos.model.ProgressStatus;
import dev.fos.model.RequirementStatus;
import dev.fos.model.UserProgress;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.GraduationChecklistRepository;
import dev.fos.repo.GraduationRequirementRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.web.dto.RequirementDtos;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requisitos do próximo grau como checklist pessoal (#196, D78).
 *
 * <p>A pessoa cola a lista que a academia dela entregou, e o app a liga aos nós da árvore. O app
 * nunca traz lista de academia nenhuma: material de academia costuma ser protegido, e o repositório
 * é público (D11).
 *
 * <p>O status é <b>derivado a cada leitura</b> do progresso gravado dos nós ligados: concluir um nó
 * no quiz ou pelo drill muda o requisito sem ação nenhuma na checklist.
 */
@Service
public class GraduationRequirementService {

    /** Teto de itens da checklist inteira: uma lista de grau, não um manual. */
    public static final int MAX_ITEMS = 60;

    /** Teto de um item, igual à coluna. */
    public static final int MAX_ITEM_LENGTH = 200;

    /**
     * Espaço como o {@code \s} e o {@code trim()} do JS o entendem, e não como o {@code \s} e o
     * {@code strip()} do Java: o NBSP ({@code U+00A0}), que texto copiado de PDF e de editor traz
     * depois de marcador, não é espaço para o Java. Sem isto, a prévia da tela ({@code
     * parseRequirements}) limpava o marcador seguido de NBSP e o servidor o gravava junto (D17).
     */
    private static final String ESPACO =
            "[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";

    /**
     * Marcador no começo da linha: {@code •}, {@code -}, {@code *}, numeração ({@code 1.}, {@code
     * 2)}, {@code (3)}) e letra ({@code a)}, {@code B.}). Número sem pontuação depois não é
     * marcador ("100kg..." começa com número e é texto), nem número decimal ("1.5 ...").
     */
    private static final Pattern MARCADOR =
            Pattern.compile(
                    "^(?:[-*•·–—▪◦►]+"
                            + ESPACO
                            + "*|\\(?\\d{1,3}[.)](?!\\d)"
                            + ESPACO
                            + "*|\\(\\d{1,3}\\)"
                            + ESPACO
                            + "*|[A-Za-z][.)]"
                            + ESPACO
                            + "+)");

    /** Espaço nas pontas da linha, no mesmo conjunto do {@code trim()} do JS. */
    private static final Pattern PONTAS = Pattern.compile("^" + ESPACO + "+|" + ESPACO + "+$");

    /** Sequência de espaço no meio da linha, que vira um espaço só. */
    private static final Pattern SEQUENCIA = Pattern.compile(ESPACO + "+");

    private final AppUserRepository contas;
    private final GraduationChecklistRepository checklists;
    private final GraduationRequirementRepository requirements;
    private final NodeRepository nodes;
    private final UserProgressRepository progress;
    private final UnlockService unlock;
    private final Clock clock;

    public GraduationRequirementService(
            AppUserRepository contas,
            GraduationChecklistRepository checklists,
            GraduationRequirementRepository requirements,
            NodeRepository nodes,
            UserProgressRepository progress,
            UnlockService unlock,
            Clock clock) {
        this.contas = contas;
        this.checklists = checklists;
        this.requirements = requirements;
        this.nodes = nodes;
        this.progress = progress;
        this.unlock = unlock;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RequirementDtos.ChecklistView get(Long userId) {
        return view(userId);
    }

    /**
     * Cola a lista: cada linha vira um requisito ao fim da checklist. Acrescenta, e nunca
     * substitui: colar de novo por engano não pode apagar vínculo que a pessoa já confirmou.
     */
    @Transactional
    public RequirementDtos.ChecklistView importText(
            Long userId, RequirementDtos.ImportRequest request) {
        List<String> linhas = parse(request.text());
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("Nenhum requisito no texto colado.");
        }
        // Trava a linha da conta antes de contar: sem isto, dois "importar" simultâneos liam o
        // mesmo total, passavam juntos do teto e repetiam o order_index. A conta, e não a
        // checklist, porque a checklist pode ainda não existir — e dois INSERT da mesma chave
        // fariam o perdedor responder 500.
        contas.lockById(userId);
        List<GraduationRequirement> atuais =
                requirements.findByUserIdOrderByOrderIndexAscIdAsc(userId);
        if (atuais.size() + linhas.size() > MAX_ITEMS) {
            throw new IllegalArgumentException(
                    "A checklist aceita até "
                            + MAX_ITEMS
                            + " requisitos; com este texto seriam "
                            + (atuais.size() + linhas.size())
                            + ".");
        }

        GraduationChecklist checklist =
                checklists.findById(userId).orElseGet(() -> new GraduationChecklist(userId));
        String rotulo = trimToNull(request.label());
        if (rotulo != null) {
            checklist.setLabel(rotulo);
        }
        checklist.touch(clock.instant());
        checklists.save(checklist);

        int ordem = atuais.stream().mapToInt(GraduationRequirement::getOrderIndex).max().orElse(-1);
        for (String linha : linhas) {
            requirements.save(new GraduationRequirement(userId, ++ordem, linha, clock.instant()));
        }
        return view(userId);
    }

    @Transactional
    public RequirementDtos.ChecklistView update(
            Long userId, Long id, RequirementDtos.RequirementPatch patch) {
        GraduationRequirement requisito = require(userId, id);
        if (patch.text() != null) {
            // O mesmo espaço do texto colado: editar não pode gravar quebra de linha nem NBSP que
            // colar não gravaria. Marcador não é tirado — aqui a pessoa digitou o texto inteiro.
            String texto = normalizeSpaces(patch.text());
            if (texto.isEmpty()) {
                throw new IllegalArgumentException("O requisito não pode ficar vazio.");
            }
            requisito.setText(texto);
        }
        if (patch.nodeCodes() != null) {
            requisito.setNodeCodes(requireNodes(patch.nodeCodes()));
        }
        if (patch.manualDone() != null) {
            requisito.setManualDone(patch.manualDone());
        }
        // A marcação à mão só existe em requisito sem nó. Guardada debaixo de um vínculo, ela
        // voltaria a valer sozinha no dia em que a pessoa desligasse os nós, e o requisito
        // apareceria concluído sem ninguém ter marcado nada.
        if (!requisito.getNodeCodes().isEmpty()) {
            requisito.setManualDone(false);
        }
        return view(userId);
    }

    /** Reordena: a lista precisa ter todos os ids da checklist, cada um uma vez. */
    @Transactional
    public RequirementDtos.ChecklistView reorder(Long userId, List<Long> ids) {
        List<GraduationRequirement> atuais =
                requirements.findByUserIdOrderByOrderIndexAscIdAsc(userId);
        Set<Long> esperados =
                atuais.stream().map(GraduationRequirement::getId).collect(Collectors.toSet());
        if (ids.size() != esperados.size() || !esperados.equals(new HashSet<>(ids))) {
            throw new IllegalArgumentException(
                    "A ordem precisa trazer todos os requisitos da checklist, cada um uma vez.");
        }
        Map<Long, GraduationRequirement> porId =
                atuais.stream()
                        .collect(
                                Collectors.toMap(
                                        GraduationRequirement::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            porId.get(ids.get(i)).setOrderIndex(i);
        }
        return view(userId);
    }

    @Transactional
    public RequirementDtos.ChecklistView delete(Long userId, Long id) {
        requirements.delete(require(userId, id));
        return view(userId);
    }

    /** Apaga a checklist inteira — quando o grau chegou. Os nós e o progresso não são tocados. */
    @Transactional
    public void deleteAll(Long userId) {
        requirements.deleteByUserId(userId);
        checklists.deleteById(userId);
    }

    /**
     * O texto colado vira requisitos: uma linha por item, sem linha vazia, sem marcador e com
     * espaço normalizado. Item acima de {@link #MAX_ITEM_LENGTH} é recusado com a linha apontada, e
     * não cortado: cortar mudaria o requisito sem a pessoa ver.
     */
    static List<String> parse(String texto) {
        List<String> itens = new ArrayList<>();
        String[] linhas = texto.split("\\r?\\n|\\r");
        for (int i = 0; i < linhas.length; i++) {
            String linha = semPontas(linhas[i]);
            String anterior;
            do {
                anterior = linha;
                linha = semPontas(MARCADOR.matcher(linha).replaceFirst(""));
            } while (!linha.equals(anterior) && !linha.isEmpty());
            linha = normalizeSpaces(linha);
            if (linha.isEmpty()) {
                continue;
            }
            if (linha.length() > MAX_ITEM_LENGTH) {
                throw new IllegalArgumentException(
                        "A linha "
                                + (i + 1)
                                + " passa de "
                                + MAX_ITEM_LENGTH
                                + " caracteres: um requisito por linha.");
            }
            itens.add(linha);
        }
        return itens;
    }

    /**
     * Espelho de {@code requirementStatus} em {@code shared/domain} (D17).
     *
     * <p>Lê o progresso <b>gravado</b>, e não o status exibido: um nó drillado pelo diário mas
     * ainda travado na árvore aparece como {@code LOCKED}, e mesmo assim foi treinado.
     *
     * @param gravados status gravado de cada nó ligado ({@code null} = nunca tocado)
     */
    static RequirementStatus statusOf(Collection<ProgressStatus> gravados, boolean manualDone) {
        if (gravados.isEmpty()) {
            return manualDone ? RequirementStatus.CONCLUIDO : RequirementStatus.PENDENTE;
        }
        if (gravados.stream().allMatch(s -> s == ProgressStatus.COMPLETED)) {
            return RequirementStatus.CONCLUIDO;
        }
        if (gravados.stream()
                .anyMatch(s -> s == ProgressStatus.COMPLETED || s == ProgressStatus.IN_PROGRESS)) {
            return RequirementStatus.EM_ANDAMENTO;
        }
        return RequirementStatus.PENDENTE;
    }

    private RequirementDtos.ChecklistView view(Long userId) {
        String rotulo = checklists.findById(userId).map(GraduationChecklist::getLabel).orElse(null);
        List<GraduationRequirement> itens =
                requirements.findByUserIdOrderByOrderIndexAscIdAsc(userId);
        if (itens.isEmpty()) {
            return new RequirementDtos.ChecklistView(rotulo, List.of(), 0, 0, List.of());
        }

        List<Node> arvore = nodes.findAllOrdered();
        Map<String, Node> porCodigo =
                arvore.stream().collect(Collectors.toMap(Node::getCode, Function.identity()));
        Map<Node, Integer> ordem = new java.util.HashMap<>();
        for (int i = 0; i < arvore.size(); i++) {
            ordem.put(arvore.get(i), i);
        }
        Map<Long, ProgressStatus> gravados =
                progress.findByIdUserId(userId).stream()
                        .collect(
                                Collectors.toMap(
                                        p -> p.getId().getNodeId(), UserProgress::getStatus));
        Map<Long, ProgressStatus> exibidos = unlock.resolveStatuses(arvore, gravados);

        List<RequirementDtos.RequirementView> views = new ArrayList<>();
        Set<String> todosOsNos = new LinkedHashSet<>();
        int concluidos = 0;
        for (GraduationRequirement item : itens) {
            List<Node> ligados =
                    item.getNodeCodes().stream()
                            .map(porCodigo::get)
                            .filter(java.util.Objects::nonNull)
                            .sorted(java.util.Comparator.comparingInt(ordem::get))
                            .toList();
            List<ProgressStatus> deles = new ArrayList<>();
            for (Node n : ligados) {
                deles.add(gravados.get(n.getId()));
                todosOsNos.add(n.getCode());
            }
            RequirementStatus status = statusOf(deles, item.isManualDone());
            if (status == RequirementStatus.CONCLUIDO) {
                concluidos++;
            }
            views.add(
                    new RequirementDtos.RequirementView(
                            item.getId(),
                            item.getOrderIndex(),
                            item.getText(),
                            status,
                            item.isManualDone(),
                            ligados.stream()
                                    .map(
                                            n ->
                                                    new RequirementDtos.LinkedNodeView(
                                                            n.getCode(),
                                                            n.getTitle(),
                                                            exibidos.get(n.getId())))
                                    .toList()));
        }
        return new RequirementDtos.ChecklistView(
                rotulo, views, concluidos, views.size(), List.copyOf(todosOsNos));
    }

    private GraduationRequirement require(Long userId, Long id) {
        return requirements
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RequirementNotFoundException(id));
    }

    private Set<String> requireNodes(List<String> codigos) {
        Set<String> resultado = new LinkedHashSet<>();
        for (String codigo : codigos) {
            String limpo = codigo.strip().toUpperCase(java.util.Locale.ROOT);
            if (nodes.findByCode(limpo).isEmpty()) {
                throw new IllegalArgumentException("Nó inexistente: " + limpo);
            }
            resultado.add(limpo);
        }
        return resultado;
    }

    /** Pontas aparadas e toda sequência de espaço (quebra de linha e NBSP inclusos) vira um. */
    static String normalizeSpaces(String texto) {
        return SEQUENCIA.matcher(semPontas(texto)).replaceAll(" ");
    }

    private static String semPontas(String linha) {
        return PONTAS.matcher(linha).replaceAll("");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String limpo = value.strip();
        return limpo.isEmpty() ? null : limpo;
    }
}
