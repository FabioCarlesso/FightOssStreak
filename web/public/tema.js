/*
 * Aplica o tema escolhido na tela Conta antes do bundle (D74, #187), para a página não abrir escura
 * e virar clara um instante depois.
 *
 * Arquivo à parte, e não <script> inline no index.html, porque a CSP do nginx é `script-src 'self'`
 * e não deve ser afrouxada por isso. É carregado de forma síncrona no <head>: bloqueia a pintura só
 * o tempo de ler uma chave. A chave e os valores são os de `src/state/tema.ts` — há teste que confere.
 */
(function () {
  try {
    var escolha = window.localStorage.getItem('fos.tema');
    if (escolha === 'escuro') document.documentElement.setAttribute('data-theme', 'dark');
    else if (escolha === 'claro') document.documentElement.setAttribute('data-theme', 'light');
  } catch {
    // Sem armazenamento, o tema segue o sistema.
  }
})();
