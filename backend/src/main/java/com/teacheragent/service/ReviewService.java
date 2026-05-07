package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.entity.LessonPlanHistory;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.entity.QuestionBankHistory;
import com.teacheragent.entity.QuestionItem;
import com.teacheragent.mapper.LessonPlanHistoryMapper;
import com.teacheragent.mapper.LlmConfigMapper;
import com.teacheragent.mapper.QuestionBankHistoryMapper;
import com.teacheragent.mapper.QuestionItemMapper;
import com.teacheragent.service.llm.LlmClient;
import com.teacheragent.service.llm.LlmClientFactory;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final LlmClientFactory llmClientFactory;
    private final LlmConfigMapper llmConfigMapper;
    private final LessonPlanHistoryMapper lessonHistoryMapper;
    private final QuestionBankHistoryMapper questionHistoryMapper;
    private final QuestionItemMapper questionItemMapper;

    private static final String LESSON_REVIEW_SYSTEM = """
            你是一位教学督导，擅长对教案进行专业评审。
            请从以下维度给出评审意见：
            1. 教学目标是否明确、可衡量
            2. 重难点是否准确
            3. 教学过程是否完整、逻辑连贯
            4. 是否体现课程思政
            5. 学情分析是否到位
            6. 改进建议

            请给出总分（0-100）和详细评审意见。
            """;

    private static final String QUESTION_REVIEW_SYSTEM = """
            你是一位出题评审专家，擅长对考试题目进行质量评审。
            请从以下维度给出评审意见：
            1. 知识点覆盖是否全面
            2. 难度梯度是否合理
            3. 题干表述是否清晰
            4. 答案是否正确
            5. 解析是否有教学价值
            6. 改进建议

            请给出总分（0-100）和详细评审意见。
            """;

    public Map<String, Object> reviewLessonPlan(Long userId, Map<String, Object> body) {
        Long historyId = body.get("historyId") != null ? Long.valueOf(body.get("historyId").toString()) : null;
        if (historyId == null) throw new BusinessException("请指定教案历史记录ID");

        LessonPlanHistory h = lessonHistoryMapper.selectById(historyId);
        if (h == null) throw new BusinessException("记录不存在");
        if (!userId.equals(h.getOwnerId()) && !CurrentUserHolder.isAdmin()) {
            throw new BusinessException(403, "无权操作");
        }

        String content = body.get("content") != null ? body.get("content").toString() : "";
        if (content.isBlank()) {
            content = "教案文件: " + h.getOutputFileName() + ", 章节: " + h.getChapter();
        }

        LlmClient client = getActiveClient();
        String answer = client.chat(LESSON_REVIEW_SYSTEM,
                "请评审以下教案内容：\n\n" + content);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("review", answer);
        result.put("model", client.getProvider() + " / " + client.getModelName());
        result.put("historyId", historyId);
        return result;
    }

    public Map<String, Object> reviewQuestionBank(Long userId, Map<String, Object> body) {
        Long historyId = body.get("historyId") != null ? Long.valueOf(body.get("historyId").toString()) : null;
        if (historyId == null) throw new BusinessException("请指定题库历史记录ID");

        QuestionBankHistory h = questionHistoryMapper.selectById(historyId);
        if (h == null) throw new BusinessException("记录不存在");
        if (!userId.equals(h.getOwnerId()) && !CurrentUserHolder.isAdmin()) {
            throw new BusinessException(403, "无权操作");
        }

        // 获取该题库的所有题目摘要
        List<QuestionItem> items = questionItemMapper.selectList(
                new LambdaQueryWrapper<QuestionItem>().eq(QuestionItem::getBankId, historyId));

        StringBuilder sb = new StringBuilder();
        sb.append("题库名称: ").append(h.getChapter()).append("\n");
        sb.append("题目数量: ").append(items.size()).append("\n\n");

        for (QuestionItem qi : items) {
            sb.append("[").append(typeLabel(qi.getType())).append("] ");
            sb.append(qi.getStem()).append("\n");
            sb.append("答案: ").append(qi.getAnswer()).append("\n");
            sb.append("难度: ").append(qi.getDifficulty()).append("\n\n");
        }

        LlmClient client = getActiveClient();
        String answer = client.chat(QUESTION_REVIEW_SYSTEM,
                "请评审以下题库内容：\n\n" + sb);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("review", answer);
        result.put("model", client.getProvider() + " / " + client.getModelName());
        result.put("historyId", historyId);
        result.put("questionCount", items.size());
        return result;
    }

    private String typeLabel(String type) {
        return Map.of("single", "单选", "multi", "多选", "judge", "判断", "program", "编程").getOrDefault(type != null ? type : "", type);
    }

    private LlmClient getActiveClient() {
        LlmConfig cfg = llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getIsActive, 1).last("LIMIT 1"));
        if (cfg == null) throw new BusinessException("未配置大模型");
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) throw new BusinessException("模型未配置 API Key");
        return llmClientFactory.create(cfg);
    }
}
