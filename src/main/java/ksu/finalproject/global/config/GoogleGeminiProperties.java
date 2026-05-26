package ksu.finalproject.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.gemini")
@Getter
@Setter
public class GoogleGeminiProperties {
    private String apiKey;
    private String model = "gemini-2.5-flash-lite";
    private String urlTemplate = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
}

