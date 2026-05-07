package com.teacheragent.common;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long currentId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getId();
    }

    public static boolean isAdmin() {
        CurrentUser u = HOLDER.get();
        return u != null && u.isAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
