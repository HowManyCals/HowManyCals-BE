package ksu.finalproject.global.common;

import lombok.Getter;

@Getter
public enum ResponseCode {
    //region    [   HTTP 기본 코드  ]
    SUCCESS(200, true, "요청에 성공했어요."),
    BAD_REQUEST(400, false, "요청 내용을 다시 확인해 주세요."),
    UNAUTHORIZED(401, false, "로그인이 필요해요."),
    FORBIDDEN(403, false, "접근 권한이 없어요."),
    NOT_FOUND(404, false, "찾을 수 없는 페이지예요."),
    INTERNAL_SERVER_ERROR(500, false, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),
    //endregion
    //region    [   1000번대: user 관련  ]
    SUCCESS_SIGNUP(1000, true, "회원가입에 성공했어요."),
    SUCCESS_SIGNIN(1001, true, "로그인에 성공했어요."),
    DUPLICATED_USER_EMAIL(1002, false, "이미 사용 중인 이메일이에요."),
    NOT_FOUND_AUTHORIZATION(1003, false, "인증 헤더가 존재하지 않아요."),
    INVALID_AUTHORIZATION(1004, false, "인증 헤더가 Bearer 방식이 아니에요."),
    INVALID_TOKEN(1005, false, "유효하지 않은 토큰이에요."),
    EXPIRED_TOKEN(1006, false, "만료된 토큰이에요."),
    INVALID_USER_EMAIL(1007, false, "올바르지 않은 이메일이에요."),
    INVALID_PASSWORD(1008, false, "올바르지 않은 패스워드예요.");
    //endregion
    private final Integer code;
    private final Boolean success;
    private final String message;
    ResponseCode(Integer code, Boolean success, String message){
        this.code = code;
        this.success = success;
        this.message = message;
    }
}
