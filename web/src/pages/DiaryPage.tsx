import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { DiaryDay, Feeling, SessionKind, TrainingSession } from '@fos/types';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import {
  FEELING_LABELS,
  SESSION_KIND_LABELS,
  feelingLabel,
  formatDay,
  monthRange,
  sessionKindLabel,
  todayIso,
} from '../content/diario.ts';
import { recallLabel } from '../content/recall.ts';

/**
 * A linha do tempo do diário (#114, D56).
 *
 * Um dia por bloco, do mais recente para o mais antigo, com as sessões daquele dia e os drills que
 * não pertencem a nenhuma. Os avulsos não são um caso de borda: é assim que aparece tudo que foi
 * registrado pela tela do nó, inclusive o que veio antes desta feature — nenhuma linha foi migrada.
 *
 * O filtro por mês vai ao servidor porque é ele quem recorta o período; tipo e sensação são
 * filtrados aqui, sobre a lista que já está em mãos — um ida-e-volta por clique de filtro custaria
 * mais que percorrer um mês de treinos.
 */
export function DiaryPage() {
  const [month, setMonth] = useState(() => todayIso().slice(0, 7));
  const [kind, setKind] = useState<SessionKind | ''>('');
  const [feeling, setFeeling] = useState<Feeling | ''>('');

  const range = useMemo(() => monthRange(`${month}-01`), [month]);
  const diary = useAsync(() => api.getDiary(range), [range.de, range.ate]);

  const days = (diary.data?.days ?? [])
    .map((day) => filterDay(day, kind, feeling))
    .filter((day) => (day.sessions?.length ?? 0) > 0 || (day.avulsos?.length ?? 0) > 0);

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Diário</h2>
          <Link to="/diario/nova" className="diary__cta">
            Registrar treino
          </Link>
        </header>

        <p className="hint">
          O que aconteceu no tatame — com ou sem técnica do currículo. Dia de descanso também cabe
          aqui, e é o único que não conta no streak.
        </p>

        <div className="diary__filters">
          <label className="diary__filter">
            <span>Mês</span>
            <input
              type="month"
              value={month}
              onChange={(event) => setMonth(event.target.value || todayIso().slice(0, 7))}
            />
          </label>
          <label className="diary__filter">
            <span>Tipo</span>
            <select
              value={kind}
              onChange={(event) => setKind(event.target.value as SessionKind | '')}
            >
              <option value="">Todos</option>
              {SESSION_KIND_LABELS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="diary__filter">
            <span>Sensação</span>
            <select
              value={feeling}
              onChange={(event) => setFeeling(event.target.value as Feeling | '')}
            >
              <option value="">Todas</option>
              {FEELING_LABELS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        {diary.data && (
          <p className="diary__count">
            <strong>{diary.data.sessionsInMonth ?? 0}</strong>{' '}
            {diary.data.sessionsInMonth === 1 ? 'treino registrado' : 'treinos registrados'} neste
            mês
          </p>
        )}
      </section>

      {diary.loading && !diary.data && <p className="empty">Carregando diário…</p>}
      {diary.error && <p className="error">{diary.error.message}</p>}

      {diary.data && days.length === 0 && (
        <p className="empty">
          Nada registrado neste recorte. <Link to="/diario/nova">Registre o treino de hoje</Link> —
          a data já basta.
        </p>
      )}

      {days.map((day) => (
        <DayBlock key={day.day} day={day} />
      ))}
    </div>
  );
}

function DayBlock({ day }: { day: DiaryDay }) {
  return (
    <section className="card diary-day">
      <header className="card__header">
        <h3>
          <time dateTime={day.day}>{formatDay(day.day)}</time>
        </h3>
        {!day.countsAsTrainingDay && <span className="pill">Não conta no streak</span>}
      </header>

      {day.sessions?.map((session) => (
        <SessionRow key={session.id} session={session} />
      ))}

      {(day.avulsos?.length ?? 0) > 0 && (
        <div className="diary-day__loose">
          {/* Sem sessão: é o drill registrado pela tela do nó. Aparece como registro de primeira
              classe, e não como sobra — foi assim que o app funcionou até esta feature. */}
          <p className="hint">Registrado direto no nó</p>
          <ul className="diary-technique">
            {day.avulsos?.map((tecnica, index) => (
              <li key={`${tecnica.nodeCode}-${index}`}>
                <Link to={`/no/${tecnica.nodeCode}`}>{tecnica.nodeCode}</Link> {tecnica.nodeTitle}{' '}
                <span className="pill">{recallLabel(tecnica.recall)}</span>
                {tecnica.note && <span className="diary-technique__note">{tecnica.note}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function SessionRow({ session }: { session: TrainingSession }) {
  return (
    <article className="diary-session">
      <header className="diary-session__head">
        <Link to={`/diario/${session.id}`} className="diary-session__kind">
          {sessionKindLabel(session.kind)}
        </Link>
        {session.durationMinutes != null && (
          <span className="pill">{session.durationMinutes} min</span>
        )}
        {session.feeling && <span className="pill">{feelingLabel(session.feeling)}</span>}
        {/* Peso aparece como o número que a pessoa escreveu, sem meta, faixa nem comparação com a
            sessão anterior: o app guarda e mostra, nunca interpreta (D57). */}
        {session.weightKg != null && <span className="pill">{session.weightKg} kg</span>}
      </header>

      {session.learned && <p className="diary-session__text">{session.learned}</p>}
      {session.improve && (
        <p className="diary-session__text diary-session__text--improve">{session.improve}</p>
      )}

      {(session.tecnicas?.length ?? 0) > 0 && (
        <ul className="diary-technique">
          {session.tecnicas?.map((tecnica, index) => (
            <li key={`${tecnica.nodeCode}-${index}`}>
              <Link to={`/no/${tecnica.nodeCode}`}>{tecnica.nodeCode}</Link> {tecnica.nodeTitle}{' '}
              <span className="pill">{recallLabel(tecnica.recall)}</span>
              {tecnica.note && <span className="diary-technique__note">{tecnica.note}</span>}
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

/** Filtro de tela: recorta as sessões do dia sem tocar nos avulsos quando nada foi pedido. */
function filterDay(day: DiaryDay, kind: SessionKind | '', feeling: Feeling | ''): DiaryDay {
  if (!kind && !feeling) return day;
  return {
    ...day,
    sessions: (day.sessions ?? []).filter(
      (session) => (!kind || session.kind === kind) && (!feeling || session.feeling === feeling),
    ),
    // Drill avulso não tem tipo nem sensação: filtrar por um deles é pedir sessão, e ele some.
    avulsos: [],
  };
}
