package ksu.finalproject.domain.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import ksu.finalproject.domain.auth.service.OAuthService;
import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.service.UserService.AuthTokens;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
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

        return new CommonResponse<>(ResponseCode.SUCCESS, tokens.response());
    }
}

