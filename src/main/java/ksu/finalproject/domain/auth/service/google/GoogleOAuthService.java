package ksu.finalproject.domain.auth.service.google;

import ksu.finalproject.domain.auth.dto.google.GoogleUserInfoDto;
import ksu.finalproject.domain.auth.dto.OAuthTokenResponseDto;
import ksu.finalproject.domain.auth.service.OAuthProviderService;
import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.OAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleOAuthService implements OAuthProviderService {

    private final OAuthProperties oAuthProperties;
    private final RestTemplate restTemplate;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public String getAuthorizationUrl() {
        OAuthProperties.ProviderConfig config = oAuthProperties.getGoogle();
        return "https://accounts.google.com/o/oauth2/auth"
                + "?client_id=" + config.getClientId()
                + "&redirect_uri=" + config.getRedirectUri()
                + "&response_type=code"
                + "&scope=email%20profile";
    }

    @Override
    public String exchangeCodeForToken(String code) throws CustomException {
        OAuthProperties.ProviderConfig config = oAuthProperties.getGoogle();

        log.info("Google OAuth 토큰 교환 시작");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", config.getClientId());
        params.add("client_secret", config.getClientSecret());
        params.add("redirect_uri", config.getRedirectUri());
        params.add("code", code);

        try {
            OAuthTokenResponseDto response = restTemplate.postForObject(
                    "https://oauth2.googleapis.com/token",
                    new HttpEntity<>(params, headers),
                    OAuthTokenResponseDto.class
            );
            if (response == null || response.getAccessToken() == null) {
                log.warn("Google OAuth 토큰 교환 실패 - accessToken 누락");
                throw new CustomException(ResponseCode.OAUTH_TOKEN_EXCHANGE_FAILED);
            }

            log.info("Google OAuth 토큰 교환 성공");
            return response.getAccessToken();
        } catch (RestClientException e) {
            log.error("Google OAuth 토큰 교환 예외", e);
            throw new CustomException(ResponseCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    @Override
    public String getUserEmail(String accessToken) throws CustomException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        log.info("Google 사용자 이메일 조회 시작");

        try {
            GoogleUserInfoDto userInfo = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    GoogleUserInfoDto.class
            ).getBody();

            if (userInfo == null || userInfo.getEmail() == null) {
                log.warn("Google 사용자 이메일 조회 실패 - 이메일 누락");
                throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
            }

            log.info("Google 사용자 이메일 조회 성공");

            return userInfo.getEmail();
        } catch (RestClientException e) {
            log.error("Google 사용자 이메일 조회 예외", e);
            throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
        }
    }
}

