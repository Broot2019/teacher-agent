package com.teacheragent.service.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 素材检索服务（BM25 实现，未来可扩展向量检索）。
 *
 * <p>替代教案/题库生成中的"盲截断"：把章节素材切成 chunk，按"章节标题 + 知识点"做 BM25
 * 检索取 top-K，拼成精筛素材。
 *
 * <p>容错：当 chunk 总长 ≤ budget 时直接返回原文（不降级）；当 BM25 检索结果为空时回退到原文截断。
 */
@Slf4j
@Service
public class SourceRetrievalService {

    /**
     * 教案场景检索：query = chapter + 核心知识点（合并）。
     *
     * @param chapter         章节标题
     * @param knowledgePoints 知识点列表（可空）
     * @param fullText        全文素材
     * @param budget          字符预算
     */
    public String retrieveForLessonPlan(String chapter, List<String> knowledgePoints,
                                        String fullText, int budget) {
        String query = buildQuery(chapter, knowledgePoints);
        return retrieveCore(query, fullText, budget, "lesson_plan");
    }

    /**
     * 题库场景检索：query = chapter + 题型偏好关键词。
     */
    public String retrieveForQuestion(String chapter, String type, String fullText, int budget) {
        String typeHint = switch (type == null ? "" : type) {
            case "single" -> "单选 选择题";
            case "multi" -> "多选 选择题";
            case "judge" -> "判断 概念";
            case "program" -> "编程 代码 实现";
            default -> "";
        };
        String query = buildQuery(chapter, List.of(typeHint));
        return retrieveCore(query, fullText, budget, "question_bank");
    }

    /** 通用检索内核 */
    private String retrieveCore(String query, String fullText, int budget, String scene) {
        if (fullText == null) return "";
        if (fullText.length() <= budget) return fullText;
        if (query == null || query.isBlank()) return truncateAtBoundary(fullText, budget);

        try {
            List<String> chunks = Chunker.chunk(fullText);
            if (chunks.isEmpty()) return truncateAtBoundary(fullText, budget);

            Bm25Index index = new Bm25Index(chunks);
            // 取尽可能多的 chunk 直至累计字符接近 budget
            // 上限：min(20, chunks.size())；具体取多少由累加预算决定
            List<Integer> top = index.topK(query, Math.min(20, chunks.size()));
            if (top.isEmpty()) {
                log.debug("[{}] BM25 检索无命中 query 长度={} 全文长度={} 回退截断",
                        scene, query.length(), fullText.length());
                return truncateAtBoundary(fullText, budget);
            }

            StringBuilder sb = new StringBuilder();
            int used = 0;
            int picked = 0;
            for (int idx : top) {
                String chunk = index.getDoc(idx);
                if (chunk == null) continue;
                int next = used + chunk.length() + 2;
                if (next > budget && picked > 0) break;
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(chunk);
                used = sb.length();
                picked++;
                if (used >= budget * 0.95) break;
            }
            log.debug("[{}] BM25 检索命中 chunks={} 用 chunk={} 字符={}/{}",
                    scene, chunks.size(), picked, used, budget);
            return sb.length() <= budget ? sb.toString() : sb.substring(0, budget);
        } catch (Exception e) {
            log.warn("[{}] 检索异常，回退截断: {}", scene, e.getMessage());
            return truncateAtBoundary(fullText, budget);
        }
    }

    private String buildQuery(String chapter, List<String> kps) {
        List<String> parts = new ArrayList<>();
        if (chapter != null && !chapter.isBlank()) parts.add(chapter);
        if (kps != null) {
            for (String k : kps) {
                if (k != null && !k.isBlank()) parts.add(k);
            }
        }
        return String.join(" ", parts);
    }

    /** 在句末边界回退截断 */
    private String truncateAtBoundary(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        int searchStart = Math.max(0, maxLen - 200);
        for (int i = maxLen - 1; i >= searchStart; i--) {
            char c = s.charAt(i);
            if (c == '\n' || c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                return s.substring(0, i + 1) + "\n...(已截断)";
            }
        }
        return s.substring(0, maxLen) + "\n...(已截断)";
    }
}
