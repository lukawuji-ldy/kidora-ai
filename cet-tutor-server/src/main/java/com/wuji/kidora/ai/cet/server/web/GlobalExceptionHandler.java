package com.wuji.kidora.ai.cet.server.web;

import com.wuji.kidora.ai.common.api.ApiResponse;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GlobalExceptionHandler.
 *
 * @author liudy
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KidoraException.class)
    public ResponseEntity<ApiResponse<Void>> handleKidora(KidoraException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, FORBIDDEN_LEARNER -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BAD_REQUEST, CET_INVALID_STATE, CET_SAFETY_BLOCKED -> HttpStatus.BAD_REQUEST;
            case MODEL_UNAVAILABLE, MODEL_TIMEOUT, MODEL_RATE_LIMITED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
