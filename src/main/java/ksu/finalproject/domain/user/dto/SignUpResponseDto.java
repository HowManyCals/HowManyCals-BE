package ksu.finalproject.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SignUpResponseDto {
    private String accessToken;
    private String refreshToken;
    // 추후 HttpOnly Cookie로 변경 필요 -> Body에 담아서 보내면 노출 가능성 있음
}