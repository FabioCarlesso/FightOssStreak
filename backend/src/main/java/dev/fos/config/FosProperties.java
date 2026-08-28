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
 * @param auth administradores do app e credenciais de provedor de login
 * @param email envio de e-mail: confirmação de cadastro e redefinição de senha (D47)
 * @param demo conta-modelo do acesso demonstrativo (#62)
 * @param usage coleta de uso do app (#84, D50)
 */
@ConfigurationProperties(prefix = "fos")
public record FosProperties(
        String disclaimerVersion,
        Curriculum curriculum,
        Auth auth,
        Email email,
        Demo demo,
        Usage usage) {

    public FosProperties {
        if (auth == null) {
            auth = new Auth(null, null);
        }
        if (demo == null) {
            demo = new Demo(null);
        }
        if (email == null) {
            email = new Email(null, null);
        }
        if (usage == null) {
            usage = new Usage(true, null, 0, 0);
        }
    }

    /**
     * Coleta de uso (#84, D50).
     *
     * <p>Nenhuma delas é segredo, e nenhuma precisa estar preenchida: sem base de geolocalização o
     * app coleta tudo menos país, que é como dev e CI rodam. É o mesmo desenho do provedor de login
     * e do envio de e-mail — funcionalidade que falta se anuncia como ausente, não como erro.
     *
     * @param enabled desligar para de gravar evento; a aplicação segue igual em todo o resto. É a
     *     saída para quem sobe este código e não quer coleta nenhuma
     * @param geoipDatabase caminho do CSV de faixas de IP (DB-IP Lite ou equivalente). Vazio = país
     *     desconhecido para todo mundo
     * @param retentionDays dias de retenção da tabela crua. Zero ou negativo usa o default de 90 —
     *     o agregado, que não tem dado pessoal, não expira
     * @param dailyCap teto de acessos gravados por dia. Zero ou negativo usa o default. É o único
     *     limite da coleta que não depende de nada que o cliente possa escolher — ver {@code
     *     UsageCollector}
     */
    public record Usage(boolean enabled, String geoipDatabase, int retentionDays, int dailyCap) {

        /** Os 90 dias da D50. Mudar aqui muda a promessa escrita em docs/11-privacidade.md. */
        public static final int DEFAULT_RETENTION_DAYS = 90;

        /**
         * Teto diário de acessos gravados.
         *
         * <p>Folgado o bastante para não moldar a métrica, e apertado o bastante para o pior caso
         * caber num plano barato. A conta que define o número: <b>uma linha de {@code usage_event}
         * custa 273 bytes medidos</b> (tabela + os três índices), então 5 000/dia × 90 dias de
         * retenção ≈ 450 mil linhas ≈ <b>123 MB</b> no teto. Com 50 000, o valor anterior, o mesmo
         * teto dava 1,2 GB — grande demais para o que este app é.
         *
         * <p>E o que se paga é a <b>marca d'água</b>, não a contagem de hoje: o expurgo apaga as
         * linhas, mas o Postgres só devolve o espaço ao sistema com {@code VACUUM FULL}. Um único
         * dia de abuso fixa disco que os 90 dias não recuperam sozinhos — é por isso que o teto
         * importa mesmo num app de tráfego pequeno.
         */
        public static final int DEFAULT_DAILY_CAP = 5_000;

        public Usage {
            geoipDatabase = geoipDatabase == null ? "" : geoipDatabase.trim();
            retentionDays = retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
            dailyCap = dailyCap > 0 ? dailyCap : DEFAULT_DAILY_CAP;
        }
    }

    /**
     * Envio de e-mail — a credencial que faz o cadastro por senha existir (D47).
     *
     * <p>Opcional pelo mesmo motivo dos provedores de login: sem ela a aplicação sobe igual, o
     * cadastro não é oferecido na tela, e dev e CI rodam sem segredo nenhum. É a única credencial
     * cuja ausência tira uma <em>porta de entrada</em> inteira, e não só um botão: o cadastro
     * <em>é</em> o e-mail de confirmação.
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
     * A conta-modelo do acesso demonstrativo (#62).
     *
     * <p>Opcional pela mesma regra dos provedores de login e do envio de e-mail: sem ela a
     * aplicação sobe igual, o botão não aparece na landing e o endpoint responde que a demonstração
     * não existe neste ambiente.
     *
     * <p>É um <b>e-mail</b>, e não um id de conta, porque quem cura a demonstração cura pelo app —
     * entra com a própria conta, conclui nós, registra drills, escreve as anotações — e um id de
     * linha do banco não é coisa que se configure em variável de ambiente sem consultar o banco.
     *
     * @param templateEmail e-mail verificado da conta que serve de molde. Vazio desliga o recurso
     */
    public record Demo(String templateEmail) {
        public Demo {
            templateEmail =
                    templateEmail == null ? "" : templateEmail.trim().toLowerCase(Locale.ROOT);
        }

        public boolean isConfigured() {
            return !templateEmail.isBlank();
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
     * @param ownerEmails e-mails das contas de administração (D48). Exige e-mail <b>verificado</b>
     *     — pelo provedor externo ou pela confirmação do próprio app. Vazia por default: sem ela
     *     ninguém administra nada, e o app funciona igual para todo mundo
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
