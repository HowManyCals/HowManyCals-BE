package ksu.finalproject.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "oauth")
@Getter
@Setter
public class OAuthProperties {

    private ProviderConfig kakao = new ProviderConfig();
    private ProviderConfig google = new ProviderConfig();
    private ProviderConfig naver = new ProviderConfig();

    /**
     * OAuth 콜백 성공 후 모바일 앱으로 리다이렉트할 딥링크 스킴
     * 예: howmanycals://auth
     */
    private String deepLink;

    @Getter
    @Setter
    public static class ProviderConfig {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}
