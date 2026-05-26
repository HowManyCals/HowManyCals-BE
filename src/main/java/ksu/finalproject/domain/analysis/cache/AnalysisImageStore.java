package ksu.finalproject.domain.analysis.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AnalysisImageStore {
    // Gemini API 전송 방식
    // MultipartFile -> byte array -> base64encode(byte array) -> send

    public record CachedImage(byte[] data, String contentType, LocalDateTime savedAt){}

    private final ConcurrentHashMap<Long, CachedImage> store = new ConcurrentHashMap<>();
    // ConcurrentHashMap -> 스레드 단위로 lock 가능함.
    // TTL 설정해서 주기적으로 캐시를 비워줘야함.

    // (AI 로그 ID, 이미지) 형태로 캐싱
    public void save(Long aiLogId, MultipartFile image) throws IOException {
        byte[] imageBytes = image.getBytes();
        store.put(aiLogId, new CachedImage(imageBytes, image.getContentType(), LocalDateTime.now()));
        log.info("이미지 캐싱 완료 aiLogId={} size={}", aiLogId, image.getSize());
    }

    public CachedImage get(Long aiLogId) {
        return store.get(aiLogId);
    }
}
