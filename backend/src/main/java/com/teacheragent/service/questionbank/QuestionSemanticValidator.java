package com.teacheragent.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.teacheragent.service.generation.GenerationSupport;
import com.teacheragent.service.llm.ChatOptions;
import com.teacheragent.service.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目语义校验：批量调用 LLM 判断题目的 answer 是否真的对（基于 stem + options 语义）。
 *
 * <p>为什么需要：现行 {@code QuestionBankService.filterValidQuestions} 仅校验答案的格式
 * （单选 [A-D]、多选 [A-D],[A-D] 等），不能识别"answer 字母指向的选项内容是否真的对应题干所问"。
 * LLM 偶尔会返回选项内容正确但 answer 字母错位的题（约 5-10% 概率）。
 *
 * <p>策略：把题目精简成 (stem, options, answer) 三元组，每 10 道一批发给低温度评审 LLM，
 * 让它返回 yes/no 矩阵。校验失败的题进入重生候选。
 *
 * <p>用配置项 {@code question_bank_semantic_validate_enabled} 控制开关；默认关闭以避免
 * 增加 LLM 调用次数；用户开启后约多 N/10 次调用换取约 5-10% 答案正确率提升。
 */
@Slf4j
public final class QuestionSemanticValidator {

    private static final int BATCH_SIZE = 10;

    private QuestionSemanticValidator() {}

    /**
     * 批量校验：返回每道题"答案是否对"的布尔值（与输入 List 顺序对齐）。
     * 校验失败（LLM 调用异常 / 解析失败）的位置默认 true（不误杀好题）。
     */
    public static List<Boolean> batchValidate(LlmClient client, List<Question> questions) {
        List<Boolean> result = new ArrayList<>();
        if (questions == null || questions.isEmpty()) return result;
        int n = questions.size();
        for (int i = 0; i < n; i++) result.add(true);

        for (int batchStart = 0; batchStart < n; batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, n);
            List<Question> batch = questions.subList(batchStart, batchEnd);
            // 仅校验有标准答案的题（编程题答案是代码不便逐字校验，跳过）
            boolean anyCheckable = batch.stream().anyMatch(q -> !"program".equals(q.getType()));
            if (!anyCheckable) continue;

            try {
                List<Boolean> batchResult = doValidate(client, batch);
                for (int i = 0; i < batchResult.size() && (batchStart + i) < n; i++) {
                    result.set(batchStart + i, batchResult.get(i));
                }
            } catch (Exception e) {
                log.warn("题目语义校验第 {}-{} 批失败，沿用默认 pass: {}", batchStart, batchEnd - 1, e.getMessage());
            }
        }
        return result;
    }

    private static List<Boolean> doValidate(LlmClient client, List<Question> batch) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < batch.size(); i++) {
            Question q = batch.get(i);
            if ("program".equals(q.getType())) continue;
            JSONObject o = new JSONObject();
            o.put("index", i);
            o.put("type", typeName(q.getType()));
            o.put("stem", q.getStem());
            o.put("options", q.getOptions());
            o.put("answer", q.getAnswer());
            arr.add(o);
        }

        String prompt = String.format("""
                你是出题校验专家。任务：阅读以下题目，判断每道题给定的 answer 是否真的对应题干所问的正确选项。

                判断标准：
                - 单选题：answer 字母（如 "A"）对应的 options 文本是否在题干语境下唯一正确？
                - 多选题：answer（如 "A,C"）对应的 options 文本组合是否全部正确且无遗漏？
                - 判断题：answer（"正确"/"错误"）与题干陈述是否一致？

                输出 JSON 数组，每元素 {"index": 输入的 index, "ok": true/false, "reason": 简短原因}。
                严格按数组返回，不要 markdown，长度等于输入。

                【题目数据】
                %s
                """, arr.toJSONString());

        String json = client.chatJson(QuestionBankPrompts.SYSTEM_PROMPT_REVIEW, prompt, ChatOptions.reviewer());
        if (json == null || json.isBlank()) return defaultPass(batch.size());

        String cleaned = GenerationSupport.stripCodeBlock(json.trim());
        JSONArray respArr;
        try {
            respArr = JSON.parseArray(cleaned);
        } catch (Exception e) {
            return defaultPass(batch.size());
        }
        if (respArr == null) return defaultPass(batch.size());

        Map<Integer, Boolean> map = new HashMap<>();
        for (int i = 0; i < respArr.size(); i++) {
            JSONObject o = respArr.getJSONObject(i);
            if (o == null) continue;
            int idx = o.containsKey("index") ? o.getIntValue("index") : i;
            Boolean ok = o.getBoolean("ok");
            if (idx >= 0 && idx < batch.size()) {
                map.put(idx, ok != null ? ok : true);
            }
        }
        List<Boolean> out = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            out.add(map.getOrDefault(i, true));
        }
        return out;
    }

    private static List<Boolean> defaultPass(int n) {
        List<Boolean> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(true);
        return r;
    }

    private static String typeName(String type) {
        return switch (type == null ? "" : type) {
            case "single" -> "单选题";
            case "multi" -> "多选题";
            case "judge" -> "判断题";
            case "program" -> "编程题";
            default -> type;
        };
    }
}
