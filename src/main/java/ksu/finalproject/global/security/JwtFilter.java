package ksu.finalproject.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final List<String> WHITELIST = List.of(
            "/user/signup",
            "/user/signin",
            "/status",
            "/h2-console/**"
    );

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String getToken(HttpServletRequest request) throws CustomException {
        String header = request.getHeader("Authorization");
        if (header == null || header.isEmpty())
            throw new CustomException(ResponseCode.NOT_FOUND_AUTHORIZATION);
        if (!header.startsWith("Bearer "))
            throw new CustomException(ResponseCode.INVALID_AUTHORIZATION);
        return header.substring(7);
    }

    /**
     * 예외 발생 시 JSON 에러 응답 전송
     * Filter는 ControllerAdvice가 잡지 못하므로 직접 응답 처리
     */
    private void sendErrorResponse(HttpServletResponse response, ResponseCode code) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        CommonResponse<?> body = new CommonResponse<>(code);
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(body));
        writer.flush();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String accessToken = getToken(request);

            if (!jwtProvider.isValidToken(accessToken))
                throw new CustomException(ResponseCode.INVALID_TOKEN);

            filterChain.doFilter(request, response);

        } catch (CustomException e) {
            // 인증 예외 -> 직접 에러 응답 후 필터 체인 중단
            sendErrorResponse(response, e.getStatus());
        }
    }
}
