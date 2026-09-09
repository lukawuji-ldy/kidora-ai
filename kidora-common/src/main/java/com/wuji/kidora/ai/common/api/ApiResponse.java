package com.wuji.kidora.ai.common.api;

/**
 * 统一 JSON 响应包装。
 *
 * @param code    业务码，成功为 OK
 * @param message 提示信息
 * @param data    载荷
 * @param <T>     数据类型
 *
 * @author liudy
 */
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
