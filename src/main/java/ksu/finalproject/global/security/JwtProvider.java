package ksu.finalproject.global.security;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.*;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtProvider {
    @Value("${jwt.secret}") // application.properties 값 주입
    private String secretKey;
    private Key key;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration; // 1시간

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration; // 7일

    @PostConstruct // Value 주입 이후 실행
    private void convertToKey(){
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId){
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("type", "access")  // 토큰 타입 구분용 Custom
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(this.key)
                .compact();
    }

    public String createRefreshToken(Long userId){
        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("type", "refresh")  // 토큰 타입 구분용 Custom
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(this.key)
                .compact();
    }

    // 토큰 유효 검사
    public boolean isValidToken(String token){
        try{
            Jwts.parserBuilder()
                    .setSigningKey(this.key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e){
            return false;
        }

    }
}
