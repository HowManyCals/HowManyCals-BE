package ksu.finalproject.domain.user.service;

import ksu.finalproject.domain.user.dto.SignInRequestDto;
import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.dto.SignUpRequestDto;
import ksu.finalproject.domain.user.dto.SignUpResponseDto;
import ksu.finalproject.domain.user.dto.UpdateProfileRequestDto;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    public record AuthTokens<T>(T response, String refreshToken) {}

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthTokens<SignUpResponseDto> signUp(SignUpRequestDto request) throws CustomException {
        Optional<Users> users = userRepository.findByEmail(request.getEmail());
        if(users.isPresent()) throw new CustomException(ResponseCode.DUPLICATED_USER_EMAIL);

        // 패스워드 해시 처리
        String encodedPW = passwordEncoder.encode(request.getPassword());

        Users user = Users.builder()
                        .email(request.getEmail())
                        .password(encodedPW)
                        .provider(AuthProvider.LOCAL)
                        .build();
        Users savedUser = userRepository.save(user);

        String accessToken = jwtProvider.createAccessToken(savedUser.getId());
        String refreshToken = jwtProvider.createRefreshToken(savedUser.getId());

        savedUser.updateRefreshToken(refreshToken);
        userRepository.save(savedUser);

        return new AuthTokens<>(new SignUpResponseDto(accessToken), refreshToken);
    }

    public AuthTokens<SignInResponseDto> signIn(SignInRequestDto request) throws CustomException{
        String email = request.getEmail();
        String password = request.getPassword();

        // 유효 이메일 확인
        Optional<Users> users = userRepository.findByEmail(email);
        if(users.isEmpty()) throw new CustomException(ResponseCode.INVALID_USER_EMAIL);

        // 유효 패스워드 확인
        if (!passwordEncoder.matches(password, users.get().getPassword())) {
            throw new CustomException(ResponseCode.INVALID_PASSWORD);
        }
        Long userId = users.get().getId();
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        users.get().updateRefreshToken(refreshToken);
        userRepository.save(users.get());

        return new AuthTokens<>(new SignInResponseDto(accessToken), refreshToken);
    }

    /**
     * 회원가입 이후 추가 정보를 저장합니다.
     * JWT에서 추출한 userId로 유저를 조회하고, 닉네임 / 성별 / 키 / 체중 / 활동 수준을 업데이트합니다.
     */
    public void updateProfile(Long userId, UpdateProfileRequestDto request) throws CustomException {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ResponseCode.NOT_FOUND_USER));

        user.updateProfile(
                request.getNickname(),
                request.getGender(),
                request.getHeight(),
                request.getWeight(),
                request.getActivityLevel()
        );
        userRepository.save(user);
    }
}

