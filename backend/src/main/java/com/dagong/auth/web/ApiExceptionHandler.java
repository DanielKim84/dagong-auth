package com.dagong.auth.web;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** API 오류를 ProblemDetail로 바꿉니다. 맛다공 화면은 detail의 한국어 문구를 그대로 보여줍니다. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail responseStatus(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidArgument(MethodArgumentNotValidException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, firstMessage(exception.getBindingResult()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 내용을 확인해 주세요.");
    }

    /** 여러 항목이 틀렸으면 화면 입력 순서(record 필드 순서)대로 첫 항목의 문구를 돌려줍니다. */
    private static String firstMessage(BindingResult result) {
        List<String> fieldOrder = result.getTarget() instanceof Record record
                ? Arrays.stream(record.getClass().getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
                : List.of();
        return result.getFieldErrors().stream()
                .min(Comparator.comparingInt(error -> fieldIndex(fieldOrder, error)))
                .map(FieldError::getDefaultMessage)
                .orElse("요청 내용을 확인해 주세요.");
    }

    private static int fieldIndex(List<String> fieldOrder, FieldError error) {
        int index = fieldOrder.indexOf(error.getField());
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
