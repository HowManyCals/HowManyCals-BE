package ksu.finalproject.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.server")
@Getter
@Setter
public class AiServerProperties {
    private String analyzeUrl;
}

