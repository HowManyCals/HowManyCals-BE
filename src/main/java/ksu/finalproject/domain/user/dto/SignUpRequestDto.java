package ksu.finalproject.domain.user.dto;

import jakarta.validation.constraints.*;
import ksu.finalproject.domain.user.entity.enums.ActivityLevel;
import ksu.finalproject.domain.user.entity.enums.Gender;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequestDto {
    @NotBlank(message = "이메일은 필수 입력 항목이에요.")
    @Email
    private String email;

    @NotBlank(message = "비밀번호는 대소문자 + 특수문자로 8~20자로 설정해주세요.")
    @Size(min = 8, max = 20)
    private String password;

    @NotNull
    private Gender gender;

    @NotBlank(message = "닉네임은 비워둘 수 없어요")
    @Size(min = 2, max = 12) // String.length 기준
    private String nickname;

    @NotNull
    @Min(50)
    @Max(230)
    private Integer height;

    @NotNull
    @Min(20)
    @Max(300)
    private Integer weight;

    @NotNull
    private ActivityLevel activityLevel;
}