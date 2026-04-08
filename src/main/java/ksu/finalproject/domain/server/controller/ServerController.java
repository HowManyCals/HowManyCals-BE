package ksu.finalproject.domain.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;


@RestController
@RequestMapping("/status")
public class ServerController {
    @GetMapping
    public ResponseEntity<?> getStatus(){
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "timestamp", LocalDateTime.now(),
                "message", "서버가 정상적으로 동작하고 있습니다."
        )); // jackson이 자동으로 Map 객체를 JSON으로 변환해줌
    }
}
