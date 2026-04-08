package ksu.finalproject.domain.user.entity;

import jakarta.persistence.*;
import ksu.finalproject.domain.user.entity.enums.ActivityLevel;
import ksu.finalproject.domain.user.entity.enums.AuthProvider;
import ksu.finalproject.domain.user.entity.enums.Gender;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Builder
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Users {
    @Id //PK
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto increment
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", unique = true) // 로그인 식별자
    private String email;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING) // DB에 문자열로 저장
    @Column(name = "provider")
    private AuthProvider provider;

    @Column(name = "nickname", unique = true)
    private String nickName;

    @Column(name = "height_cm")
    private Integer height;

    @Column(name = "weight_kg")
    private Integer weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level") // 추천 알고리즘에 쓰일 활동 수준
    private ActivityLevel activityLevel;

    @Column(name = "refresh_token")
    private String refreshToken;

    public void updateRefreshToken(String token)
    {
        this.refreshToken = token;
    }

    @CreatedDate // 자동으로 기재
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate // 자동으로 기재
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}