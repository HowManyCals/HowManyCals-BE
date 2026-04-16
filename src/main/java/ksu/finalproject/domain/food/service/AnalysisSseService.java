package ksu.finalproject.domain.food.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SSE 연결을 관리합니다.
 * ai_log_id 기준으로 FE 연결을 보관하고, 콜백 수신 시 결과를 푸시합니다.
 */
@Slf4j
@Component
public class AnalysisSseService {

    private static final long SSE_TIMEOUT_MS = 3 * 60 * 1000L; // 3분

    private final ConcurrentMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * FE의 SSE 구독 요청을 등록합니다.
     * 동일한 aiLogId로 중복 구독 시 기존 연결은 종료됩니다.
     */
    public SseEmitter register(Long aiLogId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> emitters.remove(aiLogId));
        emitter.onTimeout(() -> {
            emitters.remove(aiLogId);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(aiLogId));

        SseEmitter previous = emitters.put(aiLogId, emitter);
        if (previous != null) {
            log.info("기존 SSE 연결 종료 후 신규 구독 등록 aiLogId={}", aiLogId);
            previous.complete(); // 기존 연결 정리
        } else {
            log.info("SSE 구독 등록 aiLogId={}", aiLogId);
        }

        return emitter;
    }

    /**
     * 분석 결과를 FE에 즉시 전송합니다.
     * 연결이 없으면 무시합니다.
     */
    public void emit(Long aiLogId, Object data) {
        SseEmitter emitter = emitters.remove(aiLogId);
        if (emitter == null) {
            log.info("SSE 전송 생략 - 활성 연결 없음 aiLogId={}", aiLogId);
            return;
        }

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("analysis-complete")
                            .data(data, MediaType.APPLICATION_JSON)
            );
            emitter.complete();
            log.info("SSE 전송 완료 aiLogId={}", aiLogId);
        } catch (IOException e) {
            log.warn("SSE 전송 실패 aiLogId={}: {}", aiLogId, e.getMessage());
            emitter.completeWithError(e);
        }
    }
}

