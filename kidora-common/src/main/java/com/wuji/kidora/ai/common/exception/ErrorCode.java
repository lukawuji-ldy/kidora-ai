package com.wuji.kidora.ai.common.exception;

/**
 * 统一业务错误码。
 *
 * @author liudy
 */
public enum ErrorCode {

    BAD_REQUEST("BAD_REQUEST", "请求参数非法"),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或令牌无效"),
    FORBIDDEN("FORBIDDEN", "无权限"),
    FORBIDDEN_LEARNER("FORBIDDEN_LEARNER", "学习者不属于当前用户"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    MODEL_TIMEOUT("MODEL_TIMEOUT", "模型调用超时"),
    MODEL_RATE_LIMITED("MODEL_RATE_LIMITED", "模型限流"),
    MODEL_UNAVAILABLE("MODEL_UNAVAILABLE", "模型不可用"),
    AGENT_MAX_ITERATIONS("AGENT_MAX_ITERATIONS", "Agent 达到最大执行次数"),
    CET_SAFETY_BLOCKED("CET_SAFETY_BLOCKED", "内容不符合安全策略"),
    CET_INVALID_STATE("CET_INVALID_STATE", "课时状态不允许此操作"),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
