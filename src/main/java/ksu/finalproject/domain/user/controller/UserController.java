package ksu.finalproject.domain.user.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ksu.finalproject.domain.user.dto.SignInRequestDto;
import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.dto.SignUpRequestDto;
import ksu.finalproject.domain.user.dto.SignUpResponseDto;
import ksu.finalproject.domain.user.service.UserService;
import ksu.finalproject.domain.user.service.UserService.AuthTokens;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtProperties jwtProperties;

    // [POST - 회원가입]
    @PostMapping("/signup")
    public CommonResponse<SignUpResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request,
                                                    HttpServletResponse response) throws CustomException {
        AuthTokens<SignUpResponseDto> tokens = userService.signUp(request); // 회원가입 시 토큰 발급
        Cookie refreshCookie = new Cookie("refreshToken", tokens.refreshToken());
        refreshCookie.setHttpOnly(true); // 브라우저에서 js 쿠키 접근 차단 (XSS 방지)
        refreshCookie.setPath("/"); // 쿠키를 첨부할 엔드포인트 지정
        refreshCookie.setMaxAge((int) (jwtProperties.getRefreshTokenExpiration() / 1000)); // 7일
        response.addCookie(refreshCookie);

        return new CommonResponse<>(ResponseCode.SUCCESS_SIGNUP, tokens.response());
    }

    // [POST - 로그인]
    @PostMapping("/signin")
    public CommonResponse<SignInResponseDto> signIn(@Valid @RequestBody SignInRequestDto request,
                                                    HttpServletResponse response) throws CustomException {
        AuthTokens<SignInResponseDto> tokens = userService.signIn(request);
        Cookie refreshCookie = new Cookie("refreshToken", tokens.refreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge((int) (jwtProperties.getRefreshTokenExpiration() / 1000));
        response.addCookie(refreshCookie);

        return new CommonResponse<>(ResponseCode.SUCCESS_SIGNIN, tokens.response());
    }
}
