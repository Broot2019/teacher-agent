package com.teacheragent.service.retrieval;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块器：把长文档切成 BM25/向量检索友好的 chunk。
 *
 * <p>切块策略（保序）：
 * <ol>
 *     <li>按段落（双换行 / 单换行）粗切</li>
 *     <li>过长段落（> targetSize）按句号 / 问号 / 感叹号 / 分号细切</li>
 *     <li>每个 chunk 与下一个 chunk 之间保留 {@code overlap} 字符的重叠（保留语义连续性）</li>
 *     <li>过短的相邻 chunk 合并（&lt; targetSize/3 视为太碎）</li>
 * </ol>
 */
public final class Chunker {

    /** 默认切块大小：约 600 字符（中文约 250-300 字） */
    public static final int DEFAULT_TARGET_SIZE = 600;
    /** 默认重叠：约 80 字符 */
    public static final int DEFAULT_OVERLAP = 80;

    private Chunker() {}

    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_TARGET_SIZE, DEFAULT_OVERLAP);
    }

    public static List<String> chunk(String text, int targetSize, int overlap) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        if (targetSize <= 0) targetSize = DEFAULT_TARGET_SIZE;
        if (overlap < 0) overlap = 0;
        if (overlap >= targetSize) overlap = Math.max(0, targetSize / 4);

        // 1. 段落粗切
        String[] paragraphs = text.split("\\n{2,}");
        List<String> rough = new ArrayList<>();
        for (String para : paragraphs) {
            String p = para.strip();
            if (p.isEmpty()) continue;
            if (p.length() <= targetSize) {
                rough.add(p);
            } else {
                rough.addAll(splitLongParagraph(p, targetSize));
            }
        }

        // 2. 合并过短相邻段落
        int minMerge = Math.max(80, targetSize / 3);
        StringBuilder buf = new StringBuilder();
        for (String r : rough) {
            if (buf.length() == 0) {
                buf.append(r);
            } else if (buf.length() + r.length() + 1 <= targetSize) {
                buf.append('\n').append(r);
            } else {
                if (buf.length() < minMerge && !result.isEmpty()) {
                    // 兜底：太短就再 append 到上一条
                    int last = result.size() - 1;
                    result.set(last, result.get(last) + "\n" + buf);
                } else {
                    result.add(buf.toString());
                }
                buf.setLength(0);
                buf.append(r);
            }
        }
        if (buf.length() > 0) {
            if (buf.length() < minMerge && !result.isEmpty()) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + "\n" + buf);
            } else {
                result.add(buf.toString());
            }
        }

        // 3. 重叠：在每个 chunk 末尾追加下一个 chunk 的前 overlap 字符
        if (overlap > 0 && result.size() > 1) {
            List<String> withOverlap = new ArrayList<>(result.size());
            for (int i = 0; i < result.size(); i++) {
                String cur = result.get(i);
                if (i < result.size() - 1) {
                    String next = result.get(i + 1);
                    String tail = next.length() > overlap ? next.substring(0, overlap) : next;
                    cur = cur + "\n[…续接：" + tail + "]";
                }
                withOverlap.add(cur);
            }
            return withOverlap;
        }
        return result;
    }

    /** 长段落按句末标点细切，每段不超过 targetSize */
    private static List<String> splitLongParagraph(String para, int targetSize) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int n = para.length();
        for (int i = 0; i < n; i++) {
            char c = para.charAt(i);
            cur.append(c);
            boolean isSentenceEnd = c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '.' || c == '!' || c == '?' || c == ';' || c == '\n';
            if (cur.length() >= targetSize && isSentenceEnd) {
                out.add(cur.toString().strip());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString().strip());
        return out;
    }
}
