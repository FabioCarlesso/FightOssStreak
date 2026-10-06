/**
 * O plano do lembrete de revisão (#142): dada a agenda do SRS, quais notificações locais agendar.
 *
 * Função pura, sem Expo e sem relógio próprio, para o teste conferir o plano sem aparelho.
 *
 * **O lembrete é sobre revisão vencida, nunca sobre o streak** (D67): lembrar "não perca a
 * sequência" seria o critério de falha do `docs/produto/mvp-web.md` construído de propósito. Por
 * isso o texto só diz quantas técnicas venceram — sem nome de técnica, que apareceria na tela
 * bloqueada, e sem peso nem sensação, que são dado de saúde (D57).
 */

export interface Preferencias {
  readonly ligado: boolean;
  /** Horário local do lembrete. */
  readonly hora: number;
  readonly minuto: number;
}

export const PREFERENCIAS_PADRAO: Preferencias = { ligado: true, hora: 19, minuto: 0 };

/**
 * Quantos dias à frente agendar. O plano é refeito ao abrir o app e após cada registro, então uma
 * semana cobre quem some por alguns dias sem chegar perto do teto de notificações pendentes do iOS.
 */
export const DIAS_AGENDADOS = 7;

export interface Lembrete {
  readonly quando: Date;
  readonly quantidade: number;
  readonly titulo: string;
  readonly corpo: string;
}

/**
 * Um lembrete por dia, no horário escolhido, para cada dia em que haverá revisão vencida.
 *
 * A contagem de um dia futuro é a de quem não revisar nada até lá: `nextReviewOn` menor ou igual ao
 * dia. Revisar muda as datas, e o registro reagenda tudo. Horário que já passou hoje fica de fora —
 * notificação no passado dispararia na hora, fora do horário que a pessoa escolheu.
 */
export function planejarLembretes(
  proximasRevisoes: readonly (string | null | undefined)[],
  agora: Date,
  preferencias: Preferencias,
  dias: number = DIAS_AGENDADOS,
): Lembrete[] {
  if (!preferencias.ligado) return [];
  const datas = proximasRevisoes.filter((data): data is string => typeof data === 'string');
  if (datas.length === 0) return [];

  const lembretes: Lembrete[] = [];
  for (let i = 0; i < dias; i++) {
    const quando = new Date(
      agora.getFullYear(),
      agora.getMonth(),
      agora.getDate() + i,
      preferencias.hora,
      preferencias.minuto,
    );
    if (quando.getTime() <= agora.getTime()) continue;
    const dia = isoLocal(quando);
    const quantidade = datas.filter((data) => data <= dia).length;
    if (quantidade > 0) lembretes.push({ quando, quantidade, ...textoDoLembrete(quantidade) });
  }
  return lembretes;
}

export function textoDoLembrete(quantidade: number): { titulo: string; corpo: string } {
  return {
    titulo: 'Hora de revisar',
    corpo:
      quantidade === 1
        ? '1 técnica venceu na sua agenda de revisão.'
        : `${quantidade} técnicas venceram na sua agenda de revisão.`,
  };
}

/** Anda o horário em passos de meia hora, dando a volta na meia-noite. */
export function moverHorario(preferencias: Preferencias, passos: number): Preferencias {
  const dia = 24 * 60;
  const minutos =
    (((preferencias.hora * 60 + preferencias.minuto + passos * 30) % dia) + dia) % dia;
  return { ...preferencias, hora: Math.floor(minutos / 60), minuto: minutos % 60 };
}

export function formatarHorario({ hora, minuto }: Preferencias): string {
  return `${String(hora).padStart(2, '0')}:${String(minuto).padStart(2, '0')}`;
}

function isoLocal(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}
