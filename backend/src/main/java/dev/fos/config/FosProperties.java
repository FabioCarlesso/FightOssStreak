package dev.fos.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da aplicação sob o prefixo {@code fos}.
 *
 * @param disclaimerVersion versão vigente do texto de disclaimer; subir força novo aceite
 * @param curriculum origem do currículo versionado
 * @param auth donos do app e credenciais de provedor de login (#24)
 * @param email envio de e-mail para a entrada por link (#52)
 * @param publicUrl URL pública do app, sem barra final. Só o resumo da fila (#54) precisa dela:
 *     e-mail disparado por agendador não tem requisição de onde deduzir o endereço, ao contrário do
 *     link de entrada. Opcional — sem ela o resumo sai sem link, e nada mais muda
 */
@ConfigurationProperties(prefix = "fos")
public record FosProperties(
        String disclaimerVersion, Curriculum curriculum, Auth auth, Email email, String publicUrl) {

    public FosProperties {
        publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
        if (auth == null) {
            auth = new Auth(null, null);
        }
        if (email == null) {
            email = new Email(null, null);
        }
    }

    /**
     * Envio de e-mail — a credencial que faz a entrada por link existir.
     *
     * <p>Opcional pelo mesmo motivo dos provedores de login: sem ela a aplicação sobe igual, a
     * entrada por e-mail não é oferecida na tela, e dev e CI rodam sem segredo nenhum.
     *
     * @param apiKey chave do provedor de envio; nunca versionada
     * @param from remetente, em domínio verificado no provedor de envio
     */
    public record Email(String apiKey, String from) {
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank() && from != null && !from.isBlank();
        }
    }

    /**
     * @param index classpath do índice de módulos
     * @param syncOnStartup reingerir o currículo na subida da aplicação
     */
    public record Curriculum(String index, boolean syncOnStartup) {
        public Curriculum {
            if (index == null || index.isBlank()) {
                index = "classpath:curriculum/modules.json";
            }
        }
    }

    /**
     * @param ownerEmails e-mails que entram já aprovados e podem decidir a fila de solicitações.
     *     Vazia por default: sem ela ninguém é aprovado automaticamente
     * @param providers credenciais por provedor de login. Provedor sem credencial não é registrado
     *     e não aparece na tela de login — a aplicação sobe em dev sem nenhum segredo
     */
    public record Auth(List<String> ownerEmails, Providers providers) {
        public Auth {
            ownerEmails =
                    ownerEmails == null
                            ? List.of()
                            : ownerEmails.stream()
                                    .filter(email -> email != null && !email.isBlank())
                                    .map(email -> email.trim().toLowerCase(Locale.ROOT))
                                    .toList();
            providers = providers == null ? new Providers(null, null, null) : providers;
        }

        /** E-mail verificado do dono — a comparação é sempre em minúsculas. */
        public boolean isOwnerEmail(String email) {
            return email != null && ownerEmails.contains(email.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Um campo por provedor suportado, e não um {@code Map}.
     *
     * <p>Mapa parece mais elegante e é armadilha aqui: as credenciais chegam por variável de
     * ambiente, e para montar um mapa o Binder precisa *enumerar* as chaves — o que a fonte de
     * variáveis de ambiente não expõe de forma confiável para propriedade aninhada. Com campo fixo
     * a leitura é direta, e a lista de provedores suportados já é fechada de qualquer jeito (ver
     * {@code OAuth2ProviderConfig}).
     */
    public record Providers(Provider google, Provider facebook, Provider apple) {
        public Providers {
            google = google == null ? new Provider(null, null) : google;
            facebook = facebook == null ? new Provider(null, null) : facebook;
            apple = apple == null ? new Provider(null, null) : apple;
        }

        /**
         * Só os que têm credencial. É esta lista que vira registration e botão na tela.
         *
         * <p>A Apple entra aqui de propósito, mesmo ainda não sendo suportada: quem preencher as
         * credenciais dela recebe uma falha explícita na subida, dizendo isso. Deixá-la fora daria
         * o pior dos dois mundos — configuração aceita em silêncio e login que nunca aparece.
         */
        public Map<String, Provider> configured() {
            Map<String, Provider> configured = new LinkedHashMap<>();
            if (google.isConfigured()) {
                configured.put("google", google);
            }
            if (facebook.isConfigured()) {
                configured.put("facebook", facebook);
            }
            if (apple.isConfigured()) {
                configured.put("apple", apple);
            }
            return configured;
        }
    }

    /**
     * @param clientId identificador do app no provedor
     * @param clientSecret segredo do app no provedor; nunca versionado
     */
    public record Provider(String clientId, String clientSecret) {
        public boolean isConfigured() {
            return clientId != null
                    && !clientId.isBlank()
                    && clientSecret != null
                    && !clientSecret.isBlank();
        }
    }
}
