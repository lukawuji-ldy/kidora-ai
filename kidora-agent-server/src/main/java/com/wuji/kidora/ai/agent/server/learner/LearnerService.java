package com.wuji.kidora.ai.agent.server.learner;

import com.wuji.kidora.ai.agent.server.auth.EnglishLevelMapper;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import com.wuji.kidora.ai.memory.repo.LearnerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家长名下儿童档案：列表 / 新建 / 编辑 / 软删除。
 *
 * @author liudy
 */
@Service
public class LearnerService {

    private static final int NICKNAME_MAX = 32;
    private static final String DEFAULT_PERSONA = "emma";

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerService(LearnerProfileRepository learnerProfileRepository) {
        this.learnerProfileRepository = learnerProfileRepository;
    }

    /**
     * ACTIVE 儿童列表（含 englishLevel）。
     *
     * @author liudy
     */
    public List<Map<String, Object>> listActive(String userId) {
        return learnerProfileRepository.listActiveByUserId(userId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 新建儿童档案。
     *
     * @author liudy
     */
    public Map<String, Object> create(String userId, String displayName, String englishLevel) {
        String name = normalizeNickname(displayName);
        String cefr = EnglishLevelMapper.toCefr(englishLevel);
        String learnerId = IdGenerator.nextBizId("lrn_");
        learnerProfileRepository.insert(learnerId, userId, name, null, cefr, DEFAULT_PERSONA);
        return toView(new LearnerProfile(learnerId, userId, name, null, cefr, DEFAULT_PERSONA, "ACTIVE"));
    }

    /**
     * 更新昵称与/或英语水平。
     *
     * @author liudy
     */
    public Map<String, Object> update(String userId, String learnerId, String displayName, String englishLevel) {
        LearnerProfile current = learnerProfileRepository.requireOwned(learnerId, userId);
        String name = StringUtils.hasText(displayName) ? normalizeNickname(displayName) : current.displayName();
        String cefr = StringUtils.hasText(englishLevel)
                ? EnglishLevelMapper.toCefr(englishLevel)
                : current.cefrLevel();
        if (!StringUtils.hasText(cefr)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "英语水平不能为空");
        }
        learnerProfileRepository.updateProfile(learnerId, name, cefr);
        return toView(new LearnerProfile(
                learnerId, userId, name, current.ageBand(), cefr, current.preferredPersona(), "ACTIVE"));
    }

    /**
     * 软删除；禁止删除最后一名 ACTIVE 儿童。
     *
     * @author liudy
     */
    public void softDelete(String userId, String learnerId) {
        learnerProfileRepository.requireOwned(learnerId, userId);
        if (learnerProfileRepository.countActiveByUserId(userId) <= 1) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "至少保留一名儿童档案");
        }
        learnerProfileRepository.softDelete(learnerId);
    }

    private Map<String, Object> toView(LearnerProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("learnerId", p.learnerId());
        m.put("displayName", p.displayName());
        m.put("ageBand", p.ageBand());
        m.put("cefrLevel", p.cefrLevel());
        m.put("englishLevel", safeEnglishLevel(p.cefrLevel()));
        m.put("preferredPersona", p.preferredPersona());
        return m;
    }

    private static String safeEnglishLevel(String cefr) {
        if (!StringUtils.hasText(cefr)) {
            return null;
        }
        try {
            return EnglishLevelMapper.fromCefr(cefr);
        } catch (KidoraException ex) {
            return null;
        }
    }

    private static String normalizeNickname(String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (!StringUtils.hasText(name) || name.length() > NICKNAME_MAX) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "儿童昵称长度须为 1～32");
        }
        return name;
    }
}
