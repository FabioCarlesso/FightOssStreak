import { useState } from 'react';
import type { DrillResult, SrsView } from '@fos/types';
import { type Recall, review } from '@fos/domain';
import { api } from '../api/client.ts';
import { RECALL_LABELS } from '../content/recall.ts';

/**
 * Registro de "treinei isso hoje".
 *
 * A auto-avaliação é o que alimenta o SM-2 — sem ela o SRS agendaria tudo no mesmo ritmo. O
 * preview de "volta em N dias" usa `@fos/domain`, a mesma regra do backend, para responder no
 * clique em vez de esperar o round-trip.
 */
export function DrillForm({
  nodeCode,
  srs,
  onDone,
}: {
  nodeCode: string;
  srs?: SrsView;
  onDone: () => void;
}) {
  const [recall, setRecall] = useState<Recall>('OK');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<DrillResult | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const preview = previewNextReview(srs, recall);

  async function submit() {
    setSaving(true);
    setFailure(null);
    try {
      setResult(await api.logDrill(nodeCode, { recall, note: note.trim() || undefined }));
      setNote('');
      onDone();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      className="drill"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <p className="hint">Como saiu no treino de hoje?</p>
      <div className="drill__options">
        {RECALL_LABELS.map((option) => (
          <label
            key={option.value}
            className={
              recall === option.value ? 'drill__option drill__option--on' : 'drill__option'
            }
          >
            <input
              type="radio"
              name="recall"
              checked={recall === option.value}
              onChange={() => setRecall(option.value)}
            />
            <strong>{option.label}</strong>
            <span>{option.hint}</span>
          </label>
        ))}
      </div>

      <label className="drill__note">
        <span>Anotação (opcional)</span>
        <textarea
          value={note}
          onChange={(event) => setNote(event.target.value)}
          rows={2}
          maxLength={1000}
          placeholder="O que travou, o que o professor corrigiu…"
        />
      </label>

      {preview && <p className="hint">Se registrar assim, volta em {preview} dias.</p>}
      {failure && <p className="error">{failure}</p>}

      <button type="submit" disabled={saving}>
        {saving ? 'Registrando…' : 'Treinei isso hoje'}
      </button>

      {result && (
        <p className="drill__result">
          Registrado. Próxima revisão em <strong>{result.nextReviewOn}</strong> · streak de{' '}
          {result.streak?.currentStreak} dia(s).
        </p>
      )}
    </form>
  );
}

/** Preview local do próximo intervalo, usando a mesma regra SM-2 do backend. */
function previewNextReview(srs: SrsView | undefined, recall: Recall): number | null {
  const today = new Date().toISOString().slice(0, 10);
  const current =
    srs?.scheduled && srs.nextReviewOn
      ? {
          repetitions: srs.repetitions ?? 0,
          intervalDays: srs.intervalDays ?? 0,
          easeFactor: 2.5,
          nextReviewOn: srs.nextReviewOn,
        }
      : null;

  // Sem estado persistido o fator de facilidade real é desconhecido, então o preview só é
  // confiável para a primeira revisão. Melhor não prometer número do que prometer errado.
  if (current && (current.repetitions ?? 0) > 2) return null;

  return review(current, recall, today).intervalDays;
}
