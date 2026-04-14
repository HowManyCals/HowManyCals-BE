package ksu.finalproject.global.common;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public CommonResponse<?> handleCustomException(CustomException e) {
        return new CommonResponse<>(e.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResponse<List<String>> handleValidationException(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        return new CommonResponse<>(ResponseCode.BAD_REQUEST, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public CommonResponse<?> handleUnknownEnumException(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();

        if (cause instanceof InvalidFormatException invalidEx) {

            // enum 타입인지 확인
            if (invalidEx.getTargetType().isEnum()) {

                // 어떤 enum인지 + 허용 값 추출
                Class<?> enumClass = invalidEx.getTargetType();
                Object[] enumConstants = enumClass.getEnumConstants();

                String allowedValues = Arrays.stream(enumConstants)
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));

                String message = String.format(
                        "잘못된 enum 값이에요. 허용 값: [%s]",
                        allowedValues);

                return new CommonResponse<>(ResponseCode.INVALID_ENUM, message);
            }
        }
        return new CommonResponse<>(ResponseCode.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public CommonResponse<?> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        return new CommonResponse<>(ResponseCode.EMPTY_FOOD_IMAGE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public CommonResponse<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return new CommonResponse<>(ResponseCode.FOOD_IMAGE_SIZE_EXCEEDED);
    }
}
