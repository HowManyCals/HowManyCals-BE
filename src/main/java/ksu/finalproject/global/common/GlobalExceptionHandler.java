package ksu.finalproject.global.common;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

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

    @ExceptionHandler(MissingServletRequestPartException.class)
    public CommonResponse<?> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        return new CommonResponse<>(ResponseCode.EMPTY_FOOD_IMAGE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public CommonResponse<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return new CommonResponse<>(ResponseCode.FOOD_IMAGE_SIZE_EXCEEDED);
    }
}

