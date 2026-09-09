package com.wuji.kidora.ai.cet.core.safety;

/**
 * @param action        动作
 * @param eventType     命中类型
 * @param reason        原因（勿含完整敏感原文）
 * @param rewriteText   REWRITE 时的改写文本
 * @param policyVersion 策略版本号
 *
 * @author liudy
 */
public record SafetyDecision(
        SafetyAction action,
        String eventType,
        String reason,
        String rewriteText,
        String policyVersion
) {
    public static final String POLICY_VERSION = "cet-safety-v1";

    public SafetyDecision(SafetyAction action, String eventType, String reason, String rewriteText) {
        this(action, eventType, reason, rewriteText, POLICY_VERSION);
    }

    public static SafetyDecision allow() {
        return new SafetyDecision(SafetyAction.ALLOW, "NONE", "ok", null, POLICY_VERSION);
    }

    public static SafetyDecision softUnavailable(String reason) {
        return new SafetyDecision(SafetyAction.SOFT_BLOCK, "L1_UNAVAILABLE", reason, null, POLICY_VERSION);
    }

    public boolean isHardBlock() {
        return action == SafetyAction.HARD_BLOCK;
    }

    public boolean isSoftBlock() {
        return action == SafetyAction.SOFT_BLOCK;
    }
}
