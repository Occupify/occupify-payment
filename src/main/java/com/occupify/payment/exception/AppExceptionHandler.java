package com.occupify.payment.exception;

import com.occupify.payment.dto.response.ResponseFactory;
import com.occupify.payment.dto.response.base.DetailedErrorResponse;
import com.occupify.payment.dto.response.base.ErrorResponse;
import com.occupify.payment.exception.error.WebErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class AppExceptionHandler {

    private final ResponseFactory responseFactory;

    // 1. Business Logic Exceptions
    @ExceptionHandler(BaseAppException.class)
    public ResponseEntity<ErrorResponse> handleBaseAppException(
            BaseAppException e, HttpServletRequest request) {
        AppErrorCode errorCode = e.getErrorCode();

        if (errorCode.getHttpStatus().is4xxClientError()) {
            log.warn("Business Exception [{}] at {}: {}", errorCode.getErrorCode(), request.getRequestURI(), e.getMessage());
        } else {
            log.error("Internal Business Error [{}] at {}: {}", errorCode.getErrorCode(), request.getRequestURI(), e.getMessage(), e);
        }

        ErrorResponse response = responseFactory.createErrorResponse(
                errorCode.getHttpStatus(),
                errorCode.getErrorCode(),
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    // 2. DTO Validation Exceptions (@Valid @RequestBody)
    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<DetailedErrorResponse> handleValidationException(
            Exception e, HttpServletRequest request) {
        log.warn("Validation failed at: {}", request.getRequestURI());
        Map<String, List<String>> errors = new HashMap<>();

        BindingResult bindingResult = (e instanceof BindException be)
                ? be.getBindingResult()
                : ((MethodArgumentNotValidException) e).getBindingResult();

        if (bindingResult != null) {
            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                errors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>())
                        .add(fieldError.getDefaultMessage());
            }
            for (ObjectError globalError : bindingResult.getGlobalErrors()) {
                errors.computeIfAbsent(globalError.getObjectName(), k -> new ArrayList<>())
                        .add(globalError.getDefaultMessage());
            }
        }

        DetailedErrorResponse response = responseFactory.createDetailedErrorResponse(
                WebErrorCode.VALIDATION_FAILED.getHttpStatus(),
                WebErrorCode.VALIDATION_FAILED.getErrorCode(),
                WebErrorCode.VALIDATION_FAILED.getMessage(),
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. PathVariable and Param Validation (@Validated on controller class)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<DetailedErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {
        log.warn("Constraint violation at: {}", request.getRequestURI());
        Map<String, List<String>> errors = new HashMap<>();

        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            errors.computeIfAbsent(propertyPath, k -> new ArrayList<>())
                    .add(violation.getMessage());
        }

        DetailedErrorResponse response = responseFactory.createDetailedErrorResponse(
                WebErrorCode.VALIDATION_FAILED.getHttpStatus(),
                WebErrorCode.VALIDATION_FAILED.getErrorCode(),
                WebErrorCode.VALIDATION_FAILED.getMessage(),
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 4. Bad Request / Message Not Readable
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("Bad Request at {}: {}", request.getRequestURI(), e.getMessage());
        return buildFromAppErrorCode(WebErrorCode.BAD_REQUEST, request.getRequestURI(), "Dữ liệu yêu cầu không hợp lệ hoặc sai định dạng");
    }

    // 5. Method Not Allowed (405)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return buildFromAppErrorCode(WebErrorCode.METHOD_NOT_ALLOWED, request.getRequestURI());
    }

    // 6. Endpoint Not Found (404)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {
        return buildFromAppErrorCode(WebErrorCode.NOT_FOUND, request.getRequestURI());
    }

    // 7. Catch-all: Unexpected Unhandled Exceptions (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknownException(
            Exception e, HttpServletRequest request) {
        log.error("Unhandled Server Exception at: {}", request.getRequestURI(), e);
        return buildFromAppErrorCode(
                WebErrorCode.INTERNAL_SERVER_ERROR,
                request.getRequestURI(),
                "Hệ thống gặp sự cố ngoài dự kiến, vui lòng thử lại sau"
        );
    }

    private ResponseEntity<ErrorResponse> buildFromAppErrorCode(AppErrorCode errorCode, String path) {
        return buildFromAppErrorCode(errorCode, path, errorCode.getMessage());
    }

    private ResponseEntity<ErrorResponse> buildFromAppErrorCode(
            AppErrorCode errorCode, String path, String customMessage) {
        ErrorResponse response = responseFactory.createErrorResponse(
                errorCode.getHttpStatus(), errorCode.getErrorCode(), customMessage, path);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }
}
