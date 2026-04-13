package ksu.finalproject.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequestDto {
    @NotBlank(message = "이메일은 필수 입력 항목이에요.")
    @Email(message = "올바른 이메일 형식으로 입력해주세요.")
    private String email;

    @NotBlank(message = "비밀번호는 대소문자 + 특수문자로 8~20자로 설정해주세요.")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 설정해주세요.")
    private String password;
}