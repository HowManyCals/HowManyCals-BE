package ksu.finalproject.domain.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ksu.finalproject.domain.auth.service.OAuthService;
import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.service.UserService;
import ksu.finalproject.domain.user.service.UserService.AuthTokens;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
    private final UserService userService;
    private final JwtProperties jwtProperties;

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
     * provider에서 전달된 code로 사용자 정보를 조회하고 JWT를 발급합니다.
     */
    @GetMapping("/oauth/{provider}/callback")
    public CommonResponse<SignInResponseDto> oauthCallback(@PathVariable String provider,
                                                           @RequestParam String code,
                                                           HttpServletResponse response) throws CustomException {
        AuthTokens<SignInResponseDto> tokens = oAuthService.processCallback(provider, code);

        Cookie refreshCookie = new Cookie("refreshToken", tokens.refreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge((int) (jwtProperties.getRefreshTokenExpiration() / 1000));
        response.addCookie(refreshCookie);

        return new CommonResponse<>(ResponseCode.SUCCESS_OAUTH_LOGIN, tokens.response());
    }

    /**
     * refreshToken 쿠키를 검증하고 새로운 accessToken을 발급합니다.
     *
     * @param request refreshToken이 담긴 HttpOnly 쿠키를 포함한 HTTP 요청
     * @return 새로 발급된 accessToken을 담은 SignInResponseDto
     */
    @PostMapping("/refresh")
    public CommonResponse<SignInResponseDto> refresh(HttpServletRequest request) throws CustomException {
        // HttpOnly 쿠키에서 refreshToken 추출
        String refreshToken = null;
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(c -> "refreshToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (refreshToken == null) throw new CustomException(ResponseCode.NOT_FOUND_AUTHORIZATION);

        return new CommonResponse<>(ResponseCode.SUCCESS_REFRESH, userService.refreshAccessToken(refreshToken));
    }
}

