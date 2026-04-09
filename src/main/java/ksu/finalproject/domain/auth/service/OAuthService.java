package ksu.finalproject.domain.auth.service;

import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.domain.user.service.UserService.AuthTokens;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.OAuthProperties;
import ksu.finalproject.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oAuthProperties;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RestTemplate restTemplate;

    /**
     * provider 문자열을 AuthProvider enum으로 변환합니다.
     * LOCAL -> 일반 회원가입 엔드포인트 안내 예외
     * APPLE -> 미지원 OAuth 제공자 예외
     */
    private AuthProvider parseProvider(String provider) throws CustomException {
        try {
            AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());
            if (authProvider == AuthProvider.LOCAL) {
                throw new CustomException(ResponseCode.LOCAL_PROVIDER_NOT_OAUTH);
            }
            if (authProvider == AuthProvider.APPLE) {
                throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
            }
            return authProvider;
        } catch (IllegalArgumentException e) {
            throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
    }

    // provider에 맞는 설정 반환
    private OAuthProperties.ProviderConfig getProviderConfig(AuthProvider provider) throws CustomException {
        return switch (provider) {
            case KAKAO -> oAuthProperties.getKakao();
            case GOOGLE -> oAuthProperties.getGoogle();
            case NAVER -> oAuthProperties.getNaver();
            default -> throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
        };
    }

    /**
     * provider별 OAuth 인가 URL을 반환합니다.
     * 프론트엔드는 해당 URL로 사용자를 리다이렉트합니다.
     */
    public String getAuthorizationUrl(String provider) throws CustomException {
        AuthProvider authProvider = parseProvider(provider);
        OAuthProperties.ProviderConfig config = getProviderConfig(authProvider);

        return switch (authProvider) {
            case KAKAO -> "https://kauth.kakao.com/oauth/authorize"
                    + "?client_id=" + config.getClientId()
                    + "&redirect_uri=" + config.getRedirectUri()
                    + "&response_type=code";
            case GOOGLE -> "https://accounts.google.com/o/oauth2/auth"
                    + "?client_id=" + config.getClientId()
                    + "&redirect_uri=" + config.getRedirectUri()
                    + "&response_type=code"
                    + "&scope=email%20profile";
            case NAVER -> "https://nid.naver.com/oauth2.0/authorize"
                    + "?client_id=" + config.getClientId()
                    + "&redirect_uri=" + config.getRedirectUri()
                    + "&response_type=code";
            default -> throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
        };
    }

    /**
     * OAuth 콜백을 처리합니다.
     * code -> 액세스 토큰 교환 -> 사용자 이메일 조회 -> 유저 생성 또는 조회 -> JWT 발급
     */
    public AuthTokens<SignInResponseDto> processCallback(String provider, String code) throws CustomException {
        AuthProvider authProvider = parseProvider(provider);
        OAuthProperties.ProviderConfig config = getProviderConfig(authProvider);

        // 1. code -> provider access token 교환
        String providerAccessToken = exchangeCodeForToken(authProvider, config, code);

        // 2. provider access token -> 사용자 이메일 조회
        String email = getUserEmail(authProvider, providerAccessToken);

        // 3. 이메일로 유저 조회, 없으면 신규 생성 (소셜 가입)
        Users user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        Users.builder()
                                .email(email)
                                .provider(authProvider)
                                .build()
                ));

        // 4. JWT 발급
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        user.updateRefreshToken(refreshToken);
        userRepository.save(user);

        return new AuthTokens<>(new SignInResponseDto(accessToken), refreshToken);
    }

    // provider의 토큰 엔드포인트에 code를 전달하여 access token을 받아옵니다.
    @SuppressWarnings("unchecked")
    private String exchangeCodeForToken(AuthProvider provider,
                                        OAuthProperties.ProviderConfig config,
                                        String code) throws CustomException {
        String tokenUri = switch (provider) {
            case KAKAO -> "https://kauth.kakao.com/oauth/token";
            case GOOGLE -> "https://oauth2.googleapis.com/token";
            case NAVER -> "https://nid.naver.com/oauth2.0/token";
            default -> throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", config.getClientId());
        params.add("client_secret", config.getClientSecret());
        params.add("redirect_uri", config.getRedirectUri());
        params.add("code", code);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    tokenUri, new HttpEntity<>(params, headers), Map.class
            );
            if (response == null || !response.containsKey("access_token")) {
                throw new CustomException(ResponseCode.OAUTH_TOKEN_EXCHANGE_FAILED);
            }
            return (String) response.get("access_token");
        } catch (RestClientException e) {
            throw new CustomException(ResponseCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    // provider의 사용자 정보 엔드포인트에서 이메일을 추출합니다.
    @SuppressWarnings("unchecked")
    private String getUserEmail(AuthProvider provider, String accessToken) throws CustomException {
        String userInfoUri = switch (provider) {
            case KAKAO -> "https://kapi.kakao.com/v2/user/me";
            case GOOGLE -> "https://www.googleapis.com/oauth2/v3/userinfo";
            case NAVER -> "https://openapi.naver.com/v1/nid/me";
            default -> throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            Map<String, Object> userInfo = restTemplate.exchange(
                    userInfoUri, HttpMethod.GET, new HttpEntity<>(headers), Map.class
            ).getBody();

            if (userInfo == null) throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);

            return switch (provider) {
                case KAKAO -> {
                    Map<String, Object> kakaoAccount = (Map<String, Object>) userInfo.get("kakao_account");
                    if (kakaoAccount == null || !kakaoAccount.containsKey("email")) {
                        throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
                    }
                    yield (String) kakaoAccount.get("email");
                }
                case GOOGLE -> {
                    if (!userInfo.containsKey("email")) {
                        throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
                    }
                    yield (String) userInfo.get("email");
                }
                case NAVER -> {
                    Map<String, Object> naverResponse = (Map<String, Object>) userInfo.get("response");
                    if (naverResponse == null || !naverResponse.containsKey("email")) {
                        throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
                    }
                    yield (String) naverResponse.get("email");
                }
                default -> throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
            };
        } catch (RestClientException e) {
            throw new CustomException(ResponseCode.OAUTH_USER_INFO_FAILED);
        }
    }
}

