package ksu.finalproject.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

// Controller에서 @Valid 필수
@Getter
public class SignInRequestDto {
    @NotBlank(message = "이메일은 필수 입력 항목이에요.")
    @Email(message = "올바른 이메일 형식으로 입력해주세요.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목이에요.")
    private String password;
}
