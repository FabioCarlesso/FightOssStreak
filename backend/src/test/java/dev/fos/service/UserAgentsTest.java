package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.DeviceClass;
import dev.fos.model.UsageEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** As três respostas grossas que o {@code User-Agent} pode dar sem individualizar ninguém (#84). */
class UserAgentsTest {

    private static final String IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15"
                    + " (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
    private static final String IPAD =
            "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
                    + " Gecko) Version/17.5 Safari/604.1";
    private static final String ANDROID_CELULAR =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String ANDROID_TABLET =
            "Mozilla/5.0 (Linux; Android 13; SM-X200) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/126.0.0.0 Safari/537.36";
    private static final String WINDOWS_FIREFOX =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0";
    private static final String MAC_CHROME =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like"
                    + " Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Test
    @DisplayName("celular, tablet e desktop saem certos — e a ordem das regras é a razão disso")
    void deviceClasses() {
        assertThat(UserAgents.device(IPHONE)).isEqualTo(DeviceClass.CELULAR);
        assertThat(UserAgents.device(ANDROID_CELULAR)).isEqualTo(DeviceClass.CELULAR);
        // Os dois casos que uma regra ingênua erraria: o iPad não diz "mobile", e o tablet Android
        // carrega "Android" igualzinho ao celular.
        assertThat(UserAgents.device(IPAD)).isEqualTo(DeviceClass.TABLET);
        assertThat(UserAgents.device(ANDROID_TABLET)).isEqualTo(DeviceClass.TABLET);
        assertThat(UserAgents.device(WINDOWS_FIREFOX)).isEqualTo(DeviceClass.DESKTOP);
    }

    @Test
    @DisplayName("sem User-Agent nada estoura: é desconhecido, que é categoria e não erro")
    void unknownIsACategory() {
        assertThat(UserAgents.device(null)).isEqualTo(DeviceClass.DESCONHECIDO);
        assertThat(UserAgents.browser("")).isEqualTo(UsageEvent.DESCONHECIDO);
        assertThat(UserAgents.os(null)).isEqualTo(UsageEvent.DESCONHECIDO);
        assertThat(UserAgents.language(null)).isEqualTo(UsageEvent.DESCONHECIDO);
    }

    @Test
    @DisplayName("família do navegador, com a ordem que impede todo mundo virar safari")
    void browserFamilies() {
        assertThat(UserAgents.browser(WINDOWS_FIREFOX)).isEqualTo("firefox");
        assertThat(UserAgents.browser(MAC_CHROME)).isEqualTo("chrome");
        assertThat(UserAgents.browser(IPHONE)).isEqualTo("safari");
        assertThat(
                        UserAgents.browser(
                                "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/126"
                                        + " Safari/537.36 Edg/126.0"))
                .isEqualTo("edge");
    }

    @Test
    @DisplayName("família do sistema — e o iPad, que se anuncia como Macintosh, não vira macos")
    void osFamilies() {
        assertThat(UserAgents.os(IPHONE)).isEqualTo("ios");
        assertThat(UserAgents.os(IPAD)).isEqualTo("ios");
        assertThat(UserAgents.os(ANDROID_CELULAR)).isEqualTo("android");
        assertThat(UserAgents.os(WINDOWS_FIREFOX)).isEqualTo("windows");
        assertThat(UserAgents.os(MAC_CHROME)).isEqualTo("macos");
    }

    @Test
    @DisplayName("do Accept-Language sai só a primeira tag, sem região — o resto é fingerprint")
    void languageIsCoarse() {
        assertThat(UserAgents.language("pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")).isEqualTo("pt");
        assertThat(UserAgents.language("en")).isEqualTo("en");
        assertThat(UserAgents.language("*")).isEqualTo(UsageEvent.DESCONHECIDO);
    }
}
