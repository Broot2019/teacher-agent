package com.teacheragent.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应封装
 */
@Data
public class R<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;

    public R() {}

    public R(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(200, "成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "成功", data);
    }

    public static <T> R<T> ok(T data, String message) {
        return new R<>(200, message, data);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(500, message, null);
    }

    public static <T> R<T> fail(Integer code, String message) {
        return new R<>(code, message, null);
    }
}
