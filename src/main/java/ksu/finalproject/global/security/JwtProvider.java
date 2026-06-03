package ksu.finalproject.global.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import ksu.finalproject.global.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private SecretKey key;

    @PostConstruct
    private void convertToKey() {
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        JwtBuilder jwtBuilder = Jwts.builder()
                .subject(userId.toString())
                .claim("type", "access")
                .issuedAt(now)
                .expiration(validity)
                .signWith(this.key);

        return jwtBuilder.compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());

        JwtBuilder jwtBuilder = Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(validity)
                .signWith(this.key);

        return jwtBuilder.compact();
    }

    // 토큰 유효 검사 (서명 유효 + 만료되지 않음)
    public boolean isValidToken(String token) {
        try {
            JwtParserBuilder parserBuilder = Jwts.parser()
                    .verifyWith(this.key);
            JwtParser parser = parserBuilder.build();
            parser.parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 서명은 유효하나 만료된 토큰인지 확인
    public boolean isExpiredToken(String token) {
        try {
            JwtParserBuilder parserBuilder = Jwts.parser()
                    .verifyWith(this.key);
            JwtParser parser = parserBuilder.build();
            parser.parseSignedClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {
        JwtParserBuilder parserBuilder = Jwts.parser()
                .verifyWith(this.key);
        JwtParser parser = parserBuilder.build();

        return Long.parseLong(
                parser.parseSignedClaims(token)
                        .getPayload()
                        .getSubject()
        );
    }
}
