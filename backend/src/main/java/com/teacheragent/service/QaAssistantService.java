package com.teacheragent.service;

import com.teacheragent.common.BusinessException;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.mapper.LlmConfigMapper;
import com.teacheragent.service.llm.LlmClient;
import com.teacheragent.service.llm.LlmClientFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaAssistantService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmClientFactory llmClientFactory;
    private final LlmConfigMapper llmConfigMapper;
    private final CourseConfigService courseConfigService;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一位专业的课程教学助手。请基于以下知识库内容回答学生/教师的问题。
            回答要求：
            1. 如果知识库中有相关内容，请基于知识库内容给出准确回答
            2. 如果知识库中没有完全匹配的内容，可以基于你的专业知识补充，但要明确说明"这部分来自AI补充"
            3. 回答要简洁清晰，适合学生理解
            4. 如果涉及代码，请给出完整的可运行示例

            ⚠️ 本回答由AI辅助生成，仅供参考。
            """;

    public Map<String, String> ask(Long userId, String question) {
        Map<String, String> result = new LinkedHashMap<>();

        // 1. 从知识库检索相关内容
        String knowledgeContext = knowledgeBaseService.searchAndConcat(question, 3, 3000);

        // 2. 获取课程信息
        String courseName = "课程";
        try {
            com.teacheragent.entity.CourseConfig cfg = courseConfigService.getActive();
            if (cfg != null && cfg.getCourseName() != null) courseName = cfg.getCourseName();
        } catch (Exception ignored) {}

        // 3. 构建 prompt
        String userPrompt;
        if (!knowledgeContext.isBlank()) {
            userPrompt = String.format("""
                    【课程】%s
                    【知识库检索内容】
                    ```
                    %s
                    ```

                    【问题】%s

                    请基于上述知识库内容回答问题。如果知识库内容不足以完全回答，请补充你的专业见解，并标注哪些是补充的。
                    """, courseName, knowledgeContext, question);
        } else {
            userPrompt = String.format("""
                    【课程】%s
                    【问题】%s

                    当前知识库中没有直接相关的内容。请基于你的专业知识回答，并在回答中注明"本回答由AI补充，未匹配到知识库内容"。
                    """, courseName, question);
        }

        // 4. 调用 LLM
        try {
            LlmClient client = getActiveClient();
            String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("课程", courseName);
            String answer = client.chat(systemPrompt, userPrompt);
            result.put("answer", answer);
            result.put("source", knowledgeContext.isBlank() ? "AI补充" : "知识库+AI");
            result.put("model", client.getProvider() + " / " + client.getModelName());
        } catch (Exception e) {
            log.error("智能答疑调用失败", e);
            result.put("answer", "抱歉，AI服务暂时不可用：" + e.getMessage());
            result.put("source", "错误");
            result.put("model", "无");
        }

        return result;
    }

    private LlmClient getActiveClient() {
        LlmConfig cfg = llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getIsActive, 1).last("LIMIT 1"));
        if (cfg == null) throw new BusinessException("未配置任何大模型，请先到模型配置页面设置");
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank())
            throw new BusinessException("当前激活的模型未配置 API Key");
        return llmClientFactory.create(cfg);
    }
}
