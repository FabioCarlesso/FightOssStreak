import { Link } from 'react-router-dom';
import {
  graduationForm,
  toGraduationGoalBody,
  todayIso,
  type GraduationFormState,
} from '@fos/domain';
import { useState } from 'react';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * A meta do próximo grau, na tela Conta (#193, D75).
 *
 * Os números vêm da academia de quem usa, e é a própria pessoa que os digita: o app não traz lista
 * nem mínimo de academia nenhuma. Só a data do último grau é obrigatória — há academia que não
 * separa com e sem kimono, e há quem só queira contar.
 */
export function GraduationSettings() {
  const graduacao = useAsync(() => api.getGraduation(), []);
  // Fora do formulário porque ele é remontado quando a meta recarrega, e o aviso tem de sobreviver.
  const [aviso, setAviso] = useState<string | null>(null);
  const goal = graduacao.data?.goal;
  // Chave por meta carregada: o formulário recomeça a partir do que o servidor tem, e não do que
  // ficou digitado antes de salvar ou remover.
  const chave = goal ? JSON.stringify(goal) : 'sem-meta';

  return (
    <section className="card" aria-labelledby="graduacao-conta-titulo">
      <header className="card__header">
        <h2 id="graduacao-conta-titulo">Graduação</h2>
      </header>
      <p className="hint">
        Coloque a data do seu último grau e, se quiser, os mínimos de aulas que a sua academia pede
        para o próximo. A tela inicial passa a contar as aulas que você registra no diário.
      </p>
      {graduacao.loading && !graduacao.data && <p className="empty">Carregando…</p>}
      {graduacao.error && <p className="error">{graduacao.error.message}</p>}
      {graduacao.data && (
        <GraduationForm
          key={chave}
          initial={graduationForm(goal)}
          configured={goal != null}
          onChange={() => setAviso(null)}
          onDone={(mensagem) => {
            setAviso(mensagem);
            graduacao.reload();
          }}
        />
      )}
      {aviso && <p className="hint">{aviso}</p>}
    </section>
  );
}

function GraduationForm({
  initial,
  configured,
  onChange,
  onDone,
}: {
  initial: GraduationFormState;
  configured: boolean;
  onChange: () => void;
  onDone: (mensagem: string) => void;
}) {
  const [form, setForm] = useState(initial);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const today = todayIso();

  function set<K extends keyof GraduationFormState>(key: K, value: GraduationFormState[K]) {
    setForm((atual) => ({ ...atual, [key]: value }));
    onChange();
  }

  async function run(action: () => Promise<unknown>, mensagem: string) {
    setSaving(true);
    setFailure(null);
    try {
      await action();
      onDone(mensagem);
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      className="session-form"
      onSubmit={(event) => {
        event.preventDefault();
        void run(() => api.saveGraduationGoal(toGraduationGoalBody(form)), 'Meta salva.');
      }}
    >
      <div className="session-form__row">
        <label className="session-form__field">
          <span>Data do último grau *</span>
          <input
            type="date"
            required
            max={today}
            value={form.lastGraduationOn}
            onChange={(event) => set('lastGraduationOn', event.target.value)}
          />
        </label>
        <label className="session-form__field">
          <span>Grau atual</span>
          <input
            type="text"
            maxLength={60}
            placeholder="branca 1º grau"
            value={form.currentLabel}
            onChange={(event) => set('currentLabel', event.target.value)}
          />
        </label>
        <label className="session-form__field">
          <span>Próximo grau</span>
          <input
            type="text"
            maxLength={60}
            placeholder="branca 2º grau"
            value={form.nextLabel}
            onChange={(event) => set('nextLabel', event.target.value)}
          />
        </label>
      </div>

      <div className="session-form__row">
        <NumberField
          label="Mínimo de aulas"
          value={form.minTotal}
          onChange={(value) => set('minTotal', value)}
        />
        <NumberField
          label="Com kimono"
          value={form.minGi}
          onChange={(value) => set('minGi', value)}
        />
        <NumberField
          label="Sem kimono"
          value={form.minNogi}
          onChange={(value) => set('minNogi', value)}
        />
      </div>

      <div className="graduation__actions">
        <button type="submit" disabled={saving || form.lastGraduationOn === ''}>
          {saving ? 'Salvando…' : 'Salvar'}
        </button>
        {configured && (
          <button
            type="button"
            className="link-button"
            disabled={saving}
            onClick={() => void run(() => api.deleteGraduationGoal(), 'Meta removida.')}
          >
            Remover meta
          </button>
        )}
      </div>
      {failure && <p className="error">{failure}</p>}
      <p className="hint">
        A lista de requisitos que a academia entregou vira checklist em{' '}
        <Link to="/graduacao">Requisitos do próximo grau</Link>.
      </p>
    </form>
  );
}

function NumberField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="session-form__field">
      <span>{label}</span>
      <input
        type="number"
        min={0}
        max={2000}
        step={1}
        inputMode="numeric"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
