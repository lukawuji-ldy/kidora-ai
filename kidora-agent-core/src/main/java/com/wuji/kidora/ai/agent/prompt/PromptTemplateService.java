package com.wuji.kidora.ai.agent.prompt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 prompt_template 加载并渲染提示词。
 *
 * @author liudy
 */
@Service
public class PromptTemplateService {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, String> contentCache = new ConcurrentHashMap<>();

    public PromptTemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String loadActiveContent(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return contentCache.computeIfAbsent(code, this::loadActiveContentFromDb);
    }

    public void invalidate(String code) {
        if (StringUtils.hasText(code)) {
            contentCache.remove(code);
        }
    }

    public void invalidateAll() {
        contentCache.clear();
    }

    public String render(String template, Map<String, String> vars) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        Map<String, String> safeVars = vars == null ? Map.of() : vars;
        Matcher matcher = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = safeVars.getOrDefault(key, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String loadAndRender(String code, Map<String, String> vars, String fallback) {
        String content = loadActiveContent(code);
        if (!StringUtils.hasText(content)) {
            content = fallback;
        }
        return render(content, vars);
    }

    private String loadActiveContentFromDb(String code) {
        if (jdbcTemplate == null) {
            return "";
        }
        List<String> list = jdbcTemplate.query("""
                SELECT content FROM prompt_template
                WHERE code = ? AND status = 'ACTIVE'
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("content"), code);
        return list.isEmpty() ? "" : list.get(0);
    }
}
