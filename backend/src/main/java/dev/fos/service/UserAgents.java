package dev.fos.service;

import dev.fos.model.DeviceClass;
import dev.fos.model.UsageEvent;
import java.util.Locale;

/**
 * O que dá para saber do {@code User-Agent} sem individualizar ninguém (#84, D50).
 *
 * <p>Três respostas grossas: celular/tablet/desktop, família do navegador, família do sistema. Nada
 * de versão, build ou lista de recursos — é justamente a combinação de sinais finos que transforma
 * "perfil de dispositivo" em impressão digital, e a D50 se comprometeu a não fazer isso.
 *
 * <p>Casamento por substring, e não uma biblioteca de parsing: a lista de famílias é fechada e
 * pequena, o custo de errar é uma categoria a menos no painel, e uma dependência nova que se
 * atualiza sozinha com regras de terceiro seria mais superfície do que o problema pede.
 */
public final class UserAgents {

    private UserAgents() {}

    /**
     * Tablet antes de celular, e "mobile" por último: o iPad se anuncia como Macintosh em Safari
     * moderno, e todo Android tablet carrega "Android" — que também está em todo celular. A ordem é
     * a regra.
     */
    public static DeviceClass device(String userAgent) {
        String ua = lower(userAgent);
        if (ua.isBlank()) {
            return DeviceClass.DESCONHECIDO;
        }
        if (ua.contains("ipad")
                || ua.contains("tablet")
                || ua.contains("playbook")
                || ua.contains("kindle")
                || (ua.contains("android") && !ua.contains("mobile"))) {
            return DeviceClass.TABLET;
        }
        if (ua.contains("mobi")
                || ua.contains("iphone")
                || ua.contains("ipod")
                || ua.contains("android")
                || ua.contains("windows phone")) {
            return DeviceClass.CELULAR;
        }
        return DeviceClass.DESKTOP;
    }

    /** Família do navegador. A ordem importa: quase todos se dizem "safari" e "chrome" também. */
    public static String browser(String userAgent) {
        String ua = lower(userAgent);
        if (ua.isBlank()) {
            return UsageEvent.DESCONHECIDO;
        }
        if (ua.contains("edg/") || ua.contains("edga") || ua.contains("edgios")) {
            return "edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "opera";
        }
        if (ua.contains("samsungbrowser")) {
            return "samsung";
        }
        if (ua.contains("firefox") || ua.contains("fxios")) {
            return "firefox";
        }
        if (ua.contains("chrome") || ua.contains("crios") || ua.contains("chromium")) {
            return "chrome";
        }
        if (ua.contains("safari")) {
            return "safari";
        }
        return UsageEvent.DESCONHECIDO;
    }

    /** Família do sistema. "iphone os" e "cpu os" são como o iOS se anuncia. */
    public static String os(String userAgent) {
        String ua = lower(userAgent);
        if (ua.isBlank()) {
            return UsageEvent.DESCONHECIDO;
        }
        if (ua.contains("iphone os") || ua.contains("cpu os") || ua.contains("ipados")) {
            return "ios";
        }
        if (ua.contains("android")) {
            return "android";
        }
        if (ua.contains("windows")) {
            return "windows";
        }
        // Depois do iOS de propósito: o iPad moderno se anuncia como Macintosh.
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macos";
        }
        if (ua.contains("cros")) {
            return "chromeos";
        }
        if (ua.contains("linux")) {
            return "linux";
        }
        return UsageEvent.DESCONHECIDO;
    }

    /**
     * Idioma do {@code Accept-Language}: só a primeira tag, e só até o hífen.
     *
     * <p>{@code pt-BR,pt;q=0.9,en-US;q=0.8} vira {@code pt}. A lista completa de idiomas aceitos é
     * um sinal de fingerprint conhecido, e o país já vem do IP — a região do idioma não acrescenta
     * nada que valha o que custa.
     */
    public static String language(String acceptLanguage) {
        String header = lower(acceptLanguage);
        if (header.isBlank()) {
            return UsageEvent.DESCONHECIDO;
        }
        String primeira = header.split(",", 2)[0].split(";", 2)[0].trim();
        String base = primeira.split("-", 2)[0];
        return base.length() >= 2
                        && base.length() <= 8
                        && base.chars().allMatch(Character::isLetter)
                ? base
                : UsageEvent.DESCONHECIDO;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
