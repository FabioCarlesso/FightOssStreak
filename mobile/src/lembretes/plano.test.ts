import {
  formatarHorario,
  moverHorario,
  planejarLembretes,
  PREFERENCIAS_PADRAO,
  textoDoLembrete,
} from './plano';

// 6 de outubro de 2026, 10h, no fuso do aparelho.
const AGORA = new Date(2026, 9, 6, 10, 0);
const AS_19 = PREFERENCIAS_PADRAO;

describe('planejarLembretes', () => {
  it('sem revisão nenhuma na agenda, não agenda nada', () => {
    expect(planejarLembretes([], AGORA, AS_19)).toEqual([]);
    expect(planejarLembretes([null, undefined], AGORA, AS_19)).toEqual([]);
  });

  it('com revisão vencida, agenda para hoje no horário escolhido e segue nos dias seguintes', () => {
    const plano = planejarLembretes(['2026-10-04'], AGORA, AS_19, 3);

    expect(plano.map((l) => l.quando)).toEqual([
      new Date(2026, 9, 6, 19, 0),
      new Date(2026, 9, 7, 19, 0),
      new Date(2026, 9, 8, 19, 0),
    ]);
    expect(plano.every((l) => l.quantidade === 1)).toBe(true);
  });

  it('revisão futura só entra a partir do dia em que vence, e a contagem acumula', () => {
    const plano = planejarLembretes(['2026-10-08', '2026-10-09', '2026-10-30'], AGORA, AS_19, 5);

    expect(plano.map((l) => [l.quando.getDate(), l.quantidade])).toEqual([
      [8, 1],
      [9, 2],
      [10, 2],
    ]);
  });

  it('horário que já passou hoje fica para amanhã, em vez de disparar na hora', () => {
    const noite = new Date(2026, 9, 6, 21, 30);
    const plano = planejarLembretes(['2026-10-06'], noite, AS_19, 2);

    expect(plano.map((l) => l.quando)).toEqual([new Date(2026, 9, 7, 19, 0)]);
  });

  it('desligado não agenda nada, mesmo com revisão vencida', () => {
    expect(planejarLembretes(['2026-10-01'], AGORA, { ...AS_19, ligado: false })).toEqual([]);
  });

  it('agenda no máximo uma semana', () => {
    expect(planejarLembretes(['2026-10-01'], AGORA, AS_19)).toHaveLength(7);
  });
});

describe('textoDoLembrete', () => {
  it('diz só quantas técnicas venceram — nada de streak, peso, sensação ou nome de técnica', () => {
    for (const quantidade of [1, 2, 15]) {
      const { titulo, corpo } = textoDoLembrete(quantidade);
      const texto = `${titulo} ${corpo}`.toLowerCase();
      expect(texto).toContain(String(quantidade));
      expect(texto).not.toMatch(/streak|sequência|peso|kg|sensação|cansad|dor/);
    }
    expect(textoDoLembrete(1).corpo).toBe('1 técnica venceu na sua agenda de revisão.');
    expect(textoDoLembrete(3).corpo).toBe('3 técnicas venceram na sua agenda de revisão.');
  });
});

describe('horário', () => {
  it('anda de meia em meia hora e dá a volta na meia-noite', () => {
    expect(formatarHorario(moverHorario(AS_19, 1))).toBe('19:30');
    expect(formatarHorario(moverHorario(AS_19, -1))).toBe('18:30');
    expect(formatarHorario(moverHorario({ ...AS_19, hora: 23, minuto: 30 }, 1))).toBe('00:00');
    expect(formatarHorario(moverHorario({ ...AS_19, hora: 0, minuto: 0 }, -1))).toBe('23:30');
  });
});
