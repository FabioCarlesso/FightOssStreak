import { fireEvent, screen } from '@testing-library/react-native';
import { Text } from 'react-native';

import { PortaoConta } from '../components/Portoes';
import { apiFalsa, cofreFalso, comProvedores } from '../test/fakes';
import { TelaConta } from './TelaConta';

describe('TelaConta', () => {
  it('excluir a conta chama DELETE /api/me, apaga o token e volta ao login', async () => {
    const api = apiFalsa();
    const tokens = cofreFalso();
    await comProvedores(
      <PortaoConta>
        <TelaConta />
        <Text>abas</Text>
      </PortaoConta>,
      { api, tokens },
    );

    await fireEvent.press(await screen.findByRole('button', { name: 'Excluir minha conta' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Sim, excluir tudo' }));

    expect(await screen.findByLabelText('E-mail')).toBeOnTheScreen();
    expect(api.deleteAccount).toHaveBeenCalledTimes(1);
    expect(tokens.atual()).toBeNull();
  });

  it('sair revoga o token e volta ao login', async () => {
    const api = apiFalsa();
    const tokens = cofreFalso();
    await comProvedores(
      <PortaoConta>
        <TelaConta />
      </PortaoConta>,
      { api, tokens },
    );

    await fireEvent.press(await screen.findByRole('button', { name: 'Sair' }));

    expect(await screen.findByLabelText('E-mail')).toBeOnTheScreen();
    expect(api.mobileLogout).toHaveBeenCalledTimes(1);
    expect(tokens.atual()).toBeNull();
  });
});
