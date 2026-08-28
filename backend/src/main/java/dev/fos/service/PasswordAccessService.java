package dev.fos.service;

import dev.fos.email.EmailSender;
import dev.fos.model.AppUser;
import dev.fos.model.LoginToken;
import dev.fos.model.LoginTokenPurpose;
import dev.fos.model.PasswordCredential;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.LoginTokenRepository;
import dev.fos.repo.PasswordCredentialRepository;
import dev.fos.repo.UserIdentityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro, entrada e recuperação com senha própria (#81, D47).
 *
 * <p><b>O que muda em relação à D37.</b> Ela recusou senha de propósito, e o argumento era bom
 * enquanto o app era de uso pessoal: sem senha não há hash para vazar, recuperação para sequestrar
 * nem força bruta para frear. O que mudou não foi o argumento, foi o app — a fila de aprovação
 * virou o gargalo de um produto que quer usuários, e o magic link de 15 minutos é curto demais para
 * um link que chega por e-mail e é aberto no celular. A D47 aceita os três riscos com nome e
 * sobrenome, e é este arquivo que os endereça.
 *
 * <p><b>A ordem dos passos é a decisão, de novo.</b> O cadastro <b>não abre sessão</b>: cria a
 * conta e manda o link. Só a confirmação autentica. Sem isso, quem digitasse o endereço de outra
 * pessoa entraria no app antes de qualquer prova de que a caixa é dele — e a conta ficaria
 * pendurada no endereço alheio.
 *
 * <p><b>Três rotas respondem igual para e-mail que existe e que não existe</b> (cadastro, reenvio e
 * recuperação). É a mesma razão da D37 aplicada em triplicata: qualquer diferença observável
 * transforma um endpoint público em consulta de "quem tem conta neste app". Daí também o hash ser
 * calculado mesmo quando ele vai ser jogado fora — sem isso, o caminho "e-mail já existe" voltaria
 * numa fração do tempo do outro, e o cronômetro responderia a pergunta que o corpo se recusa a
 * responder.
 */
@Service
public class PasswordAccessService {

    private static final Logger log = LoggerFactory.getLogger(PasswordAccessService.class);

    /**
     * Prazo do link de confirmação.
     *
     * <p>24 horas, e não os 15 minutos do magic link da #52: aquele prazo é o de uma credencial de
     * entrada, e este é o de um e-mail que precisa sobreviver a chegar de madrugada, cair na caixa
     * de promoções e ser aberto no dia seguinte. Foi essa confusão entre os dois papéis que a #81
     * veio desfazer.
     */
    static final Duration VALIDADE_VERIFICACAO = Duration.ofHours(24);

    /**
     * Prazo do link de redefinição.
     *
     * <p>Curto porque este link <em>troca a credencial</em>: quem o tiver vira dono da conta. Uma
     * hora é o que separa "cheguei ao e-mail e cliquei" de "este link ficou meses na caixa de
     * entrada de alguém".
     */
    static final Duration VALIDADE_REDEFINICAO = Duration.ofHours(1);

    /** Tentativas erradas toleradas por janela, para o mesmo e-mail. */
    static final int MAX_TENTATIVAS_EMAIL = 5;

    /**
     * E por IP.
     *
     * <p>Mais folgado que o de e-mail porque um IP legítimo é compartilhado — uma academia inteira
     * atrás do mesmo NAT erra senha mais que uma pessoa. O endereço vem do {@code ClientIp} (#77):
     * com {@code getRemoteAddr()} este recorte não valia nada atrás do nginx, porque quem tentava
     * escolhia a própria chave. O freio por e-mail é o que segura o ataque dirigido, e ele não
     * depende disso.
     */
    static final int MAX_TENTATIVAS_IP = 30;

    static final Duration JANELA_TENTATIVAS = Duration.ofMinutes(15);

    /** Teto do nome, alinhado ao da coluna {@code user_identity.display_name}. */
    private static final int MAX_NOME = 200;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Hash descartável, para conferir senha contra e-mail inexistente.
     *
     * <p>Sem ele, "e-mail não existe" volta na hora e "senha errada" volta depois do bcrypt — e o
     * cronômetro vira consulta de quem tem conta. O valor é o de uma senha que ninguém tem.
     */
    private static final String HASH_FANTASMA =
            "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserRepository users;
    private final UserIdentityRepository identities;
    private final LoginTokenRepository tokens;
    private final PasswordCredentialRepository credentials;
    private final AccountService accounts;
    private final AccessRateLimiter freio;
    private final PasswordEncoder encoder;
    private final ObjectProvider<EmailSender> emailSender;
    private final Clock clock;

    public PasswordAccessService(
            AppUserRepository users,
            UserIdentityRepository identities,
            LoginTokenRepository tokens,
            PasswordCredentialRepository credentials,
            AccountService accounts,
            AccessRateLimiter freio,
            PasswordEncoder encoder,
            ObjectProvider<EmailSender> emailSender,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.tokens = tokens;
        this.credentials = credentials;
        this.accounts = accounts;
        this.freio = freio;
        this.encoder = encoder;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    /**
     * Sem credencial de envio não há cadastro por senha.
     *
     * <p>Mesma regra dos provedores de login e da entrada por e-mail, e agora ela pesa mais: o
     * cadastro <em>é</em> o e-mail de confirmação, então oferecê-lo sem provedor de envio seria
     * criar contas que ninguém consegue confirmar. Dev e CI continuam subindo sem segredo nenhum —
     * só sem esta porta.
     */
    public boolean isEnabled() {
        return emailSender.getIfAvailable() != null;
    }

    // ------------------------------------------------------------------ cadastro

    /**
     * Cria a conta não verificada e manda o link de confirmação. Não abre sessão.
     *
     * <p>Os três desfechos respondem igual para fora, e cada um faz uma coisa diferente por dentro:
     *
     * <ul>
     *   <li><b>Endereço novo</b>: conta, identidade e credencial nascem juntas; sai o link.
     *   <li><b>Cadastro pendente</b>: a senha <b>não</b> é trocada, e sai um link novo. Trocar
     *       deixaria alguém plantar a própria senha num cadastro alheio ainda não confirmado, e o
     *       dono da caixa confirmaria a conta do invasor ao clicar no link que ele mesmo pediu.
     *       Quem esqueceu a senha antes de confirmar usa a recuperação, que também confirma.
     *   <li><b>Conta já confirmada</b>: nada é criado, e sai um aviso para a caixa do dono. Só ele
     *       vê esse e-mail, então ele não conta nada a quem tentou.
     * </ul>
     *
     * <p>Devolve se uma conta <b>nova</b> nasceu, e isso não afrouxa a indistinção da D37: quem
     * chama responde {@code 202} nos três casos de qualquer jeito. O sinal existe para o evento de
     * funil da coleta de uso (#84, D50) — sem ele, "cadastros criados" contaria também cada
     * tentativa de cadastrar um endereço que já tem conta, que é o número que a issue NÃO pediu.
     */
    @Transactional
    public boolean register(String rawEmail, String rawPassword, String rawNome, String baseUrl) {
        exigirEnvioConfigurado();
        String email = Emails.normalize(rawEmail);
        String nome = nomeOuNulo(rawNome);
        PasswordPolicy.check(rawPassword, email);
        // Sempre, mesmo quando for descartado logo abaixo: ver HASH_FANTASMA.
        String hash = encoder.encode(rawPassword);
        Instant now = Instant.now(clock);

        Optional<UserIdentity> existente =
                identities.findByProviderAndProviderSubject(
                        PasswordAuthenticationToken.PROVIDER, email);
        if (existente.isPresent()) {
            UserIdentity identity = existente.get();
            if (identity.isEmailVerified()) {
                avisarQueJaTemConta(email);
            } else {
                enviarVerificacao(identity.getUserId(), email, baseUrl, now);
            }
            return false;
        }

        // O rótulo é o nome quando ele veio, e o endereço quando não veio: o cabeçalho do app
        // mostra esse valor, e "aluno@example.test" no lugar do nome é o tipo de aspereza que
        // ninguém volta para consertar.
        AppUser user = users.save(AppUser.forPassword(nome != null ? nome : email, now));
        UserIdentity identity =
                identities.save(
                        new UserIdentity(
                                user.getId(),
                                PasswordAuthenticationToken.PROVIDER,
                                email,
                                email,
                                // Falso, e é o ponto inteiro: até aqui ninguém provou ser dono do
                                // endereço — só digitou ele. Enquanto for falso, a conta não tem
                                // sessão, não vincula nada e não vale primary_email.
                                false,
                                nome,
                                now));
        credentials.save(new PasswordCredential(identity.getId(), hash, now));
        enviarVerificacao(user.getId(), email, baseUrl, now);
        log.info("Cadastro por senha registrado — conta {}, à espera de confirmação", user.getId());
        return true;
    }

    /** Outro link de confirmação, para quem não recebeu o primeiro. Responde igual sempre. */
    @Transactional
    public void resendVerification(String rawEmail, String baseUrl) {
        exigirEnvioConfigurado();
        String email = Emails.normalize(rawEmail);
        Instant now = Instant.now(clock);
        identities
                .findByProviderAndProviderSubject(PasswordAuthenticationToken.PROVIDER, email)
                .filter(identity -> !identity.isEmailVerified())
                .ifPresent(
                        identity -> enviarVerificacao(identity.getUserId(), email, baseUrl, now));
    }

    /**
     * Por que um link não serviu.
     *
     * <p>Os três casos existem porque a tela faz coisas diferentes com cada um: <b>vencido</b>
     * oferece reenviar, <b>usado</b> manda entrar (a conta já está confirmada) e <b>inválido</b>
     * não tem o que oferecer. Com um motivo só, a saída certa — "clique em reenviar" — ficaria
     * escondida atrás de um erro genérico.
     *
     * <p>Distinguir não vaza nada de útil: para chegar a qualquer uma destas respostas é preciso
     * apresentar o token, e quem o tem já teve acesso à caixa de entrada.
     */
    public enum FalhaDeLink {
        VENCIDO,
        USADO,
        INVALIDO
    }

    /**
     * Resultado de abrir um link: ou o e-mail confirmado, ou o motivo de não ter dado.
     *
     * @param email endereço autenticado; nulo quando houve falha
     * @param falha motivo; nulo quando deu certo
     */
    public record Confirmacao(String email, FalhaDeLink falha) {
        public boolean ok() {
            return email != null;
        }

        static Confirmacao de(String email) {
            return new Confirmacao(email, null);
        }

        static Confirmacao falhou(FalhaDeLink falha) {
            return new Confirmacao(null, falha);
        }
    }

    /**
     * O link de confirmação ainda vale — sem gastá-lo.
     *
     * <p>É o que a tela consulta ao abrir o link que veio no e-mail, e existe pela mesma razão do
     * {@link #checkResetLink}: <b>quem abre uma URL não é necessariamente quem a recebeu</b>.
     * Antivírus de caixa de entrada e varredores de link corporativos (Safe Links e parentes)
     * seguem toda URL que chega, e um {@code GET} que confirma queimaria o link antes de a pessoa
     * clicar — ela veria "este link já foi usado" sem nunca o ter usado. Confirmar é ato explícito,
     * e ato explícito é {@code POST}.
     *
     * <p>Devolve string vazia no lugar do e-mail, e não o endereço: quem só tem a URL ainda não
     * provou nada, e responder de quem é a conta seria entregar isso a quem varreu o link.
     */
    @Transactional(readOnly = true)
    public Confirmacao checkVerificationLink(String rawToken) {
        Instant now = Instant.now(clock);
        Optional<LoginToken> encontrado =
                tokenComProposito(rawToken, LoginTokenPurpose.VERIFICACAO);
        if (encontrado.isEmpty()) {
            return Confirmacao.falhou(FalhaDeLink.INVALIDO);
        }
        FalhaDeLink falha = motivo(encontrado.get(), now);
        return falha == null ? Confirmacao.de("") : Confirmacao.falhou(falha);
    }

    /**
     * Consome o link de confirmação e devolve o e-mail autenticado.
     *
     * <p>É aqui, e só aqui, que o cadastro vira conta de verdade: o endereço passa a ser
     * verificado, a conta reivindica o {@code primary_email} e a sessão pode ser aberta. O
     * propósito é conferido antes de tudo: link de redefinição apresentado aqui é inválido, não uma
     * confirmação.
     */
    @Transactional
    public Confirmacao verify(String rawToken) {
        Instant now = Instant.now(clock);
        Optional<LoginToken> encontrado =
                tokenComProposito(rawToken, LoginTokenPurpose.VERIFICACAO);
        if (encontrado.isEmpty()) {
            return Confirmacao.falhou(FalhaDeLink.INVALIDO);
        }
        LoginToken token = encontrado.get();
        FalhaDeLink falha = motivo(token, now);
        if (falha != null) {
            return Confirmacao.falhou(falha);
        }
        token.consume(LoginTokenPurpose.VERIFICACAO, now);
        return identidadeDeSenha(token.getUserId())
                .map(identity -> Confirmacao.de(confirmar(identity, now)))
                // Conta sem identidade de senha não tem o que confirmar. Não acontece pelo fluxo
                // normal, e "inválido" é a resposta honesta se acontecer.
                .orElseGet(() -> Confirmacao.falhou(FalhaDeLink.INVALIDO));
    }

    /**
     * O token com este hash, se ele existir <b>e</b> for do propósito pedido.
     *
     * <p>O propósito é conferido aqui, e não em cada chamada: link de 24 horas apresentado na
     * redefinição — ou o contrário — é inválido, não um link do outro tipo.
     */
    private Optional<LoginToken> tokenComProposito(String rawToken, LoginTokenPurpose purpose) {
        return tokens.findByTokenHash(hash(rawToken))
                .filter(token -> token.getPurpose() == purpose);
    }

    /** Nulo quando o token ainda serve. */
    private static FalhaDeLink motivo(LoginToken token, Instant now) {
        if (token.getUsedAt() != null) {
            return FalhaDeLink.USADO;
        }
        if (!now.isBefore(token.getExpiresAt())) {
            return FalhaDeLink.VENCIDO;
        }
        return null;
    }

    // ------------------------------------------------------------------ entrada

    /**
     * Confere a senha e devolve o e-mail autenticado. Quem abre a sessão é o controller.
     *
     * <p>O freio conta <b>tentativa errada</b>, não requisição: contar acerto também derrubaria uma
     * pessoa que entra e sai várias vezes no mesmo dia, sem tornar o ataque mais caro. Acertar zera
     * o contador daquele e-mail.
     *
     * @param ip origem da requisição, para o segundo recorte do freio
     */
    @Transactional
    public String authenticate(String rawEmail, String rawPassword, String ip) {
        String email = Emails.normalize(rawEmail);
        Instant now = Instant.now(clock);
        freio.evictOlderThan(JANELA_TENTATIVAS, now);
        if (freio.isBlocked(chaveEmail(email), MAX_TENTATIVAS_EMAIL, JANELA_TENTATIVAS, now)
                || freio.isBlocked(chaveIp(ip), MAX_TENTATIVAS_IP, JANELA_TENTATIVAS, now)) {
            throw PasswordAccessException.muitasTentativas();
        }

        Optional<UserIdentity> identity =
                identities.findByProviderAndProviderSubject(
                        PasswordAuthenticationToken.PROVIDER, email);
        Optional<PasswordCredential> credential =
                identity.flatMap(i -> credentials.findByIdentityId(i.getId()));
        // Contra o hash fantasma quando não há credencial: o custo do bcrypt precisa ser pago
        // pelos dois caminhos, senão o relógio diz o que a resposta não diz.
        boolean confere =
                encoder.matches(
                        rawPassword,
                        credential.map(PasswordCredential::getPasswordHash).orElse(HASH_FANTASMA));
        if (!confere || credential.isEmpty()) {
            registrarErro(email, ip, now);
            throw PasswordAccessException.credencialInvalida();
        }

        UserIdentity encontrada = identity.orElseThrow();
        if (!encontrada.isEmailVerified()) {
            // Não é 401 genérico: quem chegou aqui acertou a senha, então dizer "confirme o
            // e-mail" não conta nada que a pessoa já não saiba, e é a única saída útil da tela.
            throw PasswordAccessException.emailNaoVerificado();
        }
        // Algoritmo mudou desde o cadastro: rehash agora, com a senha em claro em mãos. É a razão
        // de o hash guardar o prefixo — sem isso a troca viraria "todo mundo redefine a senha".
        if (encoder.upgradeEncoding(credential.get().getPasswordHash())) {
            credential.get().changeTo(encoder.encode(rawPassword), now);
        }
        freio.clear(chaveEmail(email));
        encontrada.registerLogin(now);
        return email;
    }

    // ------------------------------------------------------------------ recuperação

    /** Manda o link de redefinição, se houver conta com senha nesse endereço. Responde igual. */
    @Transactional
    public void requestReset(String rawEmail, String baseUrl) {
        exigirEnvioConfigurado();
        String email = Emails.normalize(rawEmail);
        Instant now = Instant.now(clock);
        identities
                .findByProviderAndProviderSubject(PasswordAuthenticationToken.PROVIDER, email)
                .ifPresent(
                        identity -> enviarRedefinicao(identity.getUserId(), email, baseUrl, now));
    }

    /**
     * O link de redefinição ainda vale — sem gastá-lo.
     *
     * <p>É o que a tela consulta antes de pedir a senha nova. Consumir na abertura queimaria o link
     * em qualquer pré-carregamento do navegador ou do cliente de e-mail. Devolve o motivo pela
     * mesma razão da confirmação: "venceu" e "já foi usado" levam a telas diferentes.
     */
    @Transactional(readOnly = true)
    public Confirmacao checkResetLink(String rawToken) {
        Instant now = Instant.now(clock);
        Optional<LoginToken> encontrado =
                tokenComProposito(rawToken, LoginTokenPurpose.REDEFINICAO);
        if (encontrado.isEmpty()) {
            return Confirmacao.falhou(FalhaDeLink.INVALIDO);
        }
        FalhaDeLink falha = motivo(encontrado.get(), now);
        // String vazia, e não o e-mail: quem consulta o link ainda não provou nada, e devolver o
        // endereço aqui entregaria de quem é a conta a quem só tem a URL.
        return falha == null ? Confirmacao.de("") : Confirmacao.falhou(falha);
    }

    /**
     * Troca a senha e devolve o e-mail da conta.
     *
     * <p>Três consequências, e nenhuma é opcional. <b>Os tokens pendentes queimam</b>: link antigo
     * na caixa de entrada continuaria valendo depois da troca, que é a janela que um invasor com
     * acesso à caixa usaria. <b>As sessões abertas caem</b> — quem chama trata disso com o {@link
     * SessionLogin}, porque trocar a senha sem derrubar sessão não expulsa ninguém. <b>E o e-mail
     * fica verificado</b>: quem abriu este link provou ter a caixa, que é exatamente o que a
     * confirmação prova. É também o que tira do beco quem esqueceu a senha antes de confirmar.
     *
     * <p>Não abre sessão de propósito: a próxima tela é o login, com a senha nova. Abrir sessão
     * aqui criaria a única sessão que a própria troca não derruba.
     */
    @Transactional
    public String resetPassword(String rawToken, String rawPassword) {
        Instant now = Instant.now(clock);
        LoginToken token =
                tokens.findByTokenHash(hash(rawToken))
                        .filter(t -> t.consume(LoginTokenPurpose.REDEFINICAO, now))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Este link de redefinição não vale mais. Peça"
                                                        + " outro."));
        UserIdentity identity =
                identidadeDeSenha(token.getUserId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Esta conta não entra por senha."));
        PasswordPolicy.check(rawPassword, identity.getEmail());
        credentials
                .findByIdentityId(identity.getId())
                .ifPresentOrElse(
                        credential -> credential.changeTo(encoder.encode(rawPassword), now),
                        () ->
                                credentials.save(
                                        new PasswordCredential(
                                                identity.getId(),
                                                encoder.encode(rawPassword),
                                                now)));
        invalidarPendentes(token.getUserId(), now);
        freio.clear(chaveEmail(identity.getEmail()));
        confirmar(identity, now);
        log.info("Senha redefinida — conta {}", token.getUserId());
        return identity.getEmail();
    }

    /**
     * Os {@code provider_subject} de todas as identidades da conta.
     *
     * <p>É o que o {@link SessionLogin} precisa para derrubar sessão sem saber por onde ela entrou.
     */
    @Transactional(readOnly = true)
    public List<String> subjectsOf(String email) {
        return identities
                .findByProviderAndProviderSubject(
                        PasswordAuthenticationToken.PROVIDER, Emails.normalize(email))
                .map(
                        identity ->
                                identities.findByUserId(identity.getUserId()).stream()
                                        .map(UserIdentity::getProviderSubject)
                                        .toList())
                .orElseGet(List::of);
    }

    // ------------------------------------------------------------------ internos

    /**
     * Marca o endereço como verificado e resolve de quem é a conta.
     *
     * <p>É o único ponto em que uma identidade de senha passa a valer, e por isso o vínculo da D47
     * mora aqui: se outra conta já é dona deste endereço verificado, a identidade se muda para lá e
     * a conta do cadastro (vazia, porque nunca teve sessão) é apagada. Sem isso, quem já usava o
     * app pelo Google se cadastraria com o mesmo e-mail e cairia numa árvore em branco.
     */
    private String confirmar(UserIdentity identity, Instant now) {
        identity.refresh(identity.getEmail(), true, identity.getDisplayName());
        identity.registerLogin(now);
        AppUser dona =
                accounts.accountOwning(identity.getEmail(), true)
                        .map(target -> accounts.mergeIdentityInto(identity, target))
                        .orElseGet(() -> users.findById(identity.getUserId()).orElseThrow());
        accounts.claimVerifiedEmail(dona, identity.getEmail(), true);
        return identity.getEmail();
    }

    /** A identidade de senha de uma conta, se ela tiver uma. */
    private Optional<UserIdentity> identidadeDeSenha(Long userId) {
        return identities.findByUserId(userId).stream()
                .filter(
                        identity ->
                                PasswordAuthenticationToken.PROVIDER.equals(identity.getProvider()))
                .findFirst();
    }

    private void enviarVerificacao(Long userId, String email, String baseUrl, Instant now) {
        // Um link por vez: o anterior queima quando outro é pedido, senão pedir "reenviar" três
        // vezes deixaria três links vivos, e o mais antigo continuaria valendo por 24 horas.
        invalidarPendentes(userId, LoginTokenPurpose.VERIFICACAO, now);
        String raw = emitir(userId, LoginTokenPurpose.VERIFICACAO, VALIDADE_VERIFICACAO, now);
        enviar(
                email,
                "Confirme seu e-mail no FightOssStreak",
                """
                Falta um passo para sua conta existir. Abra o link abaixo para confirmar
                que este endereço é seu:

                %s/confirmar-email/%s

                O link vale por 24 horas e só funciona uma vez.
                Se não foi você que se cadastrou, ignore este e-mail — nada acontece, e a
                conta some sozinha por não ter sido confirmada.
                """
                        .formatted(baseUrl, raw));
    }

    private void enviarRedefinicao(Long userId, String email, String baseUrl, Instant now) {
        invalidarPendentes(userId, LoginTokenPurpose.REDEFINICAO, now);
        String raw = emitir(userId, LoginTokenPurpose.REDEFINICAO, VALIDADE_REDEFINICAO, now);
        enviar(
                email,
                "Redefinir sua senha no FightOssStreak",
                """
                Para escolher uma senha nova, abra o link abaixo:

                %s/senha/redefinir/%s

                O link vale por 1 hora e só funciona uma vez. Ao usá-lo, qualquer sessão
                aberta nesta conta é encerrada.
                Se não foi você que pediu, ignore este e-mail — sua senha continua a mesma.
                """
                        .formatted(baseUrl, raw));
    }

    private void avisarQueJaTemConta(String email) {
        enviar(
                email,
                "Você já tem conta no FightOssStreak",
                """
                Alguém tentou criar uma conta com este endereço, e ele já tem uma.

                Se foi você: entre com sua senha, ou use "esqueci minha senha" para
                escolher outra. Se você entra pelo Google, use o botão do Google — é a
                mesma conta, com o mesmo progresso.

                Se não foi você, não há o que fazer: ninguém entrou, e nada mudou.
                """);
    }

    private String emitir(Long userId, LoginTokenPurpose purpose, Duration validade, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new LoginToken(userId, hash(raw), purpose, now, now.plus(validade)));
        return raw;
    }

    private void invalidarPendentes(Long userId, Instant now) {
        tokens.findByUserIdAndUsedAtIsNull(userId).forEach(token -> token.invalidate(now));
    }

    private void invalidarPendentes(Long userId, LoginTokenPurpose purpose, Instant now) {
        tokens.findByUserIdAndUsedAtIsNull(userId).stream()
                .filter(token -> token.getPurpose() == purpose)
                .forEach(token -> token.invalidate(now));
    }

    private void enviar(String to, String assunto, String corpo) {
        EmailSender sender = emailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Cadastro por senha pedido sem provedor de envio configurado");
            return;
        }
        sender.send(to, assunto, corpo);
    }

    private void exigirEnvioConfigurado() {
        if (!isEnabled()) {
            throw PasswordAccessException.indisponivel();
        }
    }

    private void registrarErro(String email, String ip, Instant now) {
        freio.record(chaveEmail(email), JANELA_TENTATIVAS, now);
        freio.record(chaveIp(ip), JANELA_TENTATIVAS, now);
    }

    /**
     * Nome em branco é ausência de nome, não string vazia — a identidade guarda nulo nesse caso.
     */
    private static String nomeOuNulo(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        String limpo = nome.trim();
        return limpo.length() > MAX_NOME ? limpo.substring(0, MAX_NOME) : limpo;
    }

    private static String chaveEmail(String email) {
        return "senha:email:" + email;
    }

    private static String chaveIp(String ip) {
        return "senha:ip:" + ip;
    }

    /** O banco guarda só o hash — ver {@link LoginToken}. */
    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }
}
