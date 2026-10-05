package com.menusolomon.common.web;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.vote.domain.VoteClosingTimeException;
import java.util.Map;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ProblemDetail> handleInvalidParameter(Exception exception) {
        return problem(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDetail(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        var response = problem(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDetail(), null);
        var headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        if (exception.getSupportedHttpMethods() != null) headers.setAllow(exception.getSupportedHttpMethods());
        return new ResponseEntity<>(response.getBody(), headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(VoteClosingTimeException.class)
    public ResponseEntity<ProblemDetail> handleVoteClosingTime(VoteClosingTimeException exception) {
        return problem(ErrorCode.VALIDATION_ERROR, "마감 시간은 현재 시각 이후여야 합니다.",
                Map.of("closesAt", "선택한 마감 시간이 지났습니다. 다시 설정해 주세요."));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return problem(errorCode, errorCode.getDetail(), null);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ProblemDetail> handleMissingSessionCookie(
            MissingRequestCookieException exception
    ) {
        return problem(ErrorCode.SESSION_REQUIRED, ErrorCode.SESSION_REQUIRED.getDetail(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, ignored) -> first
                ));
        return problem(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDetail(), fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception) {
        return problem(
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.getDetail(),
                null
        );
    }

    private ResponseEntity<ProblemDetail> problem(
            ErrorCode errorCode,
            String detail,
            Map<String, String> fieldErrors
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detail);
        problemDetail.setProperty("code", errorCode.name());
        if (fieldErrors != null) {
            problemDetail.setProperty("fieldErrors", fieldErrors);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problemDetail, headers, errorCode.getStatus());
    }
}
