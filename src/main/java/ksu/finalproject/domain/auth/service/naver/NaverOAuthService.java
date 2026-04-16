package ksu.finalproject.domain.auth.service.naver;

import ksu.finalproject.domain.auth.dto.naver.NaverUserInfoDto;
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
public class NaverOAuthService implements OAuthProviderService {

    private final OAuthProperties oAuthProperties;
    private final RestTemplate restTemplate;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.NAVER;
    }

    @Override
    public String getAuthorizationUrl() {
        OAuthProperties.ProviderConfig config = oAuthProperties.getNaver();
        return "https://nid.naver.com/oauth2.0/authorize"
                + "?client_id=" + config.getClientId()
                + "&redirect_uri=" + config.getRedirectUri()
                + "&response_type=code";
    }

    @Override
    public String exchangeCodeForToken(String code) throws CustomException {
        OAuthProperties.ProviderConfig config = oAuthProperties.getNaver();

        log.info("Naver OAuth 토큰 교환 시작");

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
                    "https://nid.naver.com/oauth2.0/token",
                    new HttpEntity<>(params, headers),
                    OAuthTokenResponseDto.class
            );
            if (response == null || response.getAccessToken() == null) {
                log.warn("Naver OAuth 토큰 교환 실패 - accessToken 누락");
                throw new CustomException(ResponseCode.OAUTH_TOKEN_EXCHANGE_FAILED);
            }

            log.info("Naver OAuth 토큰 교환 성공");
            return response.getAccessToken();
        } catch (RestClientException e) {
            log.error("Naver OAuth 토큰 교환 예외", e);
            throw new CustomException(ResponseCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    @Override
    public String getUserEmail(String accessToken) throws CustomException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        log.info("Naver 사용자 이메일 조회 시작");

        try {
            NaverUserInfoDto userInfo = restTemplate.exchange(
                    "https://openapi.naver.com/v1/nid/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    NaverUserInfoDto.class
            ).getBody();

            if (userInfo == null || userInfo.getNaverUserProfile() == null
                    || userInfo.getNaverUserProfile().getEmail() == null) {
                log.warn("Naver 사용자 이메일 조회 실패 - 이메일 누락");
                throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
            }

            log.info("Naver 사용자 이메일 조회 성공");

            return userInfo.getNaverUserProfile().getEmail();
        } catch (RestClientException e) {
            log.error("Naver 사용자 이메일 조회 예외", e);
            throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
        }
    }
}

