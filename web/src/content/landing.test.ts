import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { NUMEROS } from './landing.ts';

/**
 * Os números da landing conferem com o currículo versionado.
 *
 * Copy com número envelhece em silêncio: escrever o quiz de M4 não faz ninguém lembrar de trocar
 * "91 perguntas" na página, e aí a única tela pública do projeto passa a mentir sobre ele. O
 * currículo é a fonte (D11), então é dele que a conferência sai.
 */
const CURRICULUM = acharCurriculo();

/**
 * Sobe a partir do diretório de trabalho até achar o currículo.
 *
 * Caminho relativo fixo dependeria de quem chamou o Vitest (a raiz do monorepo ou o workspace
 * `web/`), e `import.meta.url` não resolve para `file:` no ambiente jsdom.
 */
function acharCurriculo(): string {
  let dir = process.cwd();
  for (let i = 0; i < 4; i++) {
    const alvo = join(dir, 'backend/src/main/resources/curriculum');
    if (existsSync(alvo)) return alvo;
    dir = dirname(dir);
  }
  throw new Error('currículo não encontrado a partir de ' + process.cwd());
}

interface QuizItem {
  readonly prompt: string;
}

interface NodeItem {
  readonly code: string;
  readonly video?: { readonly youtubeId?: string };
  readonly quiz?: readonly QuizItem[];
  readonly extraVideos?: readonly unknown[];
}

interface ModuleFile {
  readonly code: string;
  readonly nodes: readonly NodeItem[];
}

function lerModulos(): ModuleFile[] {
  const index = JSON.parse(readFileSync(join(CURRICULUM, 'modules.json'), 'utf8')) as {
    modules: { file: string }[];
  };
  return index.modules.map(
    (m) => JSON.parse(readFileSync(join(CURRICULUM, m.file), 'utf8')) as ModuleFile,
  );
}

const modulos = lerModulos();
const nos = modulos.flatMap((m) => m.nodes);

describe('números citados na landing', () => {
  it('módulos e nós', () => {
    expect(NUMEROS.modules).toBe(modulos.length);
    expect(NUMEROS.nodes).toBe(nos.length);
  });

  it('perguntas de quiz e nós cobertos', () => {
    const comQuiz = nos.filter((no) => (no.quiz?.length ?? 0) > 0);
    expect(NUMEROS.quizNodes).toBe(comQuiz.length);
    expect(NUMEROS.quizQuestions).toBe(
      comQuiz.reduce((total, no) => total + (no.quiz?.length ?? 0), 0),
    );

    // "M0 a M8" é afirmação sobre quais módulos têm quiz, não sobre quantos: se um M9 nascesse
    // sem quiz, o texto passaria a estar errado mesmo com a contagem batendo.
    const modulosComQuiz = modulos
      .filter((m) => m.nodes.some((no) => (no.quiz?.length ?? 0) > 0))
      .map((m) => m.code);
    expect(modulosComQuiz).toEqual(['M0', 'M1', 'M2', 'M3', 'M4', 'M5', 'M6', 'M7', 'M8']);
    expect(NUMEROS.quizModules).toBe('M0 a M8');
  });

  it('vídeos canônicos e clipes complementares', () => {
    const comVideo = nos.filter((no) => Boolean(no.video?.youtubeId));
    expect(NUMEROS.canonicalVideos).toBe(comVideo.length);

    const modulosComVideo = modulos
      .filter((m) => m.nodes.some((no) => Boolean(no.video?.youtubeId)))
      .map((m) => m.code);
    expect(modulosComVideo).toEqual(['M0', 'M1']);
    expect(NUMEROS.videoModules).toBe('M0 e M1');

    expect(NUMEROS.extraClips).toBe(
      nos.reduce((total, no) => total + (no.extraVideos?.length ?? 0), 0),
    );
  });
});
