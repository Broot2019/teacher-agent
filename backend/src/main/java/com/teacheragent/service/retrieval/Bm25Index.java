package com.teacheragent.service.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java BM25 倒排检索（无外部依赖）。
 *
 * <p>用于教案/题库生成场景：把章节素材切成 chunk → 建索引 → 按"章节标题 + 知识点"做 query
 * 取 top-K，避免盲截断。
 *
 * <p>分词策略：中英文混合的轻量切分——英文按非字母数字切，中文按字符级 unigram + bigram。
 * 不引入 Lucene/jieba 等重依赖，召回足够覆盖教学素材语义。
 *
 * <p>BM25 参数：k1=1.5, b=0.75（标准默认值）
 */
public final class Bm25Index {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<String> docs;
    private final List<List<String>> docTokens;
    private final Map<String, Integer> df = new HashMap<>();
    private final double avgDocLen;
    private final int n;

    public Bm25Index(List<String> docs) {
        this.docs = docs == null ? List.of() : docs;
        this.n = this.docs.size();
        this.docTokens = new ArrayList<>(n);
        long totalLen = 0;
        for (String d : this.docs) {
            List<String> tokens = tokenize(d);
            docTokens.add(tokens);
            totalLen += tokens.size();
            // 文档级 df：每个 term 在该文档至少出现一次就 +1
            for (String t : new java.util.HashSet<>(tokens)) {
                df.merge(t, 1, Integer::sum);
            }
        }
        this.avgDocLen = n == 0 ? 1 : (double) totalLen / n;
    }

    /** 取 top-K 文档索引（按 BM25 分数倒序） */
    public List<Integer> topK(String query, int k) {
        if (n == 0 || k <= 0) return List.of();
        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty()) return List.of();

        record Scored(int idx, double score) {}
        List<Scored> scored = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double s = score(qTokens, i);
            if (s > 0) scored.add(new Scored(i, s));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int actualK = Math.min(k, scored.size());
        List<Integer> out = new ArrayList<>(actualK);
        for (int i = 0; i < actualK; i++) out.add(scored.get(i).idx());
        return out;
    }

    public String getDoc(int idx) {
        return idx >= 0 && idx < n ? docs.get(idx) : null;
    }

    public int size() {
        return n;
    }

    private double score(List<String> qTokens, int docIdx) {
        List<String> dTokens = docTokens.get(docIdx);
        int dLen = dTokens.size();
        if (dLen == 0) return 0;
        Map<String, Integer> tf = new HashMap<>();
        for (String t : dTokens) tf.merge(t, 1, Integer::sum);

        double sum = 0;
        for (String q : qTokens) {
            int f = tf.getOrDefault(q, 0);
            if (f == 0) continue;
            int dfQ = df.getOrDefault(q, 0);
            // BM25 平滑 idf：log((N - df + 0.5) / (df + 0.5) + 1)
            double idf = Math.log(((double) n - dfQ + 0.5) / (dfQ + 0.5) + 1.0);
            double normTf = (f * (K1 + 1)) / (f + K1 * (1 - B + B * dLen / avgDocLen));
            sum += idf * normTf;
        }
        return sum;
    }

    /**
     * 轻量分词：
     * - 连续 ASCII 字母/数字段视为一个英文 token（小写）
     * - 中文按字符级 unigram + 相邻 bigram（提升专业术语召回，如"循环""集合"）
     * - 标点、空白丢弃
     */
    static List<String> tokenize(String s) {
        if (s == null) return List.of();
        List<String> tokens = new ArrayList<>();
        int n = s.length();
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjkBuf = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                if (cjkBuf.length() > 0) {
                    flushCjk(cjkBuf, tokens);
                    cjkBuf.setLength(0);
                }
                ascii.append(Character.toLowerCase(c));
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                if (ascii.length() > 0) {
                    tokens.add(ascii.toString());
                    ascii.setLength(0);
                }
                cjkBuf.append(c);
            } else {
                // 标点 / 空白
                if (ascii.length() > 0) {
                    tokens.add(ascii.toString());
                    ascii.setLength(0);
                }
                if (cjkBuf.length() > 0) {
                    flushCjk(cjkBuf, tokens);
                    cjkBuf.setLength(0);
                }
            }
        }
        if (ascii.length() > 0) tokens.add(ascii.toString());
        if (cjkBuf.length() > 0) flushCjk(cjkBuf, tokens);
        return tokens;
    }

    private static void flushCjk(StringBuilder buf, List<String> tokens) {
        // unigram
        for (int i = 0; i < buf.length(); i++) {
            tokens.add(String.valueOf(buf.charAt(i)));
        }
        // bigram
        for (int i = 0; i + 1 < buf.length(); i++) {
            tokens.add(buf.substring(i, i + 2));
        }
    }
}
