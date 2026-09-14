package com.wuji.kidora.ai.cet.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.cet.core.domain.LessonSession;
import com.wuji.kidora.ai.cet.core.domain.LessonStateMachine;
import com.wuji.kidora.ai.cet.core.domain.LessonStatus;
import com.wuji.kidora.ai.cet.core.eval.SessionEvaluator;
import com.wuji.kidora.ai.cet.core.planner.LessonPlanner;
import com.wuji.kidora.ai.cet.core.planner.LessonReplanner;
import com.wuji.kidora.ai.cet.core.planner.PlanLearningHints;
import com.wuji.kidora.ai.cet.core.props.PropAssetResolver;
import com.wuji.kidora.ai.cet.core.props.PropStageDirector;
import com.wuji.kidora.ai.cet.core.props.SessionPropContext;
import com.wuji.kidora.ai.cet.core.repo.CetAssessmentRepository;
import com.wuji.kidora.ai.cet.core.repo.CetLessonSessionRepository;
import com.wuji.kidora.ai.cet.core.repo.CetPersonaVoiceRepository;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetGenerationTaskRepository;
import com.wuji.kidora.ai.cet.core.repo.CetSafetyEventRepository;
import com.wuji.kidora.ai.cet.core.repo.CetSessionReportRepository;
import com.wuji.kidora.ai.cet.core.repo.CetSpeechRouteRepository;
import com.wuji.kidora.ai.cet.core.repo.CetTrainingPlanRepository;
import com.wuji.kidora.ai.cet.core.repo.CetTrainingPlanRevisionRepository;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import com.wuji.kidora.ai.cet.core.safety.SafetyAction;
import com.wuji.kidora.ai.cet.core.safety.SafetyDecision;
import com.wuji.kidora.ai.cet.core.safety.SafetyGuard;
import com.wuji.kidora.ai.cet.core.speech.CetStreamEvent;
import com.wuji.kidora.ai.cet.core.speech.SpeechToolPort;
import com.wuji.kidora.ai.cet.core.speech.TurnInput;
import com.wuji.kidora.ai.cet.core.speech.TurnTimingCollector;
import com.wuji.kidora.ai.cet.core.tutor.TutorLoop;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import com.wuji.kidora.ai.memory.repo.LearnerProfileRepository;
import com.wuji.kidora.ai.memory.service.LearnerMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CET 课时编排门面（开课 / 小循环 / 阶段 Re-plan / 结课）。
 *
 * @author liudy
 */
@Service
public class CetLessonService {

    private static final Logger log = LoggerFactory.getLogger(CetLessonService.class);

    static final String SOFT_INPUT_REDIRECT =
            "Let's talk about something fun instead! What is your favorite animal?";

    /** client-timing 上限（10 分钟）。 */
    static final int MAX_E2E_HEARD_MS = 600_000;

    private final LearnerProfileRepository learnerProfileRepository;
    private final CetLessonSessionRepository lessonSessionRepository;
    private final CetTrainingPlanRepository trainingPlanRepository;
    private final CetTrainingPlanRevisionRepository planRevisionRepository;
    private final CetTutorTurnRepository tutorTurnRepository;
    private final CetAssessmentRepository assessmentRepository;
    private final CetSessionReportRepository sessionReportRepository;
    private final CetSafetyEventRepository safetyEventRepository;
    private final SafetyGuard safetyGuard;
    private final LessonPlanner lessonPlanner;
    private final LessonReplanner lessonReplanner;
    private final TutorLoop tutorLoop;
    private final SessionEvaluator sessionEvaluator;
    private final LearnerMemoryService learnerMemoryService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<SpeechToolPort> speechToolPort;
    private final CetPersonaVoiceRepository personaVoiceRepository;
    private final CetPropAssetGenerationTaskRepository propGenerationTaskRepository;
    private final CetSpeechRouteRepository speechRouteRepository;
    private final PropAssetResolver propAssetResolver;
    private final PropStageDirector propStageDirector;
    private final SessionPropContext sessionPropContext;

    public CetLessonService(LearnerProfileRepository learnerProfileRepository,
                            CetLessonSessionRepository lessonSessionRepository,
                            CetTrainingPlanRepository trainingPlanRepository,
                            CetTrainingPlanRevisionRepository planRevisionRepository,
                            CetTutorTurnRepository tutorTurnRepository,
                            CetAssessmentRepository assessmentRepository,
                            CetSessionReportRepository sessionReportRepository,
                            CetSafetyEventRepository safetyEventRepository,
                            SafetyGuard safetyGuard,
                            LessonPlanner lessonPlanner,
                            LessonReplanner lessonReplanner,
                            TutorLoop tutorLoop,
                            SessionEvaluator sessionEvaluator,
                            LearnerMemoryService learnerMemoryService,
                            ObjectMapper objectMapper,
                            ObjectProvider<SpeechToolPort> speechToolPort,
                            CetPersonaVoiceRepository personaVoiceRepository,
                            CetPropAssetGenerationTaskRepository propGenerationTaskRepository,
                            CetSpeechRouteRepository speechRouteRepository,
                            PropAssetResolver propAssetResolver,
                            PropStageDirector propStageDirector,
                            SessionPropContext sessionPropContext) {
        this.learnerProfileRepository = learnerProfileRepository;
        this.lessonSessionRepository = lessonSessionRepository;
        this.trainingPlanRepository = trainingPlanRepository;
        this.planRevisionRepository = planRevisionRepository;
        this.tutorTurnRepository = tutorTurnRepository;
        this.assessmentRepository = assessmentRepository;
        this.sessionReportRepository = sessionReportRepository;
        this.safetyEventRepository = safetyEventRepository;
        this.safetyGuard = safetyGuard;
        this.lessonPlanner = lessonPlanner;
        this.lessonReplanner = lessonReplanner;
        this.tutorLoop = tutorLoop;
        this.sessionEvaluator = sessionEvaluator;
        this.learnerMemoryService = learnerMemoryService;
        this.objectMapper = objectMapper;
        this.speechToolPort = speechToolPort;
        this.personaVoiceRepository = personaVoiceRepository;
        this.propGenerationTaskRepository = propGenerationTaskRepository;
        this.speechRouteRepository = speechRouteRepository;
        this.propAssetResolver = propAssetResolver;
        this.propStageDirector = propStageDirector;
        this.sessionPropContext = sessionPropContext;
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
        lessonSessionRepository.updateExtraJson(session.lessonSessionId(), "{\"lastStageEvalTurn\":0}");

        JsonNode planRoot = parsePlanRoot(plan.planJson());
        List<String> vocabHints = PlanLearningHints.vocabHints(planRoot);
        List<String> propHints = PlanLearningHints.propCandidateHints(planRoot, topic);
        PropAssetResolver.PropResolution propResolution =
                propAssetResolver.resolveWithMissing(propHints);
        try {
            propGenerationTaskRepository.enqueueMissing(
                    propResolution.missingLemmas(),
                    PlanLearningHints.propTheme(planRoot, topic),
                    planId,
                    session.lessonSessionId());
        } catch (RuntimeException e) {
            log.warn("CET prop generation task enqueue failed, sessionId={}",
                    session.lessonSessionId(), e);
        }
        return new OpenResult(
                session.lessonSessionId(),
                LessonStatus.PRACTICING.name(),
                plan.childSummary(),
                planId,
                PlanLearningHints.childGoals(planRoot, topic),
                vocabHints,
                propResolution.assets());
    }

    /**
     * 开场外教先说：无儿童输入，落库 child_text=null，可选 TTS。禁止 Planner。
     *
     * @param userId          用户
     * @param lessonSessionId 会话
     * @return SSE 事件流
     * @author liudy
     */
    public Flux<CetStreamEvent> streamOpening(String userId, String lessonSessionId) {
        TurnTimingCollector timing = new TurnTimingCollector("opening", "text");
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        if (session.status() != LessonStatus.PRACTICING) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "当前状态不可开场: " + session.status());
        }
        assertOpeningAllowed(tutorTurnRepository.listAll(lessonSessionId).size());

        ModelRouter.CallContext ctx = baseCtx(userId, session.learnerId(), lessonSessionId);
        String planJson = trainingPlanRepository.findActivePlanJson(session.activePlanId())
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "训练计划不存在"));
        List<PropAssetResolver.PropAssetView> availableProps =
                resolveAvailableProps(lessonSessionId, planJson, session.topic());
        List<String> availablePropLemmas = lemmasOf(availableProps);

        int turnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
        String[] turnIdHolder = new String[1];
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                turnIdHolder[0] = tutorTurnRepository.insert(
                        lessonSessionId, session.learnerId(), turnIndex, "warmup", null, null)));
        String turnId = turnIdHolder[0];

        TutorLoop.TutorReply[] replyHolder = new TutorLoop.TutorReply[1];
        timing.addTutorMs(TurnTimingCollector.measureMs(() ->
                replyHolder[0] = tutorLoop.generateOpening(
                        planJson, session.cefrLevel(), session.personaId(),
                        resolveDisplayName(session.learnerId()), session.topic(),
                        availablePropLemmas, ctx)));
        String rawTutor = replyHolder[0].text();
        String declaredProp = replyHolder[0].propLemma();

        SafetyDecision[] outHolder = new SafetyDecision[1];
        long safetyOutMs = TurnTimingCollector.measureMs(() ->
                outHolder[0] = safetyGuard.checkOutput(rawTutor, ctx));
        SafetyDecision out = outHolder[0];
        timing.addSafetyOutMs(safetyOutMs, TurnTimingCollector.safetyModelSkipped(out.eventType()));

        String finalText = applyOutputGate(out, rawTutor);
        if (out.action() != SafetyAction.ALLOW) {
            persistSafety(lessonSessionId, turnId, session.learnerId(), userId, "OUTPUT", out);
        }
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                tutorTurnRepository.updateTutorText(turnId, finalText)));

        timing.addAsrMs(0, true);
        timing.addScoreMs(0, true);
        timing.addStageEvalMs(0, true);
        timing.addSafetyInMs(0, true);

        Flux<CetStreamEvent> deltas = TutorLoop.chunkForSse(finalText).map(CetStreamEvent::delta);
        SpeechToolPort port = speechToolPort.getIfAvailable();
        String voice = resolveTtsVoice(session.personaId());
        return deltas
                .concatWith(Flux.just(propStageEvent(declaredProp, finalText, availableProps)))
                .concatWith(speechExtrasFlux(
                        port, finalText, null, null, null, voice, timing)
                .concatWith(Flux.defer(() -> Flux.just(persistAndEmitTiming(turnId, timing)))));
    }

    /**
     * 外教告别第 1 句（无儿童输入）。要求 {@link WrapUpPhase#PENDING_TUTOR_FAREWELL}。
     *
     * @author liudy
     */
    public Flux<CetStreamEvent> streamWrapUpTutor(String userId, String lessonSessionId) {
        String phase = WrapUpPhase.readPhase(lessonSessionRepository, lessonSessionId, objectMapper)
                .orElseThrow(() -> new KidoraException(ErrorCode.CET_INVALID_STATE, "当前不在告别阶段"));
        if (!WrapUpPhase.PENDING_TUTOR_FAREWELL.equals(phase)) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "告别已开始或已结束");
        }
        return fluxWrapUpTutorStep1(userId, lessonSessionId);
    }

    /**
     * 孩子未回再见时的兜底：直接外教鼓励并结课。
     *
     * @author liudy
     */
    public Flux<CetStreamEvent> streamWrapUpTimeout(String userId, String lessonSessionId) {
        String phase = WrapUpPhase.readPhase(lessonSessionRepository, lessonSessionId, objectMapper)
                .orElseThrow(() -> new KidoraException(ErrorCode.CET_INVALID_STATE, "当前不在告别阶段"));
        if (!WrapUpPhase.AWAIT_CHILD_FAREWELL.equals(phase)) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "尚未到等待孩子告别阶段");
        }
        return fluxWrapUpFinalAndComplete(userId, lessonSessionId, "");
    }

    private Flux<CetStreamEvent> fluxWrapUpTutorStep1(String userId, String lessonSessionId) {
        TurnTimingCollector timing = new TurnTimingCollector("turn", "text");
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        if (session.status() != LessonStatus.PRACTICING) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "当前状态不可告别: " + session.status());
        }
        ModelRouter.CallContext ctx = baseCtx(userId, session.learnerId(), lessonSessionId);
        String planJson = trainingPlanRepository.findActivePlanJson(session.activePlanId())
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "训练计划不存在"));
        List<CetTutorTurnRepository.TurnRow> recent = tutorTurnRepository.listRecent(lessonSessionId, 6);
        int turnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
        String[] turnIdHolder = new String[1];
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                turnIdHolder[0] = tutorTurnRepository.insert(
                        lessonSessionId, session.learnerId(), turnIndex, "wrapup", null, null)));
        String turnId = turnIdHolder[0];
        String displayName = resolveDisplayName(session.learnerId());
        String[] rawHolder = new String[1];
        timing.addTutorMs(TurnTimingCollector.measureMs(() ->
                rawHolder[0] = tutorLoop.generateWrapUpReply(
                        1, planJson, session.cefrLevel(), session.personaId(), displayName,
                        session.topic(), "", recent, ctx)));
        String rawTutor = rawHolder[0];
        SafetyDecision[] outHolder = new SafetyDecision[1];
        long safetyOutMs = TurnTimingCollector.measureMs(() ->
                outHolder[0] = safetyGuard.checkOutput(rawTutor, ctx));
        SafetyDecision out = outHolder[0];
        timing.addSafetyOutMs(safetyOutMs, TurnTimingCollector.safetyModelSkipped(out.eventType()));
        String finalText = applyOutputGate(out, rawTutor);
        if (out.action() != SafetyAction.ALLOW) {
            persistSafety(lessonSessionId, turnId, session.learnerId(), userId, "OUTPUT", out);
        }
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                tutorTurnRepository.updateTutorText(turnId, finalText)));
        timing.addAsrMs(0, true);
        timing.addSafetyInMs(0, true);
        timing.addScoreMs(0, true);
        timing.addStageEvalMs(0, true);
        WrapUpPhase.setAwaitChildFarewell(lessonSessionRepository, lessonSessionId, objectMapper);
        String wrapJson = "{\"phase\":\"" + WrapUpPhase.AWAIT_CHILD_FAREWELL + "\",\"step\":1}";
        Flux<CetStreamEvent> deltas = TutorLoop.chunkForSse(finalText).map(CetStreamEvent::delta);
        SpeechToolPort port = speechToolPort.getIfAvailable();
        String voice = resolveTtsVoice(session.personaId());
        return deltas
                .concatWith(Flux.just(propStageEvent(null, finalText, List.of())))
                .concatWith(speechExtrasFlux(port, finalText, null, null, null, voice, timing))
                .concatWith(Flux.just(CetStreamEvent.wrapUp(wrapJson)))
                .concatWith(Flux.defer(() -> Flux.just(persistAndEmitTiming(turnId, timing))));
    }

    private Flux<CetStreamEvent> fluxWrapUpFinalAndComplete(String userId, String lessonSessionId,
                                                            String childFarewell) {
        TurnTimingCollector timing = new TurnTimingCollector("turn", "text");
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        ModelRouter.CallContext ctx = baseCtx(userId, session.learnerId(), lessonSessionId);
        String planJson = trainingPlanRepository.findActivePlanJson(session.activePlanId())
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "训练计划不存在"));
        List<CetTutorTurnRepository.TurnRow> recent = tutorTurnRepository.listRecent(lessonSessionId, 6);
        int turnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
        String[] turnIdHolder = new String[1];
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                turnIdHolder[0] = tutorTurnRepository.insert(
                        lessonSessionId, session.learnerId(), turnIndex, "wrapup", null, null)));
        String turnId = turnIdHolder[0];
        String displayName = resolveDisplayName(session.learnerId());
        String[] rawHolder = new String[1];
        timing.addTutorMs(TurnTimingCollector.measureMs(() ->
                rawHolder[0] = tutorLoop.generateWrapUpReply(
                        2, planJson, session.cefrLevel(), session.personaId(), displayName,
                        session.topic(), childFarewell, recent, ctx)));
        String rawTutor = rawHolder[0];
        SafetyDecision[] outHolder = new SafetyDecision[1];
        long safetyOutMs = TurnTimingCollector.measureMs(() ->
                outHolder[0] = safetyGuard.checkOutput(rawTutor, ctx));
        SafetyDecision out = outHolder[0];
        timing.addSafetyOutMs(safetyOutMs, TurnTimingCollector.safetyModelSkipped(out.eventType()));
        String finalText = applyOutputGate(out, rawTutor);
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                tutorTurnRepository.updateTutorText(turnId, finalText)));
        timing.addAsrMs(0, true);
        timing.addSafetyInMs(0, true);
        timing.addScoreMs(0, true);
        timing.addStageEvalMs(0, true);
        Optional<String> pendingSummary = WrapUpPhase.readPendingSummary(
                lessonSessionRepository, lessonSessionId, objectMapper);
        CompleteResult done = finishSession(userId, lessonSessionId, pendingSummary);
        String completedJson = "{\"sessionId\":\"" + jsonEscape(done.sessionId())
                + "\",\"status\":\"" + jsonEscape(done.status())
                + "\",\"childSummary\":\"" + jsonEscape(nullToEmpty(done.childSummary())) + "\"}";
        Flux<CetStreamEvent> deltas = TutorLoop.chunkForSse(finalText).map(CetStreamEvent::delta);
        SpeechToolPort port = speechToolPort.getIfAvailable();
        String voice = resolveTtsVoice(session.personaId());
        sessionPropContext.evict(lessonSessionId);
        return deltas
                .concatWith(Flux.just(propStageEvent(null, finalText, List.of())))
                .concatWith(speechExtrasFlux(port, finalText, null, null, null, voice, timing))
                .concatWith(Flux.just(
                        CetStreamEvent.wrapUp("{\"phase\":\"completed\",\"step\":2}"),
                        CetStreamEvent.sessionCompleted(completedJson)))
                .concatWith(Flux.defer(() -> Flux.just(persistAndEmitTiming(turnId, timing))));
    }

    private Flux<CetStreamEvent> streamWrapUpChildTurn(String userId, String lessonSessionId, TurnInput input) {
        boolean voicePath = input != null && StringUtils.hasText(input.audioBase64());
        TurnTimingCollector timing = new TurnTimingCollector("turn", voicePath ? "voice" : "text");
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        ModelRouter.CallContext ctx = baseCtx(userId, session.learnerId(), lessonSessionId);
        ChildTextResolve[] resolvedHolder = new ChildTextResolve[1];
        long asrMs = TurnTimingCollector.measureMs(() ->
                resolvedHolder[0] = resolveChild(input, speechToolPort.getIfAvailable()));
        ChildTextResolve resolved = resolvedHolder[0];
        timing.addAsrMs(asrMs, resolved.asr().isEmpty());
        String childText = resolved.text();
        SafetyDecision[] inHolder = new SafetyDecision[1];
        long safetyInMs = TurnTimingCollector.measureMs(() ->
                inHolder[0] = safetyGuard.checkInput(childText, ctx));
        SafetyDecision in = inHolder[0];
        timing.addSafetyInMs(safetyInMs, TurnTimingCollector.safetyModelSkipped(in.eventType()));
        if (in.isHardBlock()) {
            persistSafety(lessonSessionId, null, session.learnerId(), userId, "INPUT", in);
            LessonStateMachine.assertTransition(LessonStatus.PRACTICING, LessonStatus.SAFETY_BLOCKED);
            lessonSessionRepository.markEnded(lessonSessionId, LessonStatus.SAFETY_BLOCKED);
            WrapUpPhase.clear(lessonSessionRepository, lessonSessionId, objectMapper);
            return withAsrPrefix(resolved, Flux.error(new KidoraException(ErrorCode.CET_SAFETY_BLOCKED,
                    "这条内容不太合适，我们换个话题吧")));
        }
        String effectiveChild = in.action() == SafetyAction.REWRITE && StringUtils.hasText(in.rewriteText())
                ? in.rewriteText() : childText;
        int childTurnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                tutorTurnRepository.insert(
                        lessonSessionId, session.learnerId(), childTurnIndex, "wrapup", null, effectiveChild)));
        return withAsrPrefix(resolved, fluxWrapUpFinalAndComplete(userId, lessonSessionId, effectiveChild));
    }

    /**
     * 开场仅允许尚无轮次时调用。
     *
     * @param existingTurnCount 已有轮次数
     */
    static void assertOpeningAllowed(int existingTurnCount) {
        if (existingTurnCount > 0) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "开场已发送，请直接陪练");
        }
    }

    public Flux<CetStreamEvent> streamTurn(String userId, String lessonSessionId, TurnInput input) {
        boolean voicePath = input != null && StringUtils.hasText(input.audioBase64());
        TurnTimingCollector timing = new TurnTimingCollector("turn", voicePath ? "voice" : "text");
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        if (session.status() != LessonStatus.PRACTICING) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "当前状态不可陪练: " + session.status());
        }
        Optional<String> wrapPhase = WrapUpPhase.readPhase(lessonSessionRepository, lessonSessionId, objectMapper);
        if (wrapPhase.isPresent() && WrapUpPhase.AWAIT_CHILD_FAREWELL.equals(wrapPhase.get())) {
            return streamWrapUpChildTurn(userId, lessonSessionId, input);
        }
        if (wrapPhase.isPresent() && WrapUpPhase.PENDING_TUTOR_FAREWELL.equals(wrapPhase.get())) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE, "告别即将开始，请稍候");
        }
        ModelRouter.CallContext ctx = baseCtx(userId, session.learnerId(), lessonSessionId);

        ChildTextResolve[] resolvedHolder = new ChildTextResolve[1];
        long asrMs = TurnTimingCollector.measureMs(() ->
                resolvedHolder[0] = resolveChild(input, speechToolPort.getIfAvailable()));
        ChildTextResolve resolved = resolvedHolder[0];
        timing.addAsrMs(asrMs, resolved.asr().isEmpty());

        String childText = resolved.text();
        String rawAudio = input == null ? null : input.audioBase64();
        String locale = input == null ? null : input.locale();
        String referenceText = input == null ? null : input.referenceText();

        SafetyDecision[] inHolder = new SafetyDecision[1];
        long safetyInMs = TurnTimingCollector.measureMs(() ->
                inHolder[0] = safetyGuard.checkInput(childText, ctx));
        SafetyDecision in = inHolder[0];
        timing.addSafetyInMs(safetyInMs, TurnTimingCollector.safetyModelSkipped(in.eventType()));

        if (in.isHardBlock()) {
            persistSafety(lessonSessionId, null, session.learnerId(), userId, "INPUT", in);
            LessonStateMachine.assertTransition(LessonStatus.PRACTICING, LessonStatus.SAFETY_BLOCKED);
            lessonSessionRepository.markEnded(lessonSessionId, LessonStatus.SAFETY_BLOCKED);
            return withAsrPrefix(resolved, Flux.error(new KidoraException(ErrorCode.CET_SAFETY_BLOCKED,
                    "这条内容不太合适，我们换个话题吧")));
        }
        String softRedirect = softInputRedirectOrEmpty(in);
        if (softRedirect != null) {
            persistSafety(lessonSessionId, null, session.learnerId(), userId, "INPUT", in);
            int turnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
            String[] softTurnId = new String[1];
            timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                    softTurnId[0] = tutorTurnRepository.insert(lessonSessionId, session.learnerId(), turnIndex,
                            "warmup", softRedirect, childText)));
            timing.addTutorMs(0);
            timing.addSafetyOutMs(0, true);
            timing.addTtsMs(0, true);
            timing.addScoreMs(0, true);
            timing.addStageEvalMs(0, true);
            timing.markTtsReady();
            CetStreamEvent timingEvt = persistAndEmitTiming(softTurnId[0], timing);
            return withAsrPrefix(resolved, Flux.just(CetStreamEvent.delta(softRedirect), timingEvt));
        }
        String effectiveChild = in.action() == SafetyAction.REWRITE && StringUtils.hasText(in.rewriteText())
                ? in.rewriteText() : childText;

        String planJson = trainingPlanRepository.findActivePlanJson(session.activePlanId())
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "训练计划不存在"));
        List<PropAssetResolver.PropAssetView> availableProps =
                resolveAvailableProps(lessonSessionId, planJson, session.topic());
        List<String> availablePropLemmas = lemmasOf(availableProps);
        List<CetTutorTurnRepository.TurnRow> recent = tutorTurnRepository.listRecent(lessonSessionId, 6);
        int turnIndex = tutorTurnRepository.nextTurnIndex(lessonSessionId);
        String stageId = tutorLoop.resolveStageId(planJson, recent);
        String[] turnIdHolder = new String[1];
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                turnIdHolder[0] = tutorTurnRepository.insert(
                        lessonSessionId, session.learnerId(), turnIndex, stageId, null, effectiveChild)));
        String turnId = turnIdHolder[0];

        TutorLoop.TutorReply[] replyHolder = new TutorLoop.TutorReply[1];
        timing.addTutorMs(TurnTimingCollector.measureMs(() ->
                replyHolder[0] = tutorLoop.generateReply(planJson, session.cefrLevel(), session.personaId(),
                        resolveDisplayName(session.learnerId()), effectiveChild, recent,
                        availablePropLemmas, ctx)));
        String rawTutor = replyHolder[0].text();
        String declaredProp = replyHolder[0].propLemma();

        SafetyDecision[] outHolder = new SafetyDecision[1];
        long safetyOutMs = TurnTimingCollector.measureMs(() ->
                outHolder[0] = safetyGuard.checkOutput(rawTutor, ctx));
        SafetyDecision out = outHolder[0];
        timing.addSafetyOutMs(safetyOutMs, TurnTimingCollector.safetyModelSkipped(out.eventType()));

        String finalText = applyOutputGate(out, rawTutor);
        if (out.action() != SafetyAction.ALLOW) {
            persistSafety(lessonSessionId, turnId, session.learnerId(), userId, "OUTPUT", out);
        }
        timing.addPersistMs(TurnTimingCollector.measureMs(() ->
                tutorTurnRepository.updateTutorText(turnId, finalText)));

        Flux<CetStreamEvent> deltas = TutorLoop.chunkForSse(finalText).map(CetStreamEvent::delta);
        SpeechToolPort port = speechToolPort.getIfAvailable();
        String voice = resolveTtsVoice(session.personaId());
        Flux<CetStreamEvent> body = deltas
                .concatWith(Flux.just(propStageEvent(declaredProp, finalText, availableProps)))
                .concatWith(speechExtrasFlux(port, finalText, rawAudio, locale, referenceText, voice, timing))
                .concatWith(Flux.defer(() -> {
                    long[] stageMs = new long[1];
                    StageEvalBundle[] bundle = new StageEvalBundle[1];
                    stageMs[0] = TurnTimingCollector.measureMs(() ->
                            bundle[0] = maybeStageEvaluate(userId, lessonSessionId, turnIndex, planJson, ctx));
                    StageEvalBundle b = bundle[0] == null
                            ? new StageEvalBundle(false, List.of()) : bundle[0];
                    timing.addStageEvalMs(stageMs[0], !b.evaluated());
                    List<CetStreamEvent> tail = new ArrayList<>(b.events());
                    tail.add(persistAndEmitTiming(turnId, timing));
                    return Flux.fromIterable(tail);
                }));

        return withAsrPrefix(resolved, body);
    }

    /**
     * 阶段评测结果（是否真正跑过评测 + SSE）。
     *
     * @param evaluated 是否越过门槛并执行评测
     * @param events    附加事件
     * @author liudy
     */
    record StageEvalBundle(boolean evaluated, List<CetStreamEvent> events) {
    }

    /**
     * 达 targetTurns 时触发阶段评测；仅在此路径调用 Re-Planner（非每轮）。
     *
     * @param userId          用户
     * @param lessonSessionId 会话
     * @param turnIndex       当前轮次
     * @param planJson        当前计划
     * @param ctx             上下文
     * @return 是否评测 + 附加 SSE
     */
    StageEvalBundle maybeStageEvaluate(String userId, String lessonSessionId, int turnIndex,
                                       String planJson, ModelRouter.CallContext ctx) {
        List<CetStreamEvent> events = new ArrayList<>();
        try {
            LessonSession session = requireOwnedSession(userId, lessonSessionId);
            int target = SessionEvaluator.resolveTargetTurns(objectMapper, planJson);
            int lastEval = readLastStageEvalTurn(lessonSessionId);
            if (turnIndex - lastEval < target) {
                return new StageEvalBundle(false, events);
            }
            LessonStateMachine.assertTransition(LessonStatus.PRACTICING, LessonStatus.EVALUATING);
            lessonSessionRepository.updateStatus(lessonSessionId, LessonStatus.EVALUATING);

            List<CetTutorTurnRepository.TurnRow> turns = tutorTurnRepository.listAll(lessonSessionId);
            SessionEvaluator.EvalResult eval = sessionEvaluator.evaluateStage(
                    session.topic(), session.cefrLevel(), planJson, turns, ctx);
            writeLastStageEvalTurn(lessonSessionId, turnIndex);

            if (eval.decision() == SessionEvaluator.Decision.REPLAN) {
                LessonStateMachine.assertTransition(LessonStatus.EVALUATING, LessonStatus.REPLANNING);
                lessonSessionRepository.updateStatus(lessonSessionId, LessonStatus.REPLANNING);
                LessonReplanner.ReplanResult replan = lessonReplanner.replan(planJson, eval, ctx);
                trainingPlanRepository.supersedeActiveForSession(lessonSessionId);
                int version = trainingPlanRepository.findMaxVersion(lessonSessionId) + 1;
                String newPlanId = trainingPlanRepository.insertVersion(
                        lessonSessionId, session.learnerId(), replan.planJson(), version);
                planRevisionRepository.insert(lessonSessionId, newPlanId, version,
                        replan.planJson(), eval.assessmentJson(),
                        eval.focus() == null || eval.focus().isEmpty()
                                ? "stage_replan" : String.join(",", eval.focus()));
                LessonStateMachine.assertTransition(LessonStatus.REPLANNING, LessonStatus.PRACTICING);
                lessonSessionRepository.updateStatusAndPlan(lessonSessionId, LessonStatus.PRACTICING, newPlanId);
                events.add(CetStreamEvent.planUpdated(
                        "{\"planId\":\"" + jsonEscape(newPlanId)
                                + "\",\"version\":" + version
                                + ",\"childSummary\":\"" + jsonEscape(nullToEmpty(replan.childSummary()))
                                + "\",\"decision\":\"replan\"}"));
            } else {
                LessonStateMachine.assertTransition(LessonStatus.EVALUATING, LessonStatus.PRACTICING);
                lessonSessionRepository.updateStatus(lessonSessionId, LessonStatus.PRACTICING);
                if (eval.decision() == SessionEvaluator.Decision.COMPLETE) {
                    WrapUpPhase.setPendingTutorFarewell(
                            lessonSessionRepository, lessonSessionId,
                            nullToEmpty(eval.childSummary()), objectMapper);
                    events.add(CetStreamEvent.wrapUp(
                            "{\"phase\":\"" + WrapUpPhase.PENDING_TUTOR_FAREWELL + "\",\"step\":0}"));
                }
            }
            return new StageEvalBundle(true, events);
        } catch (Exception ignored) {
            try {
                lessonSessionRepository.updateStatus(lessonSessionId, LessonStatus.PRACTICING);
            } catch (Exception ignored2) {
                // best-effort restore
            }
            return new StageEvalBundle(true, events);
        }
    }

    public CompleteResult complete(String userId, String lessonSessionId) {
        WrapUpPhase.clear(lessonSessionRepository, lessonSessionId, objectMapper);
        return finishSession(userId, lessonSessionId, Optional.empty());
    }

    private CompleteResult finishSession(String userId, String lessonSessionId,
                                         Optional<String> preferredChildSummary) {
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
        String childSummary = preferredChildSummary.filter(StringUtils::hasText)
                .orElse(eval.childSummary());
        assessmentRepository.insertSession(lessonSessionId, eval.assessmentJson());
        sessionReportRepository.upsert(lessonSessionId, session.learnerId(), childSummary,
                eval.parentSummary(), eval.assessmentJson());
        learnerMemoryService.onSessionCompleted(
                session.learnerId(), lessonSessionId, session.topic(), eval.assessmentJson());
        LessonStateMachine.assertTransition(LessonStatus.EVALUATING, LessonStatus.COMPLETED);
        lessonSessionRepository.markEnded(lessonSessionId, LessonStatus.COMPLETED);
        WrapUpPhase.clear(lessonSessionRepository, lessonSessionId, objectMapper);
        sessionPropContext.evict(lessonSessionId);
        return new CompleteResult(lessonSessionId, LessonStatus.COMPLETED.name(), childSummary, eval.assessmentJson());
    }

    public ReportResult getReport(String userId, String lessonSessionId) {
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        CetSessionReportRepository.ReportRow report = sessionReportRepository.findBySession(lessonSessionId)
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "报告不存在，请先结课"));
        String parentSummary = report.parentSummary();
        if (!StringUtils.hasText(parentSummary)) {
            try {
                parentSummary = SessionEvaluator.synthesizeParentSummary(objectMapper.readTree(report.reportJson()));
            } catch (Exception ignored) {
                parentSummary = "";
            }
        }
        Instant completedAt = lessonSessionRepository.findEndTime(lessonSessionId).orElse(null);
        return new ReportResult(
                report.childSummary(),
                parentSummary,
                report.reportJson(),
                session.topic(),
                resolveDisplayName(session.learnerId()),
                session.cefrLevel(),
                completedAt == null ? null : completedAt.toString()
        );
    }

    /**
     * 按学习者列课时历史。
     *
     * @param userId    JWT 用户
     * @param learnerId 学习者
     * @return 列表
     */
    public List<CetLessonSessionRepository.SessionListItem> listSessions(String userId, String learnerId) {
        if (!StringUtils.hasText(learnerId)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "learnerId 不能为空");
        }
        learnerProfileRepository.requireOwned(learnerId, userId);
        return lessonSessionRepository.listByLearner(userId, learnerId);
    }

    /**
     * 课时详情（含计划摘要与是否有报告）。
     *
     * @param userId          用户
     * @param lessonSessionId 会话
     * @return 详情
     */
    public SessionDetail getSession(String userId, String lessonSessionId) {
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        String planSummary = "";
        List<String> childGoals = List.of();
        List<String> vocabHints = List.of();
        JsonNode planRoot = null;
        if (StringUtils.hasText(session.activePlanId())) {
            Optional<String> planJson = trainingPlanRepository.findActivePlanJson(session.activePlanId());
            if (planJson.isPresent()) {
                String json = planJson.get();
                planSummary = extractPlanChildSummary(json);
                planRoot = parsePlanRoot(json);
                childGoals = PlanLearningHints.childGoals(planRoot, session.topic());
                vocabHints = PlanLearningHints.vocabHints(planRoot);
            }
        }
        boolean hasReport = sessionReportRepository.findBySession(lessonSessionId).isPresent();
        String wrapUpPhase = WrapUpPhase.readPhase(lessonSessionRepository, lessonSessionId, objectMapper)
                .orElse(null);
        JsonNode hintsRoot = planRoot != null ? planRoot : parsePlanRoot("{}");
        return new SessionDetail(
                session.lessonSessionId(),
                session.learnerId(),
                session.topic(),
                session.personaId(),
                session.cefrLevel(),
                session.status().name(),
                planSummary,
                hasReport,
                childGoals,
                vocabHints,
                propAssetResolver.resolve(PlanLearningHints.propCandidateHints(hintsRoot, session.topic())),
                wrapUpPhase
        );
    }

    /**
     * 全量轮次（历史还原 / 续课 hydration）。
     *
     * @param userId          用户
     * @param lessonSessionId 会话
     * @return 轮次
     */
    public List<CetTutorTurnRepository.TurnRow> listTurns(String userId, String lessonSessionId) {
        requireOwnedSession(userId, lessonSessionId);
        return tutorTurnRepository.listAll(lessonSessionId);
    }

    /**
     * 中止未结课会话。
     *
     * @param userId          用户
     * @param lessonSessionId 会话
     * @return 结果
     */
    public AbortResult abortSession(String userId, String lessonSessionId) {
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        LessonStateMachine.assertTransition(session.status(), LessonStatus.ABORTED);
        lessonSessionRepository.markEnded(lessonSessionId, LessonStatus.ABORTED);
        sessionPropContext.evict(lessonSessionId);
        return new AbortResult(lessonSessionId, LessonStatus.ABORTED.name());
    }

    static final int MAX_DELETE_BATCH = 50;

    /**
     * 硬删除课时（单条或批量），级联清子表；不删 llm_call_log。
     *
     * @param userId          JWT 用户
     * @param lessonSessionIds 会话业务键
     * @return 实际删除条数
     */
    @Transactional
    public int deleteSessions(String userId, List<String> lessonSessionIds) {
        List<String> ids = normalizeDeleteIds(lessonSessionIds);
        for (String id : ids) {
            LessonSession session = lessonSessionRepository.findById(id)
                    .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "记录不存在或无权删除"));
            assertOwnedForDelete(userId, session.userId());
        }
        ids.forEach(sessionPropContext::evict);
        return lessonSessionRepository.hardDeleteCascade(ids);
    }

    /**
     * 去重、去空白并校验批量上限。
     *
     * @param lessonSessionIds 原始 id 列表
     * @return 规范化列表
     */
    static List<String> normalizeDeleteIds(List<String> lessonSessionIds) {
        if (lessonSessionIds == null || lessonSessionIds.isEmpty()) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "sessionIds 不能为空");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : lessonSessionIds) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            unique.add(raw.trim());
        }
        if (unique.isEmpty()) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "sessionIds 不能为空");
        }
        if (unique.size() > MAX_DELETE_BATCH) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "单次最多删除 " + MAX_DELETE_BATCH + " 条");
        }
        return List.copyOf(unique);
    }

    /**
     * 删除场景归属校验：非本人统一 NOT_FOUND，避免枚举。
     *
     * @param userId        JWT 用户
     * @param sessionUserId 会话归属
     */
    static void assertOwnedForDelete(String userId, String sessionUserId) {
        if (userId == null || !userId.equals(sessionUserId)) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "记录不存在或无权删除");
        }
    }

    /**
     * 按轮次重合成外教 TTS（历史回听，不落库）。
     *
     * @param userId          用户
     * @param lessonSessionId 会话
     * @param turnIndex       轮次序号
     * @return TTS 结果
     */
    public TtsReplayResult replayTutorTts(String userId, String lessonSessionId, int turnIndex) {
        LessonSession session = requireOwnedSession(userId, lessonSessionId);
        CetTutorTurnRepository.TurnRow turn = tutorTurnRepository.findByIndex(lessonSessionId, turnIndex)
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "轮次不存在"));
        if (!StringUtils.hasText(turn.tutorText())) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "该轮无外教文本");
        }
        SpeechToolPort port = speechToolPort.getIfAvailable();
        if (port == null) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "语音能力未启用");
        }
        String speakText = speakableForTts(turn.tutorText());
        String voice = resolveTtsVoice(session.personaId());
        SpeechToolPort.TtsResult tts = port.tts(speakText, voice, null)
                .filter(t -> StringUtils.hasText(t.audioBase64()))
                .orElseThrow(() -> new KidoraException(ErrorCode.BAD_REQUEST, "TTS 合成失败"));
        return new TtsReplayResult(tts.audioBase64(), tts.mimeType(), tts.provider());
    }

    private String extractPlanChildSummary(String planJson) {
        try {
            JsonNode node = objectMapper.readTree(planJson);
            String summary = node.path("childSummary").asText("");
            if (!StringUtils.hasText(summary)) {
                summary = node.path("summary").asText("");
            }
            return summary == null ? "" : summary;
        } catch (Exception e) {
            return "";
        }
    }

    private JsonNode parsePlanRoot(String planJson) {
        try {
            return objectMapper.readTree(planJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private int readLastStageEvalTurn(String lessonSessionId) {
        return lessonSessionRepository.findExtraJson(lessonSessionId).map(json -> {
            try {
                return objectMapper.readTree(json).path("lastStageEvalTurn").asInt(0);
            } catch (Exception e) {
                return 0;
            }
        }).orElse(0);
    }

    private void writeLastStageEvalTurn(String lessonSessionId, int turnIndex) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            Optional<String> existing = lessonSessionRepository.findExtraJson(lessonSessionId);
            if (existing.isPresent()) {
                JsonNode prev = objectMapper.readTree(existing.get());
                if (prev.isObject()) {
                    node = (ObjectNode) prev.deepCopy();
                }
            }
            node.put("lastStageEvalTurn", turnIndex);
            lessonSessionRepository.updateExtraJson(lessonSessionId, node.toString());
        } catch (Exception ignored) {
            lessonSessionRepository.updateExtraJson(lessonSessionId,
                    "{\"lastStageEvalTurn\":" + turnIndex + "}");
        }
    }

    private LessonSession requireOwnedSession(String userId, String lessonSessionId) {
        LessonSession session = lessonSessionRepository.findById(lessonSessionId)
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertSessionOwned(userId, session.userId());
        return session;
    }

    /**
     * 读取儿童昵称供 Tutor 称呼；档案缺失时返回空串。
     *
     * @param learnerId 学习者
     * @return display_name 或空
     * @author liudy
     */
    private String resolveDisplayName(String learnerId) {
        return learnerProfileRepository.findByLearnerId(learnerId)
                .map(LearnerProfile::displayName)
                .filter(StringUtils::hasText)
                .orElse("");
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

    /**
     * 儿童输入解析结果（语音路径携带 ASR，避免二次识别）。
     *
     * @param text   入模/展示文本
     * @param asr    语音路径非空
     * @param locale 请求 locale（可空）
     * @author liudy
     */
    public record ChildTextResolve(String text, Optional<SpeechToolPort.AsrResult> asr, String locale) {
    }

    /**
     * 解析儿童文本：优先 ASR，否则 text；语音路径保留 ASR 元数据。
     *
     * @param input 轮次输入
     * @param port  语音端口（可空）
     * @return 解析结果
     */
    static ChildTextResolve resolveChild(TurnInput input, SpeechToolPort port) {
        String locale = input == null ? null : input.locale();
        if (input != null && StringUtils.hasText(input.audioBase64())) {
            if (port == null) {
                throw new KidoraException(ErrorCode.BAD_REQUEST, "语音输入需要启用 MCP 语音能力");
            }
            SpeechToolPort.AsrResult asr = port.asr(input.audioBase64(), locale)
                    .filter(a -> StringUtils.hasText(a.text()))
                    .orElseThrow(() -> new KidoraException(ErrorCode.BAD_REQUEST,
                            "ASR 未能识别语音：请靠近麦克风再说一句，或改用打字"));
            return new ChildTextResolve(asr.text().trim(), Optional.of(asr), locale);
        }
        if (input != null && StringUtils.hasText(input.text())) {
            return new ChildTextResolve(input.text().trim(), Optional.empty(), locale);
        }
        throw new KidoraException(ErrorCode.BAD_REQUEST, "text 或 audioBase64 必填其一");
    }

    /**
     * 解析儿童文本：优先 ASR，否则 text。
     */
    static String resolveChildText(TurnInput input, SpeechToolPort port) {
        return resolveChild(input, port).text();
    }

    /**
     * 语音轮构建 asr.transcript；文本轮 empty。
     *
     * @param resolved 解析结果
     * @return 可选 ASR 事件
     */
    static Optional<CetStreamEvent> asrTranscriptEvent(ChildTextResolve resolved) {
        if (resolved == null || resolved.asr().isEmpty()) {
            return Optional.empty();
        }
        SpeechToolPort.AsrResult a = resolved.asr().get();
        String json = "{\"text\":\"" + jsonEscape(nullToEmpty(a.text()))
                + "\",\"locale\":\"" + jsonEscape(nullToEmpty(resolved.locale()))
                + "\",\"provider\":\"" + jsonEscape(nullToEmpty(a.provider())) + "\"}";
        return Optional.of(CetStreamEvent.asr(json));
    }

    /**
     * 若有 ASR 则前缀到 body 前。
     *
     * @param resolved 解析结果
     * @param body     后续事件流
     * @return 合并流
     */
    static Flux<CetStreamEvent> withAsrPrefix(ChildTextResolve resolved, Flux<CetStreamEvent> body) {
        Optional<CetStreamEvent> head = asrTranscriptEvent(resolved);
        if (head.isEmpty()) {
            return body;
        }
        return Flux.just(head.get()).concatWith(body);
    }

    /**
     * 按人设 + speech_route.primary 解析 TTS voice；无 ACTIVE 映射则 null（走厂商默认）。
     *
     * @param personaId 会话人设
     * @return voice_id 或 null
     */
    String resolveTtsVoice(String personaId) {
        return speechRouteRepository.findPrimaryVendor()
                .flatMap(vendor -> personaVoiceRepository.findActiveVoiceId(personaId, vendor))
                .orElse(null);
    }

    /**
     * 构建 TTS / 发音附加事件。
     * TTS 朗读气泡正文（中文脚手架 + 英文例句），去掉含中文的括号注释；气泡原文仍完整下发。
     *
     * @param voice 人设映射音色；null 表示不传 voice（厂商默认）
     */
    static List<CetStreamEvent> buildSpeechExtras(SpeechToolPort port, String finalText,
                                                  String rawAudio, String locale, String referenceText,
                                                  String voice) {
        return buildSpeechExtrasTimed(port, finalText, rawAudio, locale, referenceText, voice, null);
    }

    /**
     * 带分段计时的 TTS / 发音事件构建（列表形式；测试与兼容用）。
     * 听感关键路径请用 {@link #speechExtrasFlux}，以便 TTS 先于 score 下发。
     *
     * @param timing 可空；非空时写入 ttsMs/scoreMs
     */
    static List<CetStreamEvent> buildSpeechExtrasTimed(SpeechToolPort port, String finalText,
                                                       String rawAudio, String locale, String referenceText,
                                                       String voice, TurnTimingCollector timing) {
        List<CetStreamEvent> extras = new ArrayList<>(buildTtsExtrasTimed(port, finalText, voice, locale, timing));
        extras.addAll(buildScoreExtrasTimed(port, rawAudio, locale, referenceText, timing));
        return extras;
    }

    /**
     * TTS 先发、score 后发的 Flux：在 TTS 事件发出前 {@link TurnTimingCollector#markTtsReady()}，
     * 发音评测不挡听感关键路径。
     *
     * @param timing 可空；非空时写入分段并标记 ttsReady
     */
    static Flux<CetStreamEvent> speechExtrasFlux(SpeechToolPort port, String finalText,
                                                 String rawAudio, String locale, String referenceText,
                                                 String voice, TurnTimingCollector timing) {
        return Flux.defer(() -> {
            List<CetStreamEvent> ttsEvents = buildTtsExtrasTimed(port, finalText, voice, locale, timing);
            if (timing != null) {
                timing.markTtsReady();
            }
            return Flux.fromIterable(ttsEvents);
        }).concatWith(Flux.defer(() ->
                Flux.fromIterable(buildScoreExtrasTimed(port, rawAudio, locale, referenceText, timing))));
    }

    /**
     * 仅 TTS 事件 + ttsMs 计时（不含 markTtsReady）。
     */
    static List<CetStreamEvent> buildTtsExtrasTimed(SpeechToolPort port, String finalText,
                                                    String voice, String locale, TurnTimingCollector timing) {
        List<CetStreamEvent> extras = new ArrayList<>();
        if (port == null) {
            if (timing != null) {
                timing.addTtsMs(0, true);
            }
            return extras;
        }
        String speakText = speakableForTts(finalText);
        boolean ttsSkipped = true;
        if (StringUtils.hasText(speakText)) {
            long ttsStart = System.nanoTime();
            Optional<SpeechToolPort.TtsResult> ttsOpt = port.tts(speakText, voice, locale);
            long ttsMs = Math.round((System.nanoTime() - ttsStart) / 1_000_000.0);
            if (ttsOpt.isPresent()) {
                SpeechToolPort.TtsResult tts = ttsOpt.get();
                extras.add(CetStreamEvent.tts(
                        "{\"audioBase64\":\"" + jsonEscape(nullToEmpty(tts.audioBase64()))
                                + "\",\"mimeType\":\"" + jsonEscape(nullToEmpty(tts.mimeType()))
                                + "\",\"provider\":\"" + jsonEscape(nullToEmpty(tts.provider())) + "\"}"));
                ttsSkipped = false;
            }
            if (timing != null) {
                timing.addTtsMs(ttsMs, ttsSkipped);
            }
        } else if (timing != null) {
            timing.addTtsMs(0, true);
        }
        return extras;
    }

    /**
     * 仅发音评测事件 + scoreMs（不挡 audio.tts）。
     */
    static List<CetStreamEvent> buildScoreExtrasTimed(SpeechToolPort port, String rawAudio,
                                                      String locale, String referenceText,
                                                      TurnTimingCollector timing) {
        List<CetStreamEvent> extras = new ArrayList<>();
        if (port == null) {
            if (timing != null) {
                timing.addScoreMs(0, true);
            }
            return extras;
        }
        String scoreRef = speakableForTts(referenceText);
        if (StringUtils.hasText(rawAudio) && StringUtils.hasText(scoreRef)) {
            long scoreStart = System.nanoTime();
            Optional<SpeechToolPort.PronunciationResult> pOpt = port.score(rawAudio, scoreRef, locale);
            long scoreMs = Math.round((System.nanoTime() - scoreStart) / 1_000_000.0);
            boolean scoreSkipped = true;
            if (pOpt.isPresent()) {
                SpeechToolPort.PronunciationResult p = pOpt.get();
                extras.add(CetStreamEvent.pronunciation(
                        "{\"overall\":" + p.overall()
                                + ",\"accuracy\":" + p.accuracy()
                                + ",\"fluency\":" + p.fluency()
                                + ",\"completeness\":" + p.completeness()
                                + ",\"provider\":\"" + jsonEscape(nullToEmpty(p.provider())) + "\"}"));
                scoreSkipped = false;
            }
            if (timing != null) {
                timing.addScoreMs(scoreMs, scoreSkipped);
            }
        } else if (timing != null) {
            timing.addScoreMs(0, true);
        }
        return extras;
    }

    /**
     * 落库 timing_json（失败仅 warn）并构造 SSE 事件。
     */
    CetStreamEvent persistAndEmitTiming(String turnId, TurnTimingCollector timing) {
        String json = timing.toJson();
        try {
            tutorTurnRepository.updateTimingJson(turnId, json);
        } catch (Exception e) {
            log.warn("persist timing_json failed turnId={}", turnId, e);
        }
        return CetStreamEvent.timing(json);
    }

    /**
     * 客户端上报停麦→开播 e2e 耗时。
     *
     * @param userId          用户
     * @param lessonSessionId 会话
     * @param turnIndex       轮次
     * @param e2eHeardMs      毫秒
     */
    public void recordClientTiming(String userId, String lessonSessionId, int turnIndex, int e2eHeardMs) {
        requireOwnedSession(userId, lessonSessionId);
        if (e2eHeardMs < 0 || e2eHeardMs > MAX_E2E_HEARD_MS) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "e2eHeardMs 超出范围");
        }
        String reportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        int n = tutorTurnRepository.mergeClientTiming(lessonSessionId, turnIndex, e2eHeardMs, reportedAt);
        if (n == 0) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "轮次不存在");
        }
    }

    /**
     * 供 TTS 朗读：去掉含中文的括号注释，保留中文主句与英文例句。
     * 例如 {@code What color is your dog? (你的狗是什么颜色？)} → {@code What color is your dog?}；
     * {@code 说得不错，My dog is white。} 保持不变。
     * 发音评测仍由调用方传入独立 {@code referenceText}（通常为英文目标句），本方法仅对其做同样剥括号。
     *
     * @param text 外教气泡全文或参考句
     * @return 适合朗读的正文；若剥离后为空则回退原文
     * @author liudy
     */
    static String speakableForTts(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String stripped = text
                .replaceAll("[（(][^）)]*[\\u4e00-\\u9fff][^）)]*[）)]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (!StringUtils.hasText(stripped)) {
            stripped = text.trim();
        }
        // 末尾补轻停顿标点，减轻 MP3 尾帧裁切导致的「最后一个音被掐」
        if (!stripped.matches(".*[.!?…。！？]$")) {
            stripped = stripped + ".";
        }
        return stripped;
    }

    /**
     * 输出闸门（可单测）。
     *
     * @param out      决策
     * @param rawTutor 原文
     * @return 安全文本
     */
    static String applyOutputGate(SafetyDecision out, String rawTutor) {
        if (out == null || out.action() == SafetyAction.ALLOW) {
            return rawTutor;
        }
        if (out.action() == SafetyAction.HARD_BLOCK || out.action() == SafetyAction.SOFT_BLOCK) {
            return "Great try! Let's practice a safer sentence together.";
        }
        if (out.action() == SafetyAction.REWRITE && StringUtils.hasText(out.rewriteText())) {
            return out.rewriteText();
        }
        return rawTutor;
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

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 本课可用道具（会话级缓存）。与开课 / 详情共用 {@code propCandidateHints} 口径，
     * 保证提示词闸门看到的词与前端能展示的图完全一致。
     */
    private List<PropAssetResolver.PropAssetView> resolveAvailableProps(String lessonSessionId,
                                                                        String planJson,
                                                                        String topic) {
        try {
            return sessionPropContext.get(lessonSessionId, () -> {
                JsonNode planRoot = parsePlanRoot(planJson);
                return propAssetResolver.resolve(PlanLearningHints.propCandidateHints(planRoot, topic));
            });
        } catch (RuntimeException e) {
            log.warn("CET prop availability lookup failed; disabling prop questions for this turn", e);
            return List.of();
        }
    }

    private static List<String> lemmasOf(List<PropAssetResolver.PropAssetView> assets) {
        return assets.stream().map(PropAssetResolver.PropAssetView::lemma).toList();
    }

    /**
     * 判定舞台并序列化为 SSE payload；任何异常都退化为人像态，不影响主流。
     *
     * @param declaredLemma 外教本轮 {@code [[PROP:词]]} 声明，可空
     */
    private CetStreamEvent propStageEvent(String declaredLemma, String tutorText,
                                          List<PropAssetResolver.PropAssetView> availableAssets) {
        PropStageDirector.PropStageView view;
        try {
            view = propStageDirector.direct(declaredLemma, tutorText, availableAssets);
        } catch (RuntimeException e) {
            log.warn("CET prop stage decision failed; falling back to persona focus", e);
            view = new PropStageDirector.PropStageView(
                    PropStageDirector.LAYOUT_PERSONA_FOCUS, null, List.of());
        }
        return CetStreamEvent.prop(propStageJson(view));
    }

    static String propStageJson(PropStageDirector.PropStageView view) {
        StringBuilder sb = new StringBuilder("{\"layout\":\"").append(view.layout()).append("\",");
        sb.append("\"activeLemma\":");
        if (StringUtils.hasText(view.activeLemma())) {
            sb.append('"').append(jsonEscape(view.activeLemma())).append('"');
        } else {
            sb.append("null");
        }
        sb.append(",\"assets\":[");
        for (int i = 0; i < view.assets().size(); i++) {
            PropAssetResolver.PropAssetView asset = view.assets().get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"lemma\":\"").append(jsonEscape(asset.lemma()))
                    .append("\",\"theme\":\"").append(jsonEscape(nullToEmpty(asset.theme())))
                    .append("\",\"url\":\"").append(jsonEscape(asset.url())).append("\"}");
        }
        return sb.append("]}").toString();
    }

    public record OpenResult(String sessionId, String status, String planSummary, String planId,
                             List<String> childGoals, List<String> vocabHints,
                             List<PropAssetResolver.PropAssetView> propAssets) {
    }

    public record CompleteResult(String sessionId, String status, String childSummary, String assessmentJson) {
    }

    public record ReportResult(String childSummary, String parentSummary, String assessment,
                               String topic, String learnerName, String cefrLevel, String completedAt) {
        public ReportResult(String childSummary, String assessment) {
            this(childSummary, "", assessment, "", "", "", null);
        }
    }

    /**
     * 课时详情。
     *
     * @param sessionId   会话
     * @param learnerId   学习者
     * @param topic       主题
     * @param personaId   人设
     * @param cefrLevel   CEFR
     * @param status      状态
     * @param planSummary 计划儿童摘要
     * @param hasReport   是否有报告
     * @param childGoals  三目标递进
     * @param vocabHints  词提示（道具）
     * @param propAssets  本地库命中的道具（开课预取）
     * @author liudy
     */
    public record SessionDetail(String sessionId, String learnerId, String topic, String personaId,
                                String cefrLevel, String status, String planSummary, boolean hasReport,
                                List<String> childGoals, List<String> vocabHints,
                                List<PropAssetResolver.PropAssetView> propAssets, String wrapUpPhase) {
    }

    /**
     * 中止结果。
     *
     * @param sessionId 会话
     * @param status    状态
     * @author liudy
     */
    public record AbortResult(String sessionId, String status) {
    }

    /**
     * 历史外教 TTS。
     *
     * @param audioBase64 音频
     * @param mimeType    MIME
     * @param provider    厂商
     * @author liudy
     */
    public record TtsReplayResult(String audioBase64, String mimeType, String provider) {
    }
}
