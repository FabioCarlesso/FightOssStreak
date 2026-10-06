import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Text } from 'react-native';

import { PortaoConta } from '../components/Portoes';
import { notificadorIndisponivel } from '../lembretes/notificador';
import { TelaConta } from '../screens/TelaConta';
import { TelaNo } from '../screens/TelaNo';
import { apiFalsa, comProvedores, notificadorFalso, preferenciasFalsas } from '../test/fakes';

// 6 de outubro de 2026, 10h: o lembrete das 19h de hoje ainda está à frente.
const AGORA = new Date(2026, 9, 6, 10, 0);
const agora = () => AGORA;

function arvoreCom(...proximas: (string | null)[]) {
  return {
    modules: [
      {
        code: 'M1',
        nodes: proximas.map((nextReviewOn, i) => ({ code: `M1.${i + 1}`, nextReviewOn })),
      },
    ],
  };
}

const NO = {
  code: 'M1.1',
  title: 'Fuga de montada',
  moduleCode: 'M1',
  moduleTitle: 'Sobrevivência',
  status: 'AVAILABLE',
  belt: 'BRANCA',
  concept: 'Conceito.',
  quiz: [],
  srs: { scheduled: true, nextReviewOn: '2026-10-05' },
  safetyNotice: 'Respeite o tap sempre.',
};

describe('Lembrete de revisão', () => {
  it('com revisão vencida e permissão, agenda no horário escolhido — e só a contagem', async () => {
    const notificador = notificadorFalso('concedida');
    const api = apiFalsa({ getTree: jest.fn().mockResolvedValue(arvoreCom('2026-10-05', null)) });
    await comProvedores(<Text>app</Text>, {
      api,
      notificador,
      preferencias: preferenciasFalsas({ ligado: true, hora: 7, minuto: 30 }),
      agora: () => new Date(2026, 9, 6, 6, 0),
    });

    await waitFor(() => expect(notificador.agendados()).toHaveLength(7));
    expect(notificador.agendados()[0]).toMatchObject({
      quando: new Date(2026, 9, 6, 7, 30),
      corpo: '1 técnica venceu na sua agenda de revisão.',
    });
    expect(JSON.stringify(notificador.agendados())).not.toContain('Fuga');
  });

  it('sem revisão vencida, nada é agendado', async () => {
    const notificador = notificadorFalso('concedida');
    const api = apiFalsa({ getTree: jest.fn().mockResolvedValue(arvoreCom(null, null)) });
    await comProvedores(<Text>app</Text>, { api, notificador, agora });

    await waitFor(() => expect(api.getTree).toHaveBeenCalled());
    await waitFor(() => expect(notificador.cancelarTodos).toHaveBeenCalled());
    expect(notificador.agendar).not.toHaveBeenCalled();
  });

  it('não pede permissão ao abrir; pede depois do primeiro registro e reagenda', async () => {
    const notificador = notificadorFalso('indefinida', 'concedida');
    const getTree = jest.fn().mockResolvedValue(arvoreCom('2026-10-05'));
    const api = apiFalsa({
      getNode: jest.fn().mockResolvedValue(NO),
      getTree,
      logDrill: jest.fn().mockImplementation(() => {
        // Revisar empurra a data: depois do registro, a árvore traz a próxima revisão em 10 dias.
        getTree.mockResolvedValue(arvoreCom('2026-10-16'));
        return Promise.resolve({ nextReviewOn: '2026-10-16', streak: { currentStreak: 1 } });
      }),
    });
    await comProvedores(<TelaNo codigo="M1.1" />, { api, notificador, agora });

    const botao = await screen.findByRole('button', { name: 'Treinei isso hoje' });
    await waitFor(() => expect(notificador.permissao).toHaveBeenCalled());
    expect(notificador.pedirPermissao).not.toHaveBeenCalled();

    await fireEvent.press(botao);

    await waitFor(() => expect(notificador.pedirPermissao).toHaveBeenCalledTimes(1));
    // A revisão de hoje foi feita e a próxima vence fora da semana agendada: nenhum lembrete.
    await waitFor(() => expect(getTree).toHaveBeenCalledTimes(1));
    expect(notificador.agendados()).toEqual([]);
  });

  it('registrar com revisão ainda vencida cancela o plano antigo e agenda o novo', async () => {
    const notificador = notificadorFalso('concedida');
    const getTree = jest.fn().mockResolvedValue(arvoreCom('2026-10-05', '2026-10-05'));
    const api = apiFalsa({
      getNode: jest.fn().mockResolvedValue(NO),
      getTree,
      logDrill: jest.fn().mockImplementation(() => {
        getTree.mockResolvedValue(arvoreCom('2026-10-16', '2026-10-05'));
        return Promise.resolve({ nextReviewOn: '2026-10-16', streak: { currentStreak: 1 } });
      }),
    });
    await comProvedores(<TelaNo codigo="M1.1" />, { api, notificador, agora });
    await waitFor(() => expect(notificador.agendados()[0]?.quantidade).toBe(2));

    await fireEvent.press(await screen.findByRole('button', { name: 'Treinei isso hoje' }));

    await waitFor(() => expect(notificador.agendados()[0]?.quantidade).toBe(1));
    expect(notificador.agendados()).toHaveLength(7);
    expect(notificador.pedirPermissao).not.toHaveBeenCalled();
  });

  it('permissão negada não quebra o registro e a conta mostra o caminho dos ajustes', async () => {
    const notificador = notificadorFalso('negada');
    const api = apiFalsa({
      getNode: jest.fn().mockResolvedValue(NO),
      getTree: jest.fn().mockResolvedValue(arvoreCom('2026-10-05')),
      logDrill: jest
        .fn()
        .mockResolvedValue({ nextReviewOn: '2026-10-16', streak: { currentStreak: 1 } }),
    });
    await comProvedores(
      <PortaoConta>
        <TelaNo codigo="M1.1" />
        <TelaConta />
      </PortaoConta>,
      { api, notificador, agora },
    );

    await fireEvent.press(await screen.findByRole('button', { name: 'Treinei isso hoje' }));

    expect(await screen.findByTestId('drill-resultado')).toBeOnTheScreen();
    expect(
      await screen.findByRole('button', { name: 'Abrir ajustes do aparelho' }),
    ).toBeOnTheScreen();
    expect(notificador.pedirPermissao).not.toHaveBeenCalled();
    expect(notificador.agendar).not.toHaveBeenCalled();
    expect(api.getTree).not.toHaveBeenCalled();
  });

  it('falha da API não quebra tela nem deixa o plano pela metade', async () => {
    const notificador = notificadorFalso('concedida');
    const api = apiFalsa({ getTree: jest.fn().mockRejectedValue(new Error('sem rede')) });
    await comProvedores(<Text>app</Text>, { api, notificador, agora });

    await waitFor(() => expect(api.getTree).toHaveBeenCalled());
    expect(screen.getByText('app')).toBeOnTheScreen();
    expect(notificador.agendar).not.toHaveBeenCalled();
  });

  it('no Expo Go do Android o registro segue normal e a conta explica por que não há lembrete', async () => {
    const api = apiFalsa({
      getNode: jest.fn().mockResolvedValue(NO),
      getTree: jest.fn().mockResolvedValue(arvoreCom('2026-10-05')),
      logDrill: jest
        .fn()
        .mockResolvedValue({ nextReviewOn: '2026-10-16', streak: { currentStreak: 1 } }),
    });
    await comProvedores(
      <PortaoConta>
        <TelaNo codigo="M1.1" />
        <TelaConta />
      </PortaoConta>,
      { api, notificador: notificadorIndisponivel, agora },
    );

    await fireEvent.press(await screen.findByRole('button', { name: 'Treinei isso hoje' }));

    expect(await screen.findByTestId('drill-resultado')).toBeOnTheScreen();
    expect(
      await screen.findByText(/o Expo Go não traz o módulo de notificações/),
    ).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: 'Ativar lembretes' })).toBeNull();
    expect(api.getTree).not.toHaveBeenCalled();
  });

  it('desligar na conta cancela tudo; o horário novo reagenda e fica guardado', async () => {
    const notificador = notificadorFalso('concedida');
    const preferencias = preferenciasFalsas();
    const api = apiFalsa({ getTree: jest.fn().mockResolvedValue(arvoreCom('2026-10-05')) });
    await comProvedores(
      <PortaoConta>
        <TelaConta />
      </PortaoConta>,
      { api, notificador, preferencias, agora },
    );

    await waitFor(() => expect(notificador.agendados()).toHaveLength(7));
    expect(await screen.findByLabelText('Horário do lembrete')).toHaveTextContent('19:00');

    await fireEvent.press(screen.getByRole('button', { name: '+' }));
    await waitFor(() =>
      expect(notificador.agendados()[0]?.quando).toEqual(new Date(2026, 9, 6, 19, 30)),
    );
    expect(preferencias.atual()).toEqual({ ligado: true, hora: 19, minuto: 30 });

    await fireEvent(screen.getByLabelText('Lembrete de revisão'), 'valueChange', false);
    await waitFor(() => expect(notificador.agendados()).toEqual([]));
    expect(preferencias.atual().ligado).toBe(false);
  });

  it('ao sair da conta, o que estava agendado é cancelado', async () => {
    const notificador = notificadorFalso('concedida');
    const api = apiFalsa({ getTree: jest.fn().mockResolvedValue(arvoreCom('2026-10-05')) });
    const tela = await comProvedores(<Text>app</Text>, { api, notificador, agora });
    await waitFor(() => expect(notificador.agendados()).toHaveLength(7));

    await tela.unmount();

    await waitFor(() => expect(notificador.agendados()).toEqual([]));
  });
});
