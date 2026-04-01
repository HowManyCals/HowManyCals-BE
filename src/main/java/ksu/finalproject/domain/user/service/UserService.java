package ksu.finalproject.domain.user.service;

import ksu.finalproject.domain.user.dto.SignUpRequestDto;
import ksu.finalproject.domain.user.dto.SignUpResponseDto;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.SecurityConfig;
import ksu.finalproject.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public SignUpResponseDto signUp(SignUpRequestDto request) throws CustomException{
        Optional<Users> users = userRepository.findByEmail(request.getEmail());
        if(users.isPresent()) throw new CustomException(ResponseCode.DUPLICATED_USER_EMAIL);

        // 패스워드 해시 처리
        String encodedPW = passwordEncoder.encode(request.getPassword());

        Users user = Users.builder()
                        .email(request.getEmail())
                        .password(encodedPW)
                        .nickName(request.getNickname())
                        .height(request.getHeight())
                        .weight(request.getWeight())
                        .gender(request.getGender())
                        .activityLevel(request.getActivityLevel())
                        .provider(AuthProvider.LOCAL) // 일반 가입
                        .build();
        Users savedUser = userRepository.save(user);

        String accessToken = jwtProvider.createAccessToken(savedUser.getId());
        String refreshToken = jwtProvider.createRefreshToken(savedUser.getId());

        savedUser.updateRefreshToken(refreshToken);
        userRepository.save(savedUser);

        return new SignUpResponseDto(accessToken, refreshToken);
    }
}
