package ksu.finalproject.domain.user.controller;

import jakarta.validation.Valid;
import ksu.finalproject.domain.user.dto.SignUpRequestDto;
import ksu.finalproject.domain.user.dto.SignUpResponseDto;
import ksu.finalproject.domain.user.service.UserService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController{
    private final UserService userService;

    @PostMapping("/signup")
    public CommonResponse<SignUpResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request) throws CustomException {
        return new CommonResponse<SignUpResponseDto>(ResponseCode.SUCCESS, userService.signUp(request));
    }
}
