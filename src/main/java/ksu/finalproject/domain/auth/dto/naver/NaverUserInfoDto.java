package ksu.finalproject.domain.auth.dto.naver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

// 네이버 사용자 정보 응답 구조: response.email
@Getter
public class NaverUserInfoDto {

    @JsonProperty("response")
    private NaverUserProfile naverUserProfile;

    @Getter
    public static class NaverUserProfile {
        private String email;
    }
}

