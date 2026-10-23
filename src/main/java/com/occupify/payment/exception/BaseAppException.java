package com.occupify.payment.exception;

import lombok.Getter;

@Getter
public class BaseAppException extends RuntimeException {
    private final AppErrorCode errorCode;

    public BaseAppException(AppErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseAppException(AppErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public BaseAppException(AppErrorCode errorCode, Object... args) {
        super(formatSafely(errorCode.getMessage(), args));
        this.errorCode = errorCode;
    }

    private static String formatSafely(String template, Object... args) {
        if (args == null || args.length == 0 || !template.contains("%")) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (Exception ex) {
            return template;
        }
    }
}
