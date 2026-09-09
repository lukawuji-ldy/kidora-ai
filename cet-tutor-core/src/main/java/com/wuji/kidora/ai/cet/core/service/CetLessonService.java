package com.wuji.kidora.ai.cet.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.cet.core.domain.LessonSession;
import com.wuji.kidora.ai.cet.core.domain.LessonStateMachine;
import com.wuji.kidora.ai.cet.core.domain.LessonStatus;
import com.wuji.kidora.ai.cet.core.eval.SessionEvaluator;
import com.wuji.kidora.ai.cet.core.planner.LessonPlanner;
import com.wuji.kidora.ai.cet.core.repo.CetAssessmentRepository;
import com.wuji.kidora.ai.cet.core.repo.CetLessonSessionRepository;
import com.wuji.kidora.ai.cet.core.repo.CetSafetyEventRepository;
import com.wuji.kidora.ai.cet.core.repo.CetSessionReportRepository;
import com.wuji.kidora.ai.cet.core.repo.CetTrainingPlanRepository;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import com.wuji.kidora.ai.cet.core.safety.SafetyAction;
import com.wuji.kidora.ai.cet.core.safety.SafetyDecision;
import com.wuji.kidora.ai.cet.core.safety.SafetyGuard;
import com.wuji.kidora.ai.cet.core.tutor.TutorLoop;
import com.wuji.kidora.ai.cet.core.speech.CetStreamEvent;
import com.wuji.kidora.ai.cet.core.speech.SpeechToolPort;
import com.wuji.kidora.ai.cet.core.speech.TurnInput;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import com.wuji.kidora.ai.memory.repo.LearnerProfileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CET 课时编排门面（开课 / 小循环 / 结课）。
 *
 * @author liudy
 */
@Service
public class CetLessonService {

    static final String SOFT_INPUT_REDIRECT =
            "Let's talk about something fun instead! What is your favorite animal?";

    private final LearnerProfileRepository learnerProfileRepository;
    private final CetLessonSessionRepository lessonSessionRepository;
    private final CetTrainingPlanRepository trainingPlanRepository;
    private final CetTutorTurnRepository tutorTurnRepository;
    private final CetAssessmentRepository assessmentRepository;
    private final CetSessionReportRepository sessionReportRepository;
    private final CetSafetyEventRepository safetyEventRepository;
    private final SafetyGuard safetyGuard;
    private final LessonPlanner lessonPlanner;
    private final TutorLoop tutorLoop;
    private final SessionEvaluator sessionEvaluator;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<SpeechToolPort> speechToolPort;

    public CetLessonService(LearnerProfileRepository learnerProfileRepository,
                            CetLessonSessionRepository lessonSessionRepository,
                            CetTrainingPlanRepository trainingPlanRepository,
                            CetTutorTurnRepository tutorTurnRepository,
                            CetAssessmentRepository assessmentRepository,
                            CetSessionReportRepository sessionReportRepository,
                            CetSafetyEventRepository safetyEventRepository,
                            SafetyGuard safetyGuard,
                            LessonPlanner lessonPlanner,
                            TutorLoop tutorLoop,
                            SessionEvaluator sessionEvaluator,
                            ObjectMapper objectMapper,
                            ObjectProvider<SpeechToolPort> speechToolPort) {
        this.learnerProfileRepository = learnerProfileRepository;
        this.lessonSessionRepository = lessonSessionRepository;
        this.trainingPlanRepository = trainingPlanRepository;
        this.tutorTurnRepository = tutorTurnRepository;
        this.assessmentRepository = assessmentRepository;
        this.sessionReportRepository = sessionReportRepository;
        this.safetyEventRepository = safetyEventRepository;
        this.safetyGuard = safetyGuard;
        this.lessonPlanner = lessonPlanner;
        this.tutorLoop = tutorLoop;
        this.sessionEvaluator = sessionEvaluator;
        this.objectMapper = objectMapper;
        this.speechToolPort = speechToolPort;
    }

    public OpenResult openSession(String userId, String learnerId, String topic, String personaId) {
        if (!StringUtils.hasText(topic)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "topic 不能为空");
        }
        LearnerProfile profile = learnerProfileRepository.requireOwned(learnerId, userId);
        ModelRouter.CallContext ctx = baseCtx(userId, learnerId, null);

        SafetyDecision topicSafety = safetyGuard.checkInput(topic, ctx);
        if (topicSafety.isHardBlock()) {
            persistSafety(null, null, learnerId, userId, "INPUT", topicSafety);
            throw new KidoraException(ErrorCode.CET_SAFETY_BLOCKED, "主题不符合安全策略，请换一个话题");
        }

        LessonSession session = lessonSessionRepository.insert(
                userId, learnerId, topic, personaId, profile.cefrLevel());
        LessonStateMachine.assertTransition(LessonStatus.CREATED, LessonStatus.PLANNING);
        lessonSessionRepository.updateStatus(session.lessonSessionId(), LessonStatus.PLANNING);

        ModelRouter.CallContext planCtx = baseCtx(userId, learnerId, session.lessonSessionId());
        LessonPlanner.PlanResult plan = lessonPlanner.plan(profile, topic, personaId, planCtx);
        String planId = trainingPlanRepository.insert(session.lessonSessionId(), learnerId, plan.planJson());

        LessonStateMachine.assertTransition(LessonStatus.PLANNING, LessonStatus.PRACTICING);
        lessonSessionRepository.updateStatusAndPlan(session.lessonSessionId(), LessonStatus.PRACTICING, planId);

        return new OpenResult(session.lessonSessionId(), LessonStatus.PRACTICING.name(), plan.childSummary(), planId);
    }

    public Flux<CetStreamEvent> streamTurn(String userId, String lessonSessionId, TurnInput input) {
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        if (session.status() != LessonStatus.PRACTICING) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "当前状态不可陪练: " + session.status());
        }
        ModelRouter.CallContext ctx = baseCtx(userId, session.learnerId(), lessonSessionId);

        String childText = resolveChildText(input, speechToolPort.getIfAvailable());
        String rawAudio = input == null ? null : input.audioBase64();
        String locale = input == null ? null : input.locale();
        String referenceText = input == null ? null : input.referenceText();

        SafetyDecision in = safetyGuard.checkInput(childText, ctx);
        if (in.isHardBlock()) {
            persistSafety(lessonSessionId, null, session.learnerId(), userId, "INPUT", in);
            LessonStateMachine.assertTransition(LessonStatus.PRACTICING, LessonStatus.SAFETY_BLOCKED);
            lessonSessionRepository.markEnded(lessonSessionId, LessonStatus.SAFETY_BLOCKED);
            return Flux.error(new KidoraException(ErrorCode.CET_SAFETY_BLOCKED, "这条内容不太合适，我们换个话题吧"));
        }
        String softRedirect = softInputRedirectOrEmpty(in);
        if (softRedirect != null) {
            persistSafety(lessonSessionId, null, session.learnerId(), userId, "INPUT", in);
            int turnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
            tutorTurnRepository.insert(lessonSessionId, session.learnerId(), turnIndex, "warmup",
                    softRedirect, childText);
            return Flux.just(CetStreamEvent.delta(softRedirect));
        }
        String effectiveChild = in.action() == SafetyAction.REWRITE && StringUtils.hasText(in.rewriteText())
                ? in.rewriteText() : childText;

        String planJson = trainingPlanRepository.findActivePlanJson(session.activePlanId())
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "训练计划不存在"));
        List<CetTutorTurnRepository.TurnRow> recent = tutorTurnRepository.listRecent(lessonSessionId, 6);
        int turnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
        String stageId = tutorLoop.resolveStageId(planJson, recent);
        String turnId = tutorTurnRepository.insert(
                lessonSessionId, session.learnerId(), turnIndex, stageId, null, effectiveChild);

        String rawTutor = tutorLoop.generateReply(planJson, session.cefrLevel(), session.personaId(),
                effectiveChild, recent, ctx);
        SafetyDecision out = safetyGuard.checkOutput(rawTutor, ctx);
        String finalText = applyOutputGate(out, rawTutor);
        if (out.action() != SafetyAction.ALLOW) {
            persistSafety(lessonSessionId, turnId, session.learnerId(), userId, "OUTPUT", out);
        }
        tutorTurnRepository.updateTutorText(turnId, finalText);

        Flux<CetStreamEvent> deltas = TutorLoop.chunkForSse(finalText).map(CetStreamEvent::delta);
        SpeechToolPort port = speechToolPort.getIfAvailable();
        if (port == null) {
            return deltas;
        }
        return deltas.concatWith(Flux.defer(() -> Flux.fromIterable(
                buildSpeechExtras(port, finalText, rawAudio, locale, referenceText))));
    }

    /**
     * 解析儿童文本：优先 ASR，否则 text。
     *
     * @param input 输入
     * @param port  语音端口（可空）
     * @return 文本
     */
    static String resolveChildText(TurnInput input, SpeechToolPort port) {
        if (input != null && StringUtils.hasText(input.audioBase64())) {
            if (port == null) {
                throw new KidoraException(ErrorCode.BAD_REQUEST, "语音输入需要启用 MCP 语音能力");
            }
            Optional<SpeechToolPort.AsrResult> asr = port.asr(input.audioBase64(), input.locale());
            String text = asr.map(SpeechToolPort.AsrResult::text).filter(StringUtils::hasText).orElse(null);
            if (!StringUtils.hasText(text)) {
                throw new KidoraException(ErrorCode.BAD_REQUEST, "ASR 未能识别语音");
            }
            return text;
        }
        if (input != null && StringUtils.hasText(input.text())) {
            return input.text().trim();
        }
        throw new KidoraException(ErrorCode.BAD_REQUEST, "text 或 audioBase64 必填其一");
    }

    /**
     * 构建 TTS / 发音附加事件。
     */
    static List<CetStreamEvent> buildSpeechExtras(SpeechToolPort port, String finalText,
                                                  String rawAudio, String locale, String referenceText) {
        List<CetStreamEvent> extras = new ArrayList<>();
        if (port == null) {
            return extras;
        }
        port.tts(finalText, null, locale).ifPresent(tts -> extras.add(CetStreamEvent.tts(
                "{\"audioBase64\":\"" + jsonEscape(nullToEmpty(tts.audioBase64()))
                        + "\",\"mimeType\":\"" + jsonEscape(nullToEmpty(tts.mimeType()))
                        + "\",\"provider\":\"" + jsonEscape(nullToEmpty(tts.provider())) + "\"}")));
        if (StringUtils.hasText(rawAudio) && StringUtils.hasText(referenceText)) {
            port.score(rawAudio, referenceText, locale).ifPresent(p -> extras.add(CetStreamEvent.pronunciation(
                    "{\"overall\":" + p.overall() + ",\"accuracy\":" + p.accuracy()
                            + ",\"fluency\":" + p.fluency() + ",\"completeness\":" + p.completeness()
                            + ",\"provider\":\"" + jsonEscape(nullToEmpty(p.provider())) + "\"}")));
        }
        return extras;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String jsonEscape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 输出闸门：HARD/SOFT 替换为儿童友好句；REWRITE 用改写文。
     *
     * @author liudy
     */
    static String applyOutputGate(SafetyDecision out, String rawTutor) {
        if (out == null) {
            return rawTutor;
        }
        if (out.isHardBlock() || out.isSoftBlock()) {
            return "Great try! Let's practice a safer sentence together.";
        }
        if (out.action() == SafetyAction.REWRITE && StringUtils.hasText(out.rewriteText())) {
            return out.rewriteText();
        }
        return rawTutor;
    }

    public CompleteResult complete(String userId, String lessonSessionId) {
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        if (session.status() != LessonStatus.PRACTICING && session.status() != LessonStatus.EVALUATING) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "当前状态不可结课: " + session.status());
        }
        if (session.status() == LessonStatus.PRACTICING) {
            LessonStateMachine.assertTransition(LessonStatus.PRACTICING, LessonStatus.EVALUATING);
            lessonSessionRepository.updateStatus(lessonSessionId, LessonStatus.EVALUATING);
        }
        ModelRouter.CallContext ctx = baseCtx(userId, session.learnerId(), lessonSessionId);
        List<CetTutorTurnRepository.TurnRow> turns = tutorTurnRepository.listAll(lessonSessionId);
        SessionEvaluator.EvalResult eval = sessionEvaluator.evaluate(session.topic(), session.cefrLevel(), turns, ctx);
        assessmentRepository.insertSession(lessonSessionId, eval.assessmentJson());
        sessionReportRepository.upsert(lessonSessionId, session.learnerId(), eval.childSummary(), eval.assessmentJson());
        LessonStateMachine.assertTransition(LessonStatus.EVALUATING, LessonStatus.COMPLETED);
        lessonSessionRepository.markEnded(lessonSessionId, LessonStatus.COMPLETED);
        return new CompleteResult(lessonSessionId, LessonStatus.COMPLETED.name(), eval.childSummary(), eval.assessmentJson());
    }

    public ReportResult getReport(String userId, String lessonSessionId) {
        requireOwnedSession(userId, lessonSessionId);
        CetSessionReportRepository.ReportRow report = sessionReportRepository.findBySession(lessonSessionId)
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "报告不存在，请先结课"));
        return new ReportResult(report.childSummary(), report.reportJson());
    }

    private LessonSession requireOwnedSession(String userId, String lessonSessionId) {
        LessonSession session = lessonSessionRepository.findById(lessonSessionId)
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertSessionOwned(userId, session.userId());
        return session;
    }

    /**
     * 会话归属校验（可单测）。
     */
    static void assertSessionOwned(String userId, String sessionUserId) {
        if (userId == null || !userId.equals(sessionUserId)) {
            throw new KidoraException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
    }

    /**
     * SOFT 输入闸门：返回儿童友好 redirect；非 SOFT 返回 null。
     */
    static String softInputRedirectOrEmpty(SafetyDecision in) {
        if (in != null && in.isSoftBlock()) {
            return SOFT_INPUT_REDIRECT;
        }
        return null;
    }

    private void persistSafety(String lessonSessionId, String turnId, String learnerId, String userId,
                               String direction, SafetyDecision decision) {
        try {
            String detail = objectMapper.writeValueAsString(Map.of(
                    "eventType", decision.eventType(),
                    "reason", decision.reason() == null ? "" : decision.reason(),
                    "policyVersion", decision.policyVersion() == null
                            ? SafetyDecision.POLICY_VERSION : decision.policyVersion()
            ));
            String action = switch (decision.action()) {
                case HARD_BLOCK -> "BLOCK";
                case SOFT_BLOCK -> "BLOCK";
                case REWRITE -> "REWRITE";
                case ALLOW -> "LOG";
            };
            safetyEventRepository.insert(lessonSessionId, turnId, learnerId, userId,
                    direction, decision.eventType(), action, detail);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static ModelRouter.CallContext baseCtx(String userId, String learnerId, String sessionId) {
        return new ModelRouter.CallContext(
                IdGenerator.nextBizId("tr_"),
                sessionId,
                null,
                userId,
                learnerId,
                "CET",
                sessionId,
                "CET"
        );
    }

    public record OpenResult(String sessionId, String status, String planSummary, String planId) {
    }

    public record CompleteResult(String sessionId, String status, String childSummary, String assessmentJson) {
    }

    public record ReportResult(String childSummary, String assessment) {
    }
}
