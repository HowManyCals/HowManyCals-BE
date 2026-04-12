package ksu.finalproject.domain.user.dto;

import jakarta.validation.constraints.*;
import ksu.finalproject.domain.user.entity.enums.ActivityLevel;
import ksu.finalproject.domain.user.entity.enums.Gender;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProfileRequestDto {
    @NotBlank(message = "닉네임은 비워둘 수 없어요.")
    @Size(min = 2, max = 12, message = "닉네임은 2~12자로 설정해주세요.")
    private String nickname;

    @NotNull(message = "성별은 필수 입력 항목이에요.")
    private Gender gender;

    @NotNull(message = "키는 필수 입력 항목이에요.")
    @Min(value = 50, message = "키는 50cm 이상으로 입력해주세요.")
    @Max(value = 230, message = "키는 230cm 이하로 입력해주세요.")
    private Integer height;

    @NotNull(message = "체중은 필수 입력 항목이에요.")
    @Min(value = 20, message = "체중은 20kg 이상으로 입력해주세요.")
    @Max(value = 300, message = "체중은 300kg 이하로 입력해주세요.")
    private Integer weight;

    @NotNull(message = "활동 수준은 필수 입력 항목이에요.")
    private ActivityLevel activityLevel;
}

