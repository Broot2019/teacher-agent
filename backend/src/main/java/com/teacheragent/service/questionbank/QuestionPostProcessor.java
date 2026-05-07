package com.teacheragent.service.questionbank;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 题库本地后处理：答案分布均衡 + 题干相似度去重。
 *
 * <p>1. 答案分布均衡（{@link #balanceSingleChoiceDistribution}）：LLM 生成的单选题答案
 * 倾向于聚集在 B/C，对学生猜题不友好。本方法检测 A/B/C/D 频次偏差，对偏多的题"洗牌"——
 * 打乱 options 顺序后重新映射 answer 字母，无需 LLM 调用。
 *
 * <p>2. 相似度去重（{@link #deduplicateBySimilarity}）：基于题干 stem 的 token Jaccard 相似度，
 * 阈值 &gt; 0.85 视为重复，保留前一道。避免 LLM 生成"换个说法的同一题"。
 */
@Slf4j
public final class QuestionPostProcessor {

    /** Jaccard 相似度阈值，超过即视为重复 */
    private static final double DEDUP_THRESHOLD = 0.85;
    /** 答案分布偏差阈值：max - min &gt; threshold * total / 4 时触发洗牌 */
    private static final double BALANCE_TOLERANCE = 0.5;

    private QuestionPostProcessor() {}

    /**
     * 单选题答案分布均衡：检测 A/B/C/D 频次偏差超阈值时，对偏多答案的题做选项洗牌。
     * <p>洗牌策略：随机选一个偏少的字母作为新 answer 位置，与原 answer 位置的选项交换。
     */
    public static void balanceSingleChoiceDistribution(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return;
        List<Question> singles = new ArrayList<>();
        for (Question q : questions) {
            if ("single".equals(q.getType()) && q.getOptions() != null && q.getOptions().size() == 4) {
                singles.add(q);
            }
        }
        if (singles.size() < 4) return;

        Map<Character, List<Question>> byAns = new HashMap<>();
        for (char c = 'A'; c <= 'D'; c++) byAns.put(c, new ArrayList<>());
        for (Question q : singles) {
            String a = q.getAnswer() == null ? "" : q.getAnswer().trim().toUpperCase();
            if (a.length() == 1 && a.charAt(0) >= 'A' && a.charAt(0) <= 'D') {
                byAns.get(a.charAt(0)).add(q);
            }
        }

        int total = singles.size();
        int avg = total / 4;
        int maxCount = byAns.values().stream().mapToInt(List::size).max().orElse(0);
        int minCount = byAns.values().stream().mapToInt(List::size).min().orElse(0);
        if (maxCount - minCount <= Math.max(1, (int) (BALANCE_TOLERANCE * avg))) {
            return; // 已经够均衡
        }

        Random rng = new Random(42); // 固定种子保证可复现
        // 把超出 avg 的题逐个洗到偏少的字母
        for (char from = 'A'; from <= 'D'; from++) {
            List<Question> bucket = byAns.get(from);
            while (bucket.size() > avg + 1) {
                // 找当前最少的字母
                char minLetter = 'A';
                int min = Integer.MAX_VALUE;
                for (char c = 'A'; c <= 'D'; c++) {
                    int sz = byAns.get(c).size();
                    if (sz < min) { min = sz; minLetter = c; }
                }
                if (minLetter == from || min >= avg) break;
                Question q = bucket.remove(bucket.size() - 1);
                swapOptions(q, from, minLetter);
                byAns.get(minLetter).add(q);
            }
        }
        log.debug("答案分布均衡：单选题 {} 道完成洗牌", singles.size());
    }

    /** 交换 options[from] 与 options[to]，同步把 answer 改为 to */
    private static void swapOptions(Question q, char from, char to) {
        int fromIdx = from - 'A';
        int toIdx = to - 'A';
        List<String> opts = q.getOptions();
        if (fromIdx < 0 || fromIdx >= opts.size() || toIdx < 0 || toIdx >= opts.size()) return;
        String tmp = opts.get(fromIdx);
        opts.set(fromIdx, opts.get(toIdx));
        opts.set(toIdx, tmp);
        q.setAnswer(String.valueOf(to));
    }

    /**
     * 基于题干 Jaccard 相似度去重：相邻两题相似度 &gt; 0.85 视为重复，保留前一道。
     */
    public static List<Question> deduplicateBySimilarity(List<Question> questions) {
        if (questions == null || questions.size() < 2) return questions;
        List<Question> kept = new ArrayList<>();
        List<Set<String>> keptTokens = new ArrayList<>();
        for (Question q : questions) {
            Set<String> tokens = tokenize(q.getStem());
            boolean isDup = false;
            for (Set<String> prev : keptTokens) {
                double j = jaccard(tokens, prev);
                if (j > DEDUP_THRESHOLD) {
                    isDup = true;
                    break;
                }
            }
            if (!isDup) {
                kept.add(q);
                keptTokens.add(tokens);
            }
        }
        int dropped = questions.size() - kept.size();
        if (dropped > 0) {
            log.info("题干相似度去重：淘汰 {} 道（保留 {} 道）", dropped, kept.size());
        }
        return kept;
    }

    /** 同 Bm25Index.tokenize 思想的轻量分词，不引入依赖 */
    private static Set<String> tokenize(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                if (cjk.length() > 0) { flushCjk(cjk, out); cjk.setLength(0); }
                ascii.append(Character.toLowerCase(c));
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                if (ascii.length() > 0) { out.add(ascii.toString()); ascii.setLength(0); }
                cjk.append(c);
            } else {
                if (ascii.length() > 0) { out.add(ascii.toString()); ascii.setLength(0); }
                if (cjk.length() > 0) { flushCjk(cjk, out); cjk.setLength(0); }
            }
        }
        if (ascii.length() > 0) out.add(ascii.toString());
        if (cjk.length() > 0) flushCjk(cjk, out);
        return out;
    }

    private static void flushCjk(StringBuilder buf, Set<String> tokens) {
        for (int i = 0; i < buf.length(); i++) tokens.add(String.valueOf(buf.charAt(i)));
        for (int i = 0; i + 1 < buf.length(); i++) tokens.add(buf.substring(i, i + 2));
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }
}
