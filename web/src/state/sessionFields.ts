// O estado do formulário de sessão mora em `@fos/domain` desde a #141, para o web e o app o
// converterem do mesmo jeito.
export { emptySessionFields, toSessionBody } from '@fos/domain';
export type { SessionFieldsState } from '@fos/domain';
