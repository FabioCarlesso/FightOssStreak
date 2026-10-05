const path = require('node:path');

/**
 * O `jest-expo` importa `expo-modules-core` sem declará-lo (o próprio pacote marca isso como
 * "invalid dependency chain"). Num projeto sozinho o npm sobe o pacote para `node_modules` e o
 * import funciona por acaso. Aqui não sobe: o React 18 do `web/` ocupa a raiz, o `expo-modules-core`
 * tem o React como peer, e o npm o aninha em `expo/node_modules`, onde o Jest não procura.
 *
 * O mapeamento aponta para a cópia que o próprio `expo` resolve, em vez de instalar o pacote
 * direto (o `expo-doctor` reprova, e a versão deixaria de seguir a do `expo` numa troca de SDK).
 * O Metro não precisa disto: a resolução do Expo já acha o pacote, e o bundle do app sai sem
 * configuração nenhuma (`npx expo export`).
 */
const expoModulesCore = path.dirname(
  require.resolve('expo-modules-core/package.json', {
    paths: [path.dirname(require.resolve('expo/package.json'))],
  }),
);

/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-expo',
  moduleNameMapper: {
    '^expo-modules-core$': expoModulesCore,
    '^expo-modules-core/(.*)$': `${expoModulesCore}/$1`,
  },
};
