package ksu.finalproject.domain.user.service;

import ksu.finalproject.domain.user.dto.SignInRequestDto;
import ksu.finalproject.domain.user.dto.SignInResponseDto;
import ksu.finalproject.domain.user.dto.SignUpRequestDto;
import ksu.finalproject.domain.user.dto.SignUpResponseDto;
import ksu.finalproject.domain.user.dto.UpdateProfileRequestDto;
import ksu.finalproject.domain.user.dto.UpdateProfileResponseDto;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public record AuthTokens<T>(T response, String refreshToken) {}

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthTokens<SignUpResponseDto> signUp(SignUpRequestDto request) throws CustomException {
        log.info("회원가입 요청 email={}", maskEmail(request.getEmail()));

        Optional<Users> users = userRepository.findByEmail(request.getEmail());
        if(users.isPresent()) {
            log.warn("회원가입 실패 - 중복 이메일 email={}", maskEmail(request.getEmail()));
            throw new CustomException(ResponseCode.DUPLICATED_USER_EMAIL);
        }

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

        log.info("회원가입 성공 userId={}, provider={}", savedUser.getId(), savedUser.getProvider());

        return new AuthTokens<>(new SignUpResponseDto(accessToken), refreshToken);
    }

    public AuthTokens<SignInResponseDto> signIn(SignInRequestDto request) throws CustomException{
        String email = request.getEmail();
        String password = request.getPassword();

        log.info("로그인 요청 email={}", maskEmail(email));

        // 유효 이메일 확인
        Optional<Users> users = userRepository.findByEmail(email);
        if(users.isEmpty()) {
            log.warn("로그인 실패 - 존재하지 않는 이메일 email={}", maskEmail(email));
            throw new CustomException(ResponseCode.INVALID_USER_EMAIL);
        }

        // 유효 패스워드 확인
        if (!passwordEncoder.matches(password, users.get().getPassword())) {
            log.warn("로그인 실패 - 비밀번호 불일치 userId={}", users.get().getId());
            throw new CustomException(ResponseCode.INVALID_PASSWORD);
        }
        Long userId = users.get().getId();
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        users.get().updateRefreshToken(refreshToken);
        userRepository.save(users.get());

        log.info("로그인 성공 userId={}, provider={}", userId, users.get().getProvider());

        return new AuthTokens<>(new SignInResponseDto(accessToken), refreshToken);
    }

    /**
     * 회원가입 이후 추가 정보를 저장합니다.
     * JWT에서 추출한 userId로 유저를 조회하고, 닉네임 / 성별 / 키 / 체중 / 활동 수준을 업데이트합니다.
     *
     * @param userId  JWT에서 추출한 유저 ID
     * @param request 닉네임, 성별, 키, 체중, 활동 수준을 담은 요청 DTO
     */
    public void updateProfile(Long userId, UpdateProfileRequestDto request) throws CustomException {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("프로필 수정 실패 - 사용자 없음 userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });

        // 본인 닉네임이 아닌 다른 유저가 동일 닉네임 사용 중이면 예외
        boolean isDuplicated = userRepository.findByNickName(request.getNickname())
                .filter(found -> !found.getId().equals(userId))
                .isPresent();
        if (isDuplicated) {
            log.warn("프로필 수정 실패 - 닉네임 중복 userId={}, nickname={}", userId, request.getNickname());
            throw new CustomException(ResponseCode.DUPLICATED_NICKNAME);
        }

        user.updateProfile(
                request.getNickname(),
                request.getGender(),
                request.getHeight(),
                request.getWeight(),
                request.getActivityLevel(),
                request.getAge()
        );
        userRepository.save(user);

        log.info("프로필 수정 성공 userId={}, nickname={}", userId, request.getNickname());
    }

    /**
     * 유저의 프로필 정보를 조회합니다.
     *
     * @param userId JWT에서 추출한 유저 ID
     * @return 닉네임, 성별, 키, 체중, 활동 수준을 담은 UpdateProfileResponseDto
     */
    public UpdateProfileResponseDto getProfile(Long userId) throws CustomException {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("프로필 조회 실패 - 사용자 없음 userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });

        return new UpdateProfileResponseDto(
                user.getNickName(),
                user.getGender(),
                user.getHeight(),
                user.getWeight(),
                user.getActivityLevel(),
                user.getAge()
        );
    }

    /**
     * refreshToken을 검증하고 새로운 accessToken을 발급합니다.
     *
     * @param refreshToken HttpOnly 쿠키에서 추출한 refreshToken 문자열
     * @return 새로 발급된 accessToken을 담은 SignInResponseDto
     */
    public SignInResponseDto refreshAccessToken(String refreshToken) throws CustomException {
        // DB에 저장된 refreshToken과 일치하는 유저 조회
        Users user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> {
                    log.warn("액세스 토큰 재발급 실패 - DB에 없는 refreshToken");
                    return new CustomException(ResponseCode.INVALID_TOKEN);
                });

        if (jwtProvider.isExpiredToken(refreshToken)) {
            log.warn("액세스 토큰 재발급 실패 - 만료된 refreshToken userId={}", user.getId());
            throw new CustomException(ResponseCode.EXPIRED_TOKEN);
        }
        if (!jwtProvider.isValidToken(refreshToken)) {
            log.warn("액세스 토큰 재발급 실패 - 유효하지 않은 refreshToken userId={}", user.getId());
            throw new CustomException(ResponseCode.INVALID_TOKEN);
        }

        log.info("액세스 토큰 재발급 성공 userId={}", user.getId());

        return new SignInResponseDto(jwtProvider.createAccessToken(user.getId()));
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }

        return email.substring(0, Math.min(2, atIndex)) + "***" + email.substring(atIndex);
    }
}

