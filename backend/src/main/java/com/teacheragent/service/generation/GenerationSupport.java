package com.teacheragent.service.generation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.mapper.LlmConfigMapper;
import com.teacheragent.service.llm.LlmClient;
import com.teacheragent.service.llm.LlmClientFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 教案 / 题库生成服务的公共工具方法。
 *
 * <p>从 LessonPlanService、QuestionBankService 抽出，统一行为，避免两个 Service 同名方法
 * 出现行为漂移（历史上 distributeTruncate 的最小预算阈值 LessonPlan 是 300 字 / Question 是 200 字，
 * 这种细微差异修复时容易遗漏一处）。
 */
public final class GenerationSupport {

    private GenerationSupport() {}

    public static String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static String stripExt(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 去除 markdown 代码块包裹（```json ... ```） */
    public static String stripCodeBlock(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("```")) {
            int firstLineEnd = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return s.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return s;
    }

    public static String truncateText(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n...(已截断)";
    }

    /**
     * 多文件按比例分配截断，确保每个文件都有内容贡献。
     * <p>升级版：在预算切割点回退到最近的"句末"边界（句号 / 问号 / 感叹号 / 换行），
     * 避免把一句话拦腰截断送进 LLM。
     *
     * @param fileTexts 每项 [fileName, fullText]
     * @param budget    总字符预算
     */
    public static String distributeTruncate(List<String[]> fileTexts, int budget) {
        if (fileTexts == null || fileTexts.isEmpty()) return "";
        long total = fileTexts.stream().mapToLong(p -> p[1] == null ? 0 : p[1].length()).sum();
        StringBuilder sb = new StringBuilder();
        for (String[] p : fileTexts) {
            String name = p[0] == null ? "" : p[0];
            String text = p[1] == null ? "" : p[1];
            sb.append("\n【文件: ").append(name).append("】\n");
            if (total <= budget) {
                sb.append(text);
            } else {
                int fb = Math.max(300, (int) ((long) budget * text.length() / Math.max(1, total)));
                if (text.length() > fb) {
                    sb.append(truncateAtSentenceBoundary(text, fb)).append("\n...(已截断)");
                } else {
                    sb.append(text);
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 在 [maxLen-200, maxLen] 区间内寻找最近的句末边界回退；找不到则按 maxLen 硬切 */
    public static String truncateAtSentenceBoundary(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        int searchStart = Math.max(0, maxLen - 200);
        int bestPos = -1;
        for (int i = maxLen - 1; i >= searchStart; i--) {
            char c = s.charAt(i);
            if (c == '\n' || c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                bestPos = i + 1;
                break;
            }
        }
        if (bestPos < 0) bestPos = maxLen;
        return s.substring(0, bestPos);
    }

    /** 按 provider 选客户端（provider 为空走激活的） */
    public static LlmClient pickClient(LlmClientFactory factory, LlmConfigMapper mapper, String provider) {
        if (provider != null && !provider.isBlank()) {
            LlmConfig cfg = mapper.selectOne(
                    new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getProvider, provider));
            if (cfg == null) throw new BusinessException("未找到 provider: " + provider);
            if (cfg.getApiKey() == null || cfg.getApiKey().isBlank())
                throw new BusinessException("provider [" + provider + "] 未配置 API Key");
            return factory.create(cfg);
        }
        return factory.getActive();
    }

    public static void ensureDir(String path) throws IOException {
        File f = new File(path);
        if (!f.exists() && !f.mkdirs() && !f.exists()) {
            throw new IOException("无法创建目录: " + path);
        }
    }

    public static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
