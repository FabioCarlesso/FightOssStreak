import { act, fireEvent, screen } from '@testing-library/react-native';
import { Text } from 'react-native';

import { semSessao } from '../state/avisos';
import { CONTA, apiFalsa, cofreFalso, comProvedores, erroDaApi } from '../test/fakes';
import { PortaoAviso, PortaoConta, PortaoVersao } from './Portoes';

/** O app inteiro atrás dos portões, com um marcador no lugar das abas. */
function App({ versao = '1.0.0' }: { versao?: string }) {
  return (
    <PortaoVersao versaoAtual={versao}>
      <PortaoConta>
        <PortaoAviso>
          <Text>dentro do app</Text>
        </PortaoAviso>
      </PortaoConta>
    </PortaoVersao>
  );
}

describe('portões do app', () => {
  it('sem token, mostra o login; entrar guarda o token e abre o app', async () => {
    const api = apiFalsa();
    const tokens = cofreFalso(null);
    await comProvedores(<App />, { api, tokens });

    await fireEvent.changeText(await screen.findByLabelText('E-mail'), ' aluno@teste.local ');
    await fireEvent.changeText(screen.getByLabelText('Senha'), 'senha-de-teste-local');
    await fireEvent.press(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('dentro do app')).toBeOnTheScreen();
    expect(api.mobileLoginWithPassword).toHaveBeenCalledWith(
      'aluno@teste.local',
      'senha-de-teste-local',
    );
    expect(tokens.atual()).toBe('token-novo');
  });

  it('senha errada mostra a mensagem da API e continua no login', async () => {
    const api = apiFalsa({
      mobileLoginWithPassword: jest
        .fn()
        .mockRejectedValue(erroDaApi(401, 'credencial_invalida', 'E-mail ou senha não conferem.')),
    });
    await comProvedores(<App />, { api, tokens: cofreFalso(null) });

    await fireEvent.changeText(await screen.findByLabelText('E-mail'), 'aluno@teste.local');
    await fireEvent.changeText(screen.getByLabelText('Senha'), 'errada-errada');
    await fireEvent.press(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('E-mail ou senha não conferem.')).toBeOnTheScreen();
    expect(screen.queryByText('dentro do app')).toBeNull();
  });

  it('token morto no aparelho volta ao login e é apagado', async () => {
    const api = apiFalsa({
      getAccount: jest.fn().mockRejectedValue(erroDaApi(401, 'token_invalido', 'Entre de novo.')),
    });
    const tokens = cofreFalso('vencido');
    await comProvedores(<App />, { api, tokens });

    expect(await screen.findByLabelText('E-mail')).toBeOnTheScreen();
    expect(tokens.atual()).toBeNull();
  });

  it('401 no meio do uso devolve ao login', async () => {
    const tokens = cofreFalso();
    await comProvedores(<App />, { tokens });
    expect(await screen.findByText('dentro do app')).toBeOnTheScreen();

    await act(() => {
      semSessao.avisar();
      return Promise.resolve();
    });

    expect(await screen.findByLabelText('E-mail')).toBeOnTheScreen();
    expect(tokens.atual()).toBeNull();
  });

  it('conta bloqueada vê o motivo, e não o login', async () => {
    const api = apiFalsa({
      getAccount: jest.fn().mockResolvedValue({ ...CONTA, accessStatus: 'RECUSADO' }),
    });
    await comProvedores(<App />, { api });

    expect(await screen.findByText('Seu acesso está bloqueado')).toBeOnTheScreen();
    expect(screen.queryByLabelText('E-mail')).toBeNull();
    expect(screen.getByRole('button', { name: 'Excluir minha conta' })).toBeOnTheScreen();
  });

  it('app abaixo da versão mínima mostra a tela de atualização', async () => {
    const api = apiFalsa({
      getAppVersion: jest.fn().mockResolvedValue({ minimumVersion: '1.10.0' }),
    });
    await comProvedores(<App versao="1.9.3" />, { api });

    expect(await screen.findByText('Atualize o app')).toBeOnTheScreen();
    expect(screen.queryByText('dentro do app')).toBeNull();
  });

  it('aviso não aceito aparece antes do app, e o aceite vai com a versão vigente', async () => {
    const getDisclaimer = jest
      .fn()
      .mockResolvedValueOnce({ accepted: false, currentVersion: '3' })
      .mockResolvedValue({ accepted: true, currentVersion: '3' });
    const api = apiFalsa({ getDisclaimer });
    await comProvedores(<App />, { api });

    expect(await screen.findByText('Aviso importante — leia antes de usar')).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: 'Li e concordo' }));

    expect(await screen.findByText('dentro do app')).toBeOnTheScreen();
    expect(api.acceptDisclaimer).toHaveBeenCalledWith('3');
  });
});
