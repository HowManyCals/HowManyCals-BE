package ksu.finalproject.global.common;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data // Getter, Setter Auto 생성
@AllArgsConstructor
@JsonPropertyOrder({ "code", "success", "message", "data" })
public class CommonResponse<T> {
    // CommonResponse의 구조 정의
    private Integer code;
    private Boolean success;
    private String message;
    private T data;

    // 요청 성공 시
    public CommonResponse(ResponseCode status, T data) {
        this.code = status.getCode();
        this.success = status.getSuccess();
        this.message = status.getMessage();
        this.data = data;
    }

    // 요청 실패 시
    public CommonResponse(ResponseCode status) {
        this.code = status.getCode();
        this.success = status.getSuccess();
        this.message = status.getMessage();
    }
}
