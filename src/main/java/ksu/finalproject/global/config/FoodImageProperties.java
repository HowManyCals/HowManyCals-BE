package ksu.finalproject.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "food.image")
@Getter
@Setter
public class FoodImageProperties {
    private String tempDir = "./uploads/temp";
    private Long maxFileSize = 5_242_880L;
}

