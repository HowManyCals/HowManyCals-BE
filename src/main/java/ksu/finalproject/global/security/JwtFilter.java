package ksu.finalproject.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtFilter extends OncePerRequestFilter {
    private String getToken(HttpServletRequest request) throws CustomException {
        String header = request.getHeader("Authorization");
        if((header == null)||(header.isEmpty())) throw new CustomException(ResponseCode.NOT_FOUND_AUTHORIZATION);

        // 헤더 파싱
        if(!header.startsWith("Bearer ")) throw new CustomException(ResponseCode.INVALID_AUTHORIZATION);
        return header.substring(7);
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String accessToken = getToken(request);
        } catch (CustomException e) {
            throw new RuntimeException(e);
        }
    }
    //
}
