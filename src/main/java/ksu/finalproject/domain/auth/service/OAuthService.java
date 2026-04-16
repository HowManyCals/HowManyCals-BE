package ksu.finalproject.domain.auth.service;

import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.domain.user.service.UserService.AuthTokens;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final List<OAuthProviderService> providerServices; // implements 구현체 전부 주입
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * provider 문자열을 AuthProvider enum으로 변환합니다.
     * KAKAO, GOOGLE, NAVER 외의 값은 예외를 던집니다.
     *
     * @param provider OAuth provider 문자열 (예: kakao, google, naver)
     * @return 변환된 AuthProvider enum 값 (예: "kakao" -> AuthProvider.KAKAO)
     */
    private AuthProvider parseProvider(String provider) throws CustomException {
        try {
            return switch (AuthProvider.valueOf(provider.toUpperCase())) {
                case KAKAO -> AuthProvider.KAKAO;
                case GOOGLE -> AuthProvider.GOOGLE;
                case NAVER -> AuthProvider.NAVER;
                default -> throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
            };
        } catch (IllegalArgumentException e) {
            log.warn("지원하지 않는 OAuth provider 요청 provider={}", provider);
            throw new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
    }

    // provider에 맞는 OAuthProviderService 반환
    private OAuthProviderService getProviderService(AuthProvider provider) throws CustomException {
        return providerServices.stream()
                .filter(s -> s.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("OAuth provider service 미구현 provider={}", provider);
                    return new CustomException(ResponseCode.UNSUPPORTED_OAUTH_PROVIDER);
                });
    }

    /**
     * provider별 OAuth 인가 URL을 반환합니다.
     * 프론트엔드는 해당 URL로 사용자를 리다이렉트합니다.
     *
     * @param provider OAuth provider 문자열 (예: kakao, google, naver)
     * @return 해당 provider의 OAuth 인가 URL 문자열
     */
    public String getAuthorizationUrl(String provider) throws CustomException {
        AuthProvider authProvider = parseProvider(provider);
        log.info("OAuth 인가 URL 조회 provider={}", authProvider);
        return getProviderService(authProvider).getAuthorizationUrl();
    }

    /**
     * OAuth 콜백을 처리합니다.
     * code -> 액세스 토큰 교환 -> 사용자 이메일 조회 -> 유저 생성 또는 조회 -> JWT 발급
     *
     * @param provider OAuth provider 문자열 (예: kakao, google, naver)
     * @param code     provider로부터 전달받은 인가 코드
     * @return 발급된 액세스 토큰(SignInResponseDto)과 리프레시 토큰을 담은 AuthTokens
     */
    public AuthTokens<SignInResponseDto> processCallback(String provider, String code) throws CustomException {
        AuthProvider authProvider = parseProvider(provider);
        OAuthProviderService providerService = getProviderService(authProvider);

        log.info("OAuth 콜백 처리 시작 provider={}", authProvider);

        // 1. code -> provider access token 교환
        String providerAccessToken = providerService.exchangeCodeForToken(code);

        // 2. provider access token -> 사용자 이메일 조회
        String email = providerService.getUserEmail(providerAccessToken);

        // 3. 이메일로 유저 조회, 없으면 신규 생성 (소셜 가입)
        Users existingUser = userRepository.findByEmail(email).orElse(null);
        boolean newUser = existingUser == null;
        Users user = newUser
                ? userRepository.save(
                        Users.builder()
                                .email(email)
                                .provider(authProvider)
                                .build()
                )
                : existingUser;

        // 4. JWT 발급
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        user.updateRefreshToken(refreshToken);
        userRepository.save(user);

        log.info("OAuth 콜백 처리 성공 provider={}, userId={}, newUser={}, email={}",
                authProvider,
                user.getId(),
                newUser,
                maskEmail(email));

        return new AuthTokens<>(new SignInResponseDto(accessToken), refreshToken);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }

        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
