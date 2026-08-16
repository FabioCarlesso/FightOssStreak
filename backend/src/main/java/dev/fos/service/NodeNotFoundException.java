package dev.fos.service;

/** Nó inexistente no currículo. Vira 404 em {@link dev.fos.web.ApiExceptionHandler}. */
public class NodeNotFoundException extends RuntimeException {

    public NodeNotFoundException(String code) {
        super("Nó não encontrado: " + code);
    }
}
