package ksu.finalproject.domain.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import ksu.finalproject.domain.auth.service.OAuthService;
import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.service.UserService;
import ksu.finalproject.domain.user.service.UserService.AuthTokens;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.OAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
    private final UserService userService;
    private final OAuthProperties oAuthProperties;

    /**
     * 소셜 로그인 페이지로 리다이렉트합니다.
     * provider: kakao, google, naver
     */
    @GetMapping("/oauth/{provider}")
    public void redirectOAuth(@PathVariable String provider,
                              HttpServletResponse response) throws CustomException, IOException {
        String authorizationUrl = oAuthService.getAuthorizationUrl(provider);
        response.sendRedirect(authorizationUrl);
    }

    /**
     * OAuth 콜백을 처리합니다.
     * provider에서 전달된 code로 사용자 정보를 조회한 후,
     * 발급된 accessToken / refreshToken을 모바일 앱 딥링크 쿼리 파라미터로 전달합니다.
     *
     * 흐름:
     *   1) provider + code → accessToken / refreshToken 발급
     *   2) {oauth.deep-link}?accessToken=xxx&refreshToken=yyy 형태로 302 리다이렉트
     *   3) 모바일 OS가 딥링크 스킴을 보고 앱을 깨워서 토큰을 전달
     */
    @GetMapping("/oauth/{provider}/callback")
    public void oauthCallback(@PathVariable String provider,
                              @RequestParam String code,
                              HttpServletResponse response) throws CustomException, IOException {

        AuthTokens<SignInResponseDto> tokens = oAuthService.processCallback(provider, code);

        String deepLinkUrl = UriComponentsBuilder
                .fromUriString(oAuthProperties.getDeepLink())
                .queryParam("accessToken", tokens.response().getAccessToken())
                .queryParam("refreshToken", tokens.refreshToken())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        log.info("[OAuth] {} 로그인 성공 -> 딥링크 리다이렉트", provider);
        response.sendRedirect(deepLinkUrl);
    }

    /**
     * refreshToken으로 새 accessToken을 발급합니다.
     * 모바일 앱은 쿠키 기반이 아니므로, body로 refreshToken을 받습니다.
     */
    @PostMapping("/refresh")
    public CommonResponse<SignInResponseDto> refresh(@RequestBody RefreshRequest request) throws CustomException {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new CustomException(ResponseCode.NOT_FOUND_AUTHORIZATION);
        }
        return new CommonResponse<>(ResponseCode.SUCCESS_REFRESH,
                userService.refreshAccessToken(request.refreshToken()));
    }

    /**
     * /auth/refresh 요청 body DTO
     */
    public record RefreshRequest(String refreshToken) {}
}
