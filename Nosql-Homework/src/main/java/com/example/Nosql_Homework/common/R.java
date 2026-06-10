package com.example.Nosql_Homework.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码: 0=成功, 非0=失败 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 响应数据 */
    private T data;

    // ========== 成功 ==========

    public static <T> R<T> ok() {
        return R.<T>builder().code(0).msg("ok").build();
    }

    public static <T> R<T> ok(T data) {
        return R.<T>builder().code(0).msg("ok").data(data).build();
    }

    public static <T> R<T> ok(String msg, T data) {
        return R.<T>builder().code(0).msg(msg).data(data).build();
    }

    // ========== 失败 ==========

    public static <T> R<T> fail(String msg) {
        return R.<T>builder().code(500).msg(msg).build();
    }

    public static <T> R<T> fail(int code, String msg) {
        return R.<T>builder().code(code).msg(msg).build();
    }
}
