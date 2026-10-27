import { fireEvent, screen } from '@testing-library/react-native';

import { comProvedores, temaFalso } from '../test/fakes';
import { AtalhoDeTema } from './AtalhoDeTema';

/** O atalho do cabeçalho (D74): troca claro/escuro com um toque e guarda a escolha no aparelho. */
describe('atalho de tema', () => {
  it('no escuro, oferece o claro; tocar troca e guarda', async () => {
    const tema = temaFalso('escuro');
    await comProvedores(<AtalhoDeTema />, { tema });

    await fireEvent.press(await screen.findByRole('button', { name: 'Usar tema claro' }));

    expect(tema.set).toHaveBeenCalledWith('claro');
    expect(screen.getByRole('button', { name: 'Usar tema escuro' })).toBeTruthy();
  });

  it('no claro, oferece o escuro', async () => {
    await comProvedores(<AtalhoDeTema />, { tema: temaFalso('claro') });

    expect(await screen.findByRole('button', { name: 'Usar tema escuro' })).toBeTruthy();
  });
});
