package ksu.finalproject.domain.auth.service;

import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.global.common.CustomException;

public interface OAuthProviderService {

    // 해당 서비스가 담당하는 provider 반환
    AuthProvider getProvider();

    // provider 인가 URL 반환
    String getAuthorizationUrl();

    /**
     * provider 토큰 엔드포인트에 인가 코드를 전달하여 액세스 토큰을 발급받습니다.
     *
     * @param code provider로부터 전달받은 인가 코드
     * @return provider 액세스 토큰 문자열
     */
    String exchangeCodeForToken(String code) throws CustomException;

    /**
     * provider 액세스 토큰으로 사용자 이메일을 조회합니다.
     *
     * @param accessToken provider로부터 발급받은 액세스 토큰
     * @return 사용자 이메일 문자열
     */
    String getUserEmail(String accessToken) throws CustomException;
}