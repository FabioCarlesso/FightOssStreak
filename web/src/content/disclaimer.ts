/**
 * Texto do aviso completo, exibido no primeiro uso com aceite obrigatório.
 *
 * Cópia fiel de `docs/06-disclaimer-responsabilidade.md`. Ao alterar materialmente este texto,
 * suba `fos.disclaimer-version` no backend — é o que força a reexibição do aceite.
 */
export const FULL_DISCLAIMER: readonly string[] = [
  'O FightOssStreak é uma ferramenta de organização e revisão de estudos, destinada a complementar o treino presencial de jiu-jitsu brasileiro em academia, sob supervisão de professor qualificado.',
  'Este aplicativo não ensina jiu-jitsu e não substitui instrução presencial. O conteúdo aqui é de caráter estritamente informativo e instrucional de apoio. Jiu-jitsu é uma atividade física de contato que envolve técnicas de imobilização, torção articular e estrangulamento, com risco real de lesão grave.',
  'Nunca pratique as técnicas referenciadas neste aplicativo sem supervisão de um professor qualificado, fora de um ambiente adequado de treino, com parceiro que não tenha consentido e não conheça os riscos, ou sem aquecimento e condicionamento adequados.',
  'Técnicas de estrangulamento podem causar perda de consciência, lesão neurológica ou morte. Técnicas de torção articular podem causar lesão permanente. Sempre respeite o toque (tap) do parceiro imediatamente.',
  'Consulte um médico antes de iniciar qualquer atividade física, especialmente se você tem condição pré-existente ou histórico de lesão.',
  'O autor e os colaboradores deste aplicativo não se responsabilizam por qualquer lesão, dano ou prejuízo decorrente do uso das informações aqui contidas. Ao usar este aplicativo, você reconhece que assume integralmente os riscos da prática.',
  'Os vídeos referenciados são conteúdo de terceiros, incorporados a partir do YouTube. Não somos autores desse conteúdo e não temos vínculo com seus criadores.',
];

/**
 * Aviso curto, o mesmo do rodapé de nó em `docs/06-disclaimer-responsabilidade.md`.
 *
 * Aparece na landing, que é pública e não passa pelo portão de aceite: quem chega pelo link precisa
 * ler o limite antes de entrar, mesmo que nunca clique em "Abrir o app". Mora aqui junto do texto
 * completo de propósito — mudou um, revisar o outro, e mudança material continua exigindo subir
 * `fos.disclaimer-version` no backend.
 */
export const SHORT_DISCLAIMER =
  'Conteúdo de apoio ao estudo. Não substitui a instrução do seu professor. Pratique somente em academia, com supervisão e parceiro consciente. Respeite o tap sempre.';
