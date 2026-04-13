package ksu.finalproject.domain.food.entity;

import jakarta.persistence.*;
import ksu.finalproject.domain.food.entity.enums.AnalysisStatus;
import ksu.finalproject.domain.user.entity.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ai_analysis_log")
public class AiAnalysisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "log_id")
    private Long logId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "model_version")
    private String modelVersion;

    @Lob // Large Object => 대용량 데이터
    @Column(name = "raw_output")
    private String rawOutput;

    @Column(name = "inference_time_ms")
    private Long inferenceTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false)
    private AnalysisStatus analysisStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // AI 서버 접수 성공 -> rawOutput 저장, 상태를 PROCESSING으로 전환
    public void acceptRequest(String rawOutput) {
        this.rawOutput = rawOutput;
        this.analysisStatus = AnalysisStatus.PROCESSING;
    }

    // AI 서버 요청 실패 -> 상태를 FAILED로 전환
    public void fail() {
        this.analysisStatus = AnalysisStatus.FAILED;
    }

    public void updateAnalysisResult(String modelVersion,
                                     String rawOutput,
                                     Long inferenceTimeMs,
                                     AnalysisStatus analysisStatus) {
        this.modelVersion = modelVersion;
        this.rawOutput = rawOutput;
        this.inferenceTimeMs = inferenceTimeMs;
        this.analysisStatus = analysisStatus;
    }
}

