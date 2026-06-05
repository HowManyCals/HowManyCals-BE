package ksu.finalproject.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "food.image")
@Validated
@Getter
@Setter
public class FoodImageProperties {
    @NotBlank
    private String storageDir;

    @NotNull
    private Long maxFileSize;

    @NotNull
    private Long retentionDays;
}

