package com.occupify.payment.exception;

import org.springframework.http.HttpStatus;

public interface AppErrorCode {
    String getErrorCode();
    String getMessage();
    HttpStatus getHttpStatus();
}
