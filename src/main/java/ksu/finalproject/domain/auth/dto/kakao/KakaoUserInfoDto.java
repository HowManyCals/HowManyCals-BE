package ksu.finalproject.domain.auth.dto.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

// 카카오 사용자 정보 응답 구조: kakao_account.email
@Getter
public class KakaoUserInfoDto {

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    public static class KakaoAccount {
        private String email;
    }
}

