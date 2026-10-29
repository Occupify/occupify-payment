package com.occupify.payment.security;

import java.util.UUID;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void setContext(UserContext userContext) {
        CONTEXT.set(userContext);
    }

    public static UserContext getContext() {
        return CONTEXT.get();
    }

    public static UUID getUserId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    public static String getEmail() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getEmail() : null;
    }

    public static String getRole() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getRole() : null;
    }

    public static String getCorrelationId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getCorrelationId() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
