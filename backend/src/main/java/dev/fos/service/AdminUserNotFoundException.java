package dev.fos.service;

/**
 * Conta pedida por id que não existe. Vira 404 em {@link dev.fos.web.ApiExceptionHandler}.
 *
 * <p>Conta de demonstração <b>não</b> cai aqui: ela existe, e recusá-la com 404 esconderia de quem
 * administra a diferença entre "essa conta não existe" e "essa conta não se administra" (D39).
 */
public class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException(Long id) {
        super("Conta " + id + " não encontrada.");
    }
}
