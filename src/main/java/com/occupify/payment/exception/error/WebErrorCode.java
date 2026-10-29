package com.occupify.payment.exception.error;

import com.occupify.payment.exception.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WebErrorCode implements AppErrorCode {
    BAD_REQUEST("WEB_400", "Bad Request: The request is invalid", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("WEB_400_VAL", "Validation error: Invalid input data", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("WEB_401", "Unauthorized: Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("WEB_403", "Access Denied: You do not have permission", HttpStatus.FORBIDDEN),
    NOT_FOUND("WEB_404", "Not Found: The requested resource was not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("WEB_405", "Method Not Allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("WEB_415", "Unsupported Media Type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    TOO_MANY_REQUESTS("WEB_429", "Too Many Requests: Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_SERVER_ERROR("WEB_500", "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;
}
