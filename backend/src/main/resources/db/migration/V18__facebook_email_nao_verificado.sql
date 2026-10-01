-- E-mail de provedor só é verificado quando o provedor afirma (D63, FOS-03).
--
-- SQL portável (ANSI) pelo mesmo motivo da V1: as mesmas migrations rodam em Postgres e em H2
-- no modo de compatibilidade PostgreSQL.
--
-- Até aqui o `ProviderLogin` gravava `email_verified = TRUE` quando o provedor não mandava o
-- atributo, que é o caso do Facebook. O código novo grava FALSE, mas só no próximo login de cada
-- identidade — e a semente de administração (`AccountService.seedAdmins`) lê a coluna na subida,
-- antes de qualquer login. Sem esta migration, uma identidade Facebook antiga continuaria dando
-- direito a ADMIN pela `fos.auth.owner-emails`.

-- 1) As identidades Facebook deixam de contar como verificadas.
UPDATE user_identity
   SET email_verified = FALSE
 WHERE provider = 'facebook';

-- 2) A conta que só era dona do endereço por causa do Facebook deixa de ser.
--
-- `primary_email` é o que faz uma identidade NOVA se anexar à conta (D47): mantido, ele deixaria
-- uma conta Facebook dona do endereço, e o próximo login pelo Google ou cadastro por senha com
-- aquele e-mail cairia dentro dela — o mesmo vínculo que a correção veio impedir, pela outra
-- ponta. Fica o endereço que alguma identidade ainda verificada da própria conta sustenta
-- (Google, ou a senha confirmada pelo app); o resto vira NULL, e a conta segue funcionando sem
-- vínculo, como as contas sem e-mail verificado sempre funcionaram.
--
-- O papel não é tocado: a semente promove e nunca rebaixa (D49), e rebaixar é ação de quem
-- administra, pela tela de Usuários.
UPDATE app_user
   SET primary_email = NULL
 WHERE primary_email IS NOT NULL
   AND EXISTS (
        SELECT 1
          FROM user_identity f
         WHERE f.user_id = app_user.id
           AND f.provider = 'facebook')
   AND NOT EXISTS (
        SELECT 1
          FROM user_identity i
         WHERE i.user_id = app_user.id
           AND i.email_verified = TRUE
           AND LOWER(i.email) = app_user.primary_email);
