package com.wuji.kidora.ai.agent.prompt;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link #META_SYSTEM_PROMPT} 注入为<strong>唯一</strong> system，并剥离 messages 中的 SystemMessage，
 * 避免 PostgresSaver AppendStrategy 累积多条 SystemMessage 导致模型混乱。
 *
 * @author liudy
 */
public class KidoraSystemPromptInterceptor extends ModelInterceptor {

    /** RunnableConfig metadata / ModelRequest.context 键 */
    public static final String META_SYSTEM_PROMPT = "kidora.systemPrompt";

    @Override
    public String getName() {
        return "kidoraSystemPrompt";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        String fromMeta = readSystemPrompt(request.getContext());
        List<Message> cleaned = stripSystemMessages(request.getMessages());
        SystemMessage system = StringUtils.hasText(fromMeta)
                ? new SystemMessage(fromMeta)
                : request.getSystemMessage();
        ModelRequest next = ModelRequest.builder(request)
                .messages(cleaned)
                .systemMessage(system)
                .build();
        return handler.call(next);
    }

    private static String readSystemPrompt(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object v = context.get(META_SYSTEM_PROMPT);
        return v instanceof String s && StringUtils.hasText(s) ? s : null;
    }

    private static List<Message> stripSystemMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Message> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            if (!(m instanceof SystemMessage)) {
                out.add(m);
            }
        }
        return out;
    }
}
