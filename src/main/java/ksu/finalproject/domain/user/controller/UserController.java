package ksu.finalproject.domain.user.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ksu.finalproject.domain.user.dto.SignInRequestDto;
import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.dto.SignUpRequestDto;
import ksu.finalproject.domain.user.dto.SignUpResponseDto;
import ksu.finalproject.domain.user.dto.UpdateProfileRequestDto;
import ksu.finalproject.domain.user.service.UserService;
import ksu.finalproject.domain.user.service.UserService.AuthTokens;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.JwtProperties;
import ksu.finalproject.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtProperties jwtProperties;
    private final JwtProvider jwtProvider;

    // [POST - 회원가입]
    @PostMapping("/signup")
    public CommonResponse<SignUpResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request,
                                                    HttpServletResponse response) throws CustomException {
        AuthTokens<SignUpResponseDto> tokens = userService.signUp(request);
        Cookie refreshCookie = new Cookie("refreshToken", tokens.refreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge((int) (jwtProperties.getRefreshTokenExpiration() / 1000));
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

    // [PATCH - 추가 정보 입력]
    @PatchMapping("/profile")
    public CommonResponse<?> updateProfile(@Valid @RequestBody UpdateProfileRequestDto request,
                                           HttpServletRequest httpRequest) throws CustomException {
        String token = httpRequest.getHeader("Authorization").substring(7);
        Long userId = jwtProvider.getUserId(token);
        userService.updateProfile(userId, request);
        return new CommonResponse<>(ResponseCode.SUCCESS_UPDATE_PROFILE);
    }
}

