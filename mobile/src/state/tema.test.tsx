import { fireEvent, screen } from '@testing-library/react-native';

import { PortaoConta } from '../components/Portoes';
import { TelaConta } from '../screens/TelaConta';
import { comProvedores, temaFalso } from '../test/fakes';
import { paletas } from '../theme';
import { resolverTema } from './tema';

/**
 * O tema do app (D74, #187): segue o aparelho até a pessoa escolher, troca na hora e a escolha
 * fica guardada no aparelho.
 */
describe('resolverTema', () => {
  it('em Sistema, segue o aparelho; sem resposta dele, vale o escuro', () => {
    expect(resolverTema('sistema', 'light')).toBe('claro');
    expect(resolverTema('sistema', 'dark')).toBe('escuro');
    expect(resolverTema('sistema', null)).toBe('escuro');
    expect(resolverTema('sistema', undefined)).toBe('escuro');
  });

  it('a escolha manual vence o aparelho', () => {
    expect(resolverTema('escuro', 'light')).toBe('escuro');
    expect(resolverTema('claro', 'dark')).toBe('claro');
  });
});

describe('escolha de tema na tela Conta', () => {
  async function abrir(tema = temaFalso()) {
    await comProvedores(
      <PortaoConta>
        <TelaConta />
      </PortaoConta>,
      { tema },
    );
    await screen.findByText('Aparência');
    return tema;
  }

  it('escolher Claro troca as cores na hora e guarda no aparelho', async () => {
    const tema = await abrir();

    await fireEvent.press(screen.getByRole('radio', { name: 'Claro' }));

    expect(screen.getByRole('radio', { name: 'Claro' })).toBeChecked();
    expect(screen.getByText('Aparência')).toHaveStyle({ color: paletas.claro.texto });
    expect(tema.set).toHaveBeenCalledWith('claro');
    expect(tema.atual()).toBe('claro');
  });

  it('escolher Escuro usa as cores do escuro', async () => {
    await abrir();

    await fireEvent.press(screen.getByRole('radio', { name: 'Escuro' }));

    expect(screen.getByText('Aparência')).toHaveStyle({ color: paletas.escuro.texto });
  });

  it('num novo acesso, a escolha guardada volta marcada e aplicada', async () => {
    await abrir(temaFalso('claro'));

    expect(await screen.findByRole('radio', { name: 'Claro', checked: true })).toBeOnTheScreen();
    expect(screen.getByText('Aparência')).toHaveStyle({ color: paletas.claro.texto });
  });
});
