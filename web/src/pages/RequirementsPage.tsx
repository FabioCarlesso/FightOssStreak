import { useState } from 'react';
import { Link } from 'react-router-dom';
import { parseRequirements, suggestTechniques } from '@fos/domain';
import type {
  NodeSummary,
  Requirement,
  RequirementChecklist,
  RequirementPatch,
  TreeView,
} from '@fos/types';
import { api } from '../api/client.ts';
import { GraduationCard } from '../components/GraduationCard.tsx';
import { TechniquePicker } from '../components/TechniquePicker.tsx';
import { useAsync } from '../state/useAsync.ts';

const STATUS_LABEL: Record<NonNullable<Requirement['status']>, string> = {
  PENDENTE: 'Pendente',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDO: 'Concluído',
};

/**
 * Requisitos do próximo grau como checklist pessoal (#196, D78).
 *
 * A pessoa cola a lista que a academia dela entregou; o app a liga aos nós da árvore e mostra o
 * status de cada requisito, **derivado** do progresso — concluir o nó no quiz ou pelo drill conclui
 * o requisito sem nada a fazer aqui. O app nunca traz lista de academia: o texto é dado da conta, e
 * só ela o vê.
 */
export function RequirementsPage() {
  const remoto = useAsync(() => api.getRequirements(), []);
  const tree = useAsync(() => api.getTree(), []);
  const graduacao = useAsync(() => api.getGraduation(), []);
  // Toda escrita devolve a checklist inteira já recalculada: ela substitui a do servidor na tela.
  const [local, setLocal] = useState<RequirementChecklist | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const checklist = local ?? remoto.data;

  /** Devolve se deu certo: o formulário de colar só se limpa quando o texto entrou. */
  async function executar(acao: () => Promise<RequirementChecklist | void>): Promise<boolean> {
    setBusy(true);
    setFailure(null);
    try {
      const nova = await acao();
      if (nova) setLocal(nova);
      else {
        setLocal(null);
        remoto.reload();
      }
      return true;
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (remoto.error && !checklist) return <p className="error">{remoto.error.message}</p>;
  if (!checklist) return <p className="empty">Carregando requisitos…</p>;

  const itens = checklist.items ?? [];

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Requisitos do próximo grau{checklist.label ? `: ${checklist.label}` : ''}</h2>
          <Link to="/hoje">Voltar</Link>
        </header>
        {itens.length > 0 ? (
          <p className="requirements__summary">
            <strong>{checklist.done ?? 0}</strong> de {checklist.total ?? itens.length} requisitos
            concluídos
          </p>
        ) : (
          <p className="hint">
            Cole a lista de requisitos que a sua academia entregou, um por linha. Ela fica só na sua
            conta.
          </p>
        )}
        <p className="hint">
          O requisito ligado a técnicas conclui sozinho quando elas são concluídas na árvore. Bater
          a lista não é graduar: quem decide o grau é o professor.
        </p>
      </section>

      <GraduationCard graduation={graduacao.data} showRequirementsLink={false} />

      {itens.length > 0 && (
        <section className="card">
          <h2>Checklist</h2>
          <ol className="requirements">
            {itens.map((item, indice) => (
              <RequirementItem
                key={item.id}
                item={item}
                tree={tree.data}
                first={indice === 0}
                last={indice === itens.length - 1}
                busy={busy}
                onPatch={(patch) => executar(() => api.updateRequirement(item.id ?? 0, patch))}
                onMove={(delta) => {
                  const ids = itens.map((i) => i.id ?? 0);
                  const [movido] = ids.splice(indice, 1);
                  ids.splice(indice + delta, 0, movido!);
                  void executar(() => api.reorderRequirements(ids));
                }}
                onDelete={() => executar(() => api.deleteRequirement(item.id ?? 0))}
              />
            ))}
          </ol>
          {failure && <p className="error">{failure}</p>}
          <DeleteAll busy={busy} onConfirm={() => executar(() => api.deleteRequirements())} />
        </section>
      )}

      <ImportForm
        busy={busy}
        hasLabel={Boolean(checklist.label)}
        onImport={(label, text) => executar(() => api.importRequirements({ label, text }))}
      />
      {itens.length === 0 && failure && <p className="error">{failure}</p>}
    </div>
  );
}

function ImportForm({
  busy,
  hasLabel,
  onImport,
}: {
  busy: boolean;
  hasLabel: boolean;
  onImport: (label: string, text: string) => Promise<boolean>;
}) {
  const [label, setLabel] = useState('');
  const [text, setText] = useState('');

  // Prévia com a mesma regra do servidor: a pessoa vê quantos requisitos o texto vira antes de
  // mandar, e a linha longa demais aparece aqui, e não só no erro da resposta.
  let previa: string[] = [];
  let problema: string | null = null;
  try {
    previa = parseRequirements(text);
  } catch (erro) {
    problema = erro instanceof Error ? erro.message : String(erro);
  }

  return (
    <form
      className="card session-form"
      onSubmit={(event) => {
        event.preventDefault();
        void onImport(label, text).then((ok) => {
          if (!ok) return;
          setText('');
          setLabel('');
        });
      }}
    >
      <h2>Colar requisitos</h2>
      <label className="session-form__field">
        <span>Grupo</span>
        <input
          value={label}
          maxLength={60}
          onChange={(event) => setLabel(event.target.value)}
          placeholder={hasLabel ? 'Em branco mantém o atual' : 'Ex.: branca 2º grau'}
        />
      </label>
      <label className="session-form__field">
        <span>Um requisito por linha</span>
        <textarea
          rows={6}
          value={text}
          maxLength={8000}
          onChange={(event) => setText(event.target.value)}
          placeholder="Marcadores (•, -, 1.) e linhas vazias são limpos."
        />
      </label>
      {problema ? (
        <p className="error">{problema}</p>
      ) : (
        previa.length > 0 && (
          <p className="hint">
            {previa.length === 1 ? 'Vira 1 requisito' : `Vira ${previa.length} requisitos`},
            acrescentados ao fim da lista.
          </p>
        )
      )}
      <button type="submit" disabled={busy || previa.length === 0 || problema !== null}>
        Adicionar à checklist
      </button>
    </form>
  );
}

function RequirementItem({
  item,
  tree,
  first,
  last,
  busy,
  onPatch,
  onMove,
  onDelete,
}: {
  item: Requirement;
  tree: TreeView | null;
  first: boolean;
  last: boolean;
  busy: boolean;
  onPatch: (patch: RequirementPatch) => Promise<boolean>;
  onMove: (delta: number) => void;
  onDelete: () => Promise<boolean>;
}) {
  const [ligando, setLigando] = useState(false);
  const nodes = item.nodes ?? [];
  const status = item.status ?? 'PENDENTE';

  return (
    <li className="requirement">
      <div className="requirement__head">
        <span className="requirement__text">{item.text}</span>
        <span className={`pill requirement__status requirement__status--${status.toLowerCase()}`}>
          {STATUS_LABEL[status]}
        </span>
      </div>

      {nodes.length > 0 ? (
        <ul className="requirement__nodes">
          {nodes.map((node) => (
            <li key={node.code}>
              <Link to={`/no/${node.code}`}>{node.code}</Link> {node.title}
            </li>
          ))}
        </ul>
      ) : (
        <label className="requirement__manual">
          <input
            type="checkbox"
            checked={item.manualDone ?? false}
            disabled={busy}
            onChange={(event) => void onPatch({ manualDone: event.target.checked })}
          />
          Feito (requisito sem técnica no currículo)
        </label>
      )}

      {ligando ? (
        <LinkEditor
          item={item}
          tree={tree}
          busy={busy}
          onCancel={() => setLigando(false)}
          onConfirm={(codes) =>
            onPatch({ nodeCodes: codes }).then((ok) => {
              if (ok) setLigando(false);
            })
          }
        />
      ) : (
        <div className="requirement__actions">
          <button
            type="button"
            className="link-button"
            disabled={busy}
            onClick={() => setLigando(true)}
          >
            {nodes.length > 0 ? 'Editar técnicas' : 'Ligar técnicas'}
          </button>
          <button
            type="button"
            className="link-button"
            disabled={busy || first}
            onClick={() => onMove(-1)}
            aria-label={`Subir: ${item.text}`}
          >
            Subir
          </button>
          <button
            type="button"
            className="link-button"
            disabled={busy || last}
            onClick={() => onMove(1)}
            aria-label={`Descer: ${item.text}`}
          >
            Descer
          </button>
          <button
            type="button"
            className="link-button"
            disabled={busy}
            onClick={() => void onDelete()}
            aria-label={`Remover: ${item.text}`}
          >
            Remover
          </button>
        </div>
      )}
    </li>
  );
}

/**
 * Ligar o requisito a técnicas. As sugestões saem da busca de `@fos/domain` e vêm **desmarcadas**:
 * a ligação só é gravada quando a pessoa confirma, e sugerir não é decidir.
 */
function LinkEditor({
  item,
  tree,
  busy,
  onCancel,
  onConfirm,
}: {
  item: Requirement;
  tree: TreeView | null;
  busy: boolean;
  onCancel: () => void;
  onConfirm: (codes: string[]) => Promise<void>;
}) {
  const atuais = (item.nodes ?? []).map((node) => node.code ?? '');
  const [marcados, setMarcados] = useState<string[]>(atuais);
  const [extras, setExtras] = useState<Array<{ code: string; title: string }>>([]);

  const nos: NodeSummary[] = (tree?.modules ?? []).flatMap((module) => module.nodes ?? []);
  const sugestoes = suggestTechniques(nos, item.text ?? '', tree?.positions ?? []);
  const opcoes = [
    ...(item.nodes ?? []).map((n) => ({ code: n.code ?? '', title: n.title ?? '' })),
    ...sugestoes.map((n) => ({ code: n.code ?? '', title: n.title ?? '' })),
    ...extras,
  ].filter((op, i, lista) => lista.findIndex((o) => o.code === op.code) === i);

  function alternar(code: string) {
    setMarcados((atual) =>
      atual.includes(code) ? atual.filter((c) => c !== code) : [...atual, code],
    );
  }

  return (
    <div className="requirement__link">
      {opcoes.length === 0 ? (
        <p className="hint">Nenhuma técnica parecida no currículo. Busque abaixo, se houver.</p>
      ) : (
        <ul className="requirement__options">
          {opcoes.map((op) => (
            <li key={op.code}>
              <label>
                <input
                  type="checkbox"
                  checked={marcados.includes(op.code)}
                  onChange={() => alternar(op.code)}
                />
                <span className="due-list__code">{op.code}</span> {op.title}
                {!atuais.includes(op.code) && sugestoes.some((s) => s.code === op.code) && (
                  <span className="pill"> sugerida</span>
                )}
              </label>
            </li>
          ))}
        </ul>
      )}
      <TechniquePicker
        tree={tree}
        exclude={opcoes.map((op) => op.code)}
        onPick={(node) => {
          const code = node.code ?? '';
          setExtras((atual) => [...atual, { code, title: node.title ?? '' }]);
          setMarcados((atual) => (atual.includes(code) ? atual : [...atual, code]));
        }}
      />
      <div className="requirement__actions">
        <button type="button" disabled={busy} onClick={() => void onConfirm(marcados)}>
          Confirmar ligação
        </button>
        <button type="button" className="link-button" disabled={busy} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </div>
  );
}

/** Apagar a lista inteira pede um segundo toque — sem diálogo do navegador, que trava a tela. */
function DeleteAll({ busy, onConfirm }: { busy: boolean; onConfirm: () => Promise<boolean> }) {
  const [confirmando, setConfirmando] = useState(false);
  if (!confirmando) {
    return (
      <p className="requirements__danger">
        <button
          type="button"
          className="link-button"
          disabled={busy}
          onClick={() => setConfirmando(true)}
        >
          Apagar a checklist
        </button>
      </p>
    );
  }
  return (
    <p className="requirements__danger">
      Apagar todos os requisitos? Técnicas e progresso não são tocados.{' '}
      <button type="button" disabled={busy} onClick={() => void onConfirm()}>
        Apagar
      </button>{' '}
      <button type="button" className="link-button" onClick={() => setConfirmando(false)}>
        Cancelar
      </button>
    </p>
  );
}
