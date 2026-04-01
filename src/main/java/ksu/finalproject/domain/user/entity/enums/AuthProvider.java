package ksu.finalproject.domain.user.entity.enums;

import lombok.Getter;

@Getter
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    KAKAO,
    NAVER,
    APPLE
}