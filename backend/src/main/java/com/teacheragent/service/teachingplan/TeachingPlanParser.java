package com.teacheragent.service.teachingplan;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 教学计划文档解析器
 * 输入：教学计划 PDF/DOCX
 * 输出：每周章节内容映射列表
 */
@Slf4j
@Component
public class TeachingPlanParser {

    /** 匹配 "周次 学时 第X章 ..." 行 */
    private static final Pattern WEEK_LINE = Pattern.compile(
            "^\\s*(\\d{1,2})\\s+(\\d{1,2})\\s+(第[一二三四五六七八九十百零\\d]+章|国庆[^\\s]*|期末[^\\s]*|实训[^\\s]*|总复习|放假|考试).*");

    /** 仅周次开头（更宽松） */
    private static final Pattern WEEK_LINE_LOOSE = Pattern.compile("^\\s*(\\d{1,2})\\s+(\\d{1,2})\\s+(.+)$");

    /**
     * 从 PDF 流中解析
     */
    public List<TeachingWeekItem> parsePdf(InputStream is) {
        try (PDDocument doc = PDDocument.load(is)) {
            String text = new PDFTextStripper().getText(doc);
            return parseText(text);
        } catch (Exception e) {
            log.warn("解析教学计划 PDF 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从纯文本解析
     */
    public List<TeachingWeekItem> parseText(String text) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        List<TeachingWeekItem> result = new ArrayList<>();
        TeachingWeekItem current = null;
        Integer lastWeek = null;

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 严格匹配周次行
            Matcher m = WEEK_LINE.matcher(trimmed);
            if (m.matches()) {
                int week = parseInt(m.group(1));
                String chapter = m.group(3);
                if (current != null) result.add(current);
                current = new TeachingWeekItem();
                current.setWeek(week);
                current.setChapter(chapter);
                lastWeek = week;
                // 章节标识后剩余文字（去除可能重复的章节名 + "理实一体" 课型词）
                int after = m.end(3);
                String rest = trimmed.substring(after).trim();
                rest = rest.replaceAll("理实一体|纯理论|纯实践", "").trim();
                if (rest.startsWith(chapter)) rest = rest.substring(chapter.length()).trim();
                if (!rest.isEmpty()) {
                    current.setChapterTitle(rest.length() > 60 ? rest.substring(0, 60) : rest);
                    current.getTopics().add(rest);
                }
                continue;
            }

            // 宽松匹配：可能是 "第X周 第Y章 描述"
            Matcher m2 = WEEK_LINE_LOOSE.matcher(trimmed);
            if (m2.matches()) {
                int n1 = parseInt(m2.group(1));
                int n2 = parseInt(m2.group(2));
                String rest = m2.group(3);
                if (n1 >= 1 && n1 <= 30 && n2 >= 1 && n2 <= 8 && rest.length() < 200) {
                    if (current != null) result.add(current);
                    current = new TeachingWeekItem();
                    current.setWeek(n1);
                    current.setChapter(rest.contains("章") ? rest.split("\\s+")[0] : "");
                    current.setChapterTitle(rest.length() > 60 ? rest.substring(0, 60) : rest);
                    if (!rest.isEmpty()) current.getTopics().add(rest);
                    lastWeek = n1;
                    continue;
                }
            }

            // 其它行 -> 累加到当前 topics
            if (current != null && trimmed.length() < 200) {
                current.getTopics().add(trimmed);
            }
        }
        if (current != null) result.add(current);

        log.info("教学计划解析得到 {} 周条目", result.size());
        return result;
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    /**
     * 在已解析的列表中查找指定周次
     */
    public TeachingWeekItem findByWeek(List<TeachingWeekItem> list, int week) {
        if (list == null) return null;
        return list.stream().filter(i -> i.getWeek() != null && i.getWeek() == week).findFirst().orElse(null);
    }
}
