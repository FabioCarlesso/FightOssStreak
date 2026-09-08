import { FEELING_LABELS, SESSION_KIND_LABELS } from '../content/diario.ts';
import type { SessionFieldsState } from '../state/sessionFields.ts';

/**
 * Os campos da sessão, do jeito que as duas telas os escrevem (#114, D56).
 *
 * Existe para o formulário de registro e o de edição não divergirem: são o mesmo treino visto em
 * dois momentos, e um campo que aparece só em um dos dois seria um campo que ninguém preenche.
 *
 * **Só a data é obrigatória.** Seis campos no vestiário é o jeito de garantir que ninguém preencha
 * — o resto se completa depois, e sessão incompleta é sessão válida.
 */
export function SessionFields({
  value,
  onChange,
  today,
}: {
  value: SessionFieldsState;
  onChange: (next: SessionFieldsState) => void;
  today: string;
}) {
  function set<K extends keyof SessionFieldsState>(key: K, next: SessionFieldsState[K]) {
    onChange({ ...value, [key]: next });
  }

  return (
    <div className="session-form">
      <div className="session-form__row">
        <label className="session-form__field">
          <span>Data *</span>
          {/* `max` é o mesmo limite do backend: data futura é recusada lá, e a tela não deve
              oferecer o que vai voltar como erro. */}
          <input
            type="date"
            required
            max={today}
            value={value.trainedOn}
            onChange={(event) => set('trainedOn', event.target.value)}
          />
        </label>

        <label className="session-form__field">
          <span>Duração (min)</span>
          <input
            type="number"
            min={1}
            max={1440}
            inputMode="numeric"
            value={value.durationMinutes}
            onChange={(event) => set('durationMinutes', event.target.value)}
          />
        </label>

        <label className="session-form__field">
          <span>Peso (kg)</span>
          <input
            type="number"
            min={20}
            max={300}
            step="0.1"
            inputMode="decimal"
            value={value.weightKg}
            onChange={(event) => set('weightKg', event.target.value)}
          />
        </label>
      </div>

      <fieldset className="session-form__group">
        <legend>Tipo</legend>
        <div className="session-form__chips">
          {SESSION_KIND_LABELS.map((option) => (
            <label
              key={option.value}
              className={value.kind === option.value ? 'chip chip--on' : 'chip'}
              title={option.hint}
            >
              <input
                type="radio"
                name="kind"
                checked={value.kind === option.value}
                onChange={() => set('kind', option.value)}
              />
              {option.label}
            </label>
          ))}
        </div>
        {value.kind === 'DESCANSO' && (
          <p className="hint">
            Descanso fica no diário e não conta no streak — e não recebe técnica do currículo.
          </p>
        )}
      </fieldset>

      <fieldset className="session-form__group">
        <legend>Sensação</legend>
        <div className="session-form__chips">
          {/* Sem meta, sem faixa saudável e sem alerta: o app guarda e mostra, nunca interpreta
              (D57). O rótulo descreve o corpo e não prescreve nada. */}
          {FEELING_LABELS.map((option) => (
            <label
              key={option.value}
              className={value.feeling === option.value ? 'chip chip--on' : 'chip'}
            >
              <input
                type="radio"
                name="feeling"
                checked={value.feeling === option.value}
                onChange={() => set('feeling', option.value)}
              />
              <span aria-hidden="true">{option.icon}</span> {option.label}
            </label>
          ))}
          <label className={value.feeling === '' ? 'chip chip--on' : 'chip'}>
            <input
              type="radio"
              name="feeling"
              checked={value.feeling === ''}
              onChange={() => set('feeling', '')}
            />
            Não dizer
          </label>
        </div>
      </fieldset>

      <label className="session-form__field">
        <span>O que aprendi</span>
        <textarea
          rows={2}
          maxLength={2000}
          value={value.learned}
          onChange={(event) => set('learned', event.target.value)}
          placeholder="A correção que ficou, o detalhe que faltava…"
        />
      </label>

      <label className="session-form__field">
        <span>O que preciso melhorar</span>
        <textarea
          rows={2}
          maxLength={2000}
          value={value.improve}
          onChange={(event) => set('improve', event.target.value)}
          placeholder="O que travou, o que perguntar na próxima aula…"
        />
      </label>
    </div>
  );
}
