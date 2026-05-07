package com.teacheragent.service.lessonplan;

import com.teacheragent.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * 教案 docx 模板填充器
 * 直接基于 Apache POI 操作模板，按表格行列位置精确填充
 */
@Slf4j
@Component
public class LessonPlanDocFiller {

    private static final String PLACEHOLDER = "（内容填充）";

    /**
     * 根据 LessonPlanData 渲染模板
     * @param templateStream 模板文件流（教案模板.docx）
     * @param data           教案数据
     * @return 渲染后的 docx 字节数组
     */
    public byte[] fill(InputStream templateStream, LessonPlanData data) {
        try (XWPFDocument doc = new XWPFDocument(templateStream)) {

            // 1. 渲染段落区域（"X 学年第 X 学期 任课老师：X 编号："）
            fillHeaderParagraphs(doc, data);

            // 2. 渲染表格
            List<XWPFTable> tables = doc.getTables();
            if (tables.size() < 2) {
                throw new BusinessException("教案模板格式异常：未找到首页表与教学过程表");
            }
            fillFirstPageTable(tables.get(0), data);
            fillProcessTable(tables.get(1), data);

            // 3. 追加 detailed/comprehensive 等级的扩展字段（分层任务、评价量规、评价细则）
            appendLevelExtras(doc, data);

            // 输出
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                doc.write(bos);
                return bos.toByteArray();
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("教案模板填充失败", e);
            throw new BusinessException("教案模板填充失败: " + e.getMessage());
        }
    }

    /**
     * 合并多份 docx 字节数组为一份，每份之间插入分页符。
     * 通过 POI 的 XmlCursor 把后续 docx 的 body 元素（除 sectPr）追加到第一份的 sectPr 之前。
     */
    public byte[] merge(List<byte[]> docs) {
        if (docs == null || docs.isEmpty()) throw new BusinessException("无文档可合并");
        if (docs.size() == 1) return docs.get(0);
        try {
            XWPFDocument target = new XWPFDocument(new ByteArrayInputStream(docs.get(0)));
            for (int i = 1; i < docs.size(); i++) {
                // 在 target 末尾插入分页段落
                XWPFParagraph pageBreak = target.createParagraph();
                pageBreak.createRun().addBreak(BreakType.PAGE);

                try (XWPFDocument src = new XWPFDocument(new ByteArrayInputStream(docs.get(i)))) {
                    appendBody(target, src);
                }
            }
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                target.write(bos);
                target.close();
                return bos.toByteArray();
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("教案文档合并失败", e);
            throw new BusinessException("教案文档合并失败: " + e.getMessage());
        }
    }

    /** 把 src 文档的所有 body 元素（除 sectPr）追加到 target 文档的 sectPr 之前 */
    private void appendBody(XWPFDocument target, XWPFDocument src) {
        CTBody srcBody = src.getDocument().getBody();
        CTBody tgtBody = target.getDocument().getBody();

        // 在 tgtBody 中找到 sectPr，光标定位到它的开始 token（copyXml 会插在该位置之前）
        XmlCursor insertCur = locateInsertPoint(tgtBody);

        // 遍历 srcBody 的所有非 sectPr 元素，依次复制
        XmlCursor srcCur = srcBody.newCursor();
        try {
            if (srcCur.toFirstChild()) {
                do {
                    javax.xml.namespace.QName q = srcCur.getName();
                    if (q != null && "sectPr".equals(q.getLocalPart())) continue;
                    XmlObject xo = srcCur.getObject();
                    if (xo == null) continue;
                    XmlCursor copyCur = xo.newCursor();
                    try {
                        copyCur.copyXml(insertCur);
                    } finally {
                        copyCur.dispose();
                    }
                } while (srcCur.toNextSibling());
            }
        } finally {
            srcCur.dispose();
            insertCur.dispose();
        }
    }

    /** 定位 body 中"应在此前插入新内容"的位置：sectPr 之前；若没有 sectPr，则到 body 末尾 */
    private XmlCursor locateInsertPoint(CTBody body) {
        XmlCursor cur = body.newCursor();
        if (cur.toFirstChild()) {
            do {
                javax.xml.namespace.QName q = cur.getName();
                if (q != null && "sectPr".equals(q.getLocalPart())) {
                    return cur;
                }
            } while (cur.toNextSibling());
        }
        cur.toEndToken();
        return cur;
    }

    /**
     * 追加分层任务 / 评价量规 / 评价细则 段落。
     * 注意：思政目标已经在「教学目标」单元格中由 composeGoals() 拼接展示，
     * 因此此处不再单独追加【课程思政融合点】与【思政目标】，避免重复。
     */
    private void appendLevelExtras(XWPFDocument doc, LessonPlanData data) {
        boolean hasLayered = data.getLayeredTask() != null && !data.getLayeredTask().isBlank();
        boolean hasEval = data.getEvaluation() != null && !data.getEvaluation().isBlank();
        LessonPlanSession s = data.getSessions() == null || data.getSessions().isEmpty() ? null : data.getSessions().get(0);
        boolean hasCriteria = s != null && s.getEvaluationCriteria() != null && !s.getEvaluationCriteria().isBlank();
        if (!hasLayered && !hasEval && !hasCriteria) return;

        if (hasLayered) addExtraParagraph(doc, "【分层任务】", data.getLayeredTask());
        if (hasEval) addExtraParagraph(doc, "【评价量规】", data.getEvaluation());
        if (hasCriteria) addExtraParagraph(doc, "【评价细则】", s.getEvaluationCriteria());
    }

    private void addExtraParagraph(XWPFDocument doc, String label, String content) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle(null);
        XWPFRun titleRun = p.createRun();
        titleRun.setBold(true);
        titleRun.setFontFamily("微软雅黑");
        titleRun.setFontSize(12);
        titleRun.setText(label);

        XWPFParagraph contentP = doc.createParagraph();
        XWPFRun contentRun = contentP.createRun();
        contentRun.setFontFamily("宋体");
        contentRun.setFontSize(11);
        String[] lines = (content == null ? "" : content).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) contentRun.addBreak();
            contentRun.setText(lines[i], i);
        }
    }

    /** 段落部分填充：替换 "（内容填充）" 为对应字段 */
    private void fillHeaderParagraphs(XWPFDocument doc, LessonPlanData data) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            String text = p.getText();
            if (text == null || !text.contains(PLACEHOLDER)) continue;
            // "（内容填充）学年第 （内容填充） 学期 任课老师：（内容填充） 编号："
            // 按出现顺序替换
            String[] replacements = { data.getAcademicYear(), data.getSemester(), data.getTeacher(), data.getPlanNo() };
            String replaced = sequentialReplace(text, PLACEHOLDER, replacements);
            replaceParagraphText(p, replaced);
        }
    }

    /** 顺序替换：遇到 placeholder 第 i 次出现时用 replacements[i] */
    private String sequentialReplace(String src, String placeholder, String[] replacements) {
        StringBuilder result = new StringBuilder();
        int from = 0;
        int idx;
        int rep = 0;
        while ((idx = src.indexOf(placeholder, from)) >= 0) {
            result.append(src, from, idx);
            String r = (rep < replacements.length && replacements[rep] != null) ? replacements[rep] : "";
            result.append(r);
            from = idx + placeholder.length();
            rep++;
        }
        result.append(src, from, src.length());
        return result.toString();
    }

    /** 整段替换文本（清空所有 run，再写一个 run） */
    private void replaceParagraphText(XWPFParagraph p, String text) {
        // 先抓取第一个 run 的样式快照（先读后删，避免 XmlValueDisconnectedException）
        String fontFamily = null;
        int fontSize = -1;
        boolean bold = false;
        boolean italic = false;
        if (!p.getRuns().isEmpty()) {
            try {
                XWPFRun firstRun = p.getRuns().get(0);
                fontFamily = safeGet(firstRun::getFontFamily);
                Integer fs = safeGetInt(firstRun::getFontSize);
                fontSize = fs == null ? -1 : fs;
                Boolean b = safeGetBool(firstRun::isBold);
                bold = b != null && b;
                Boolean it = safeGetBool(firstRun::isItalic);
                italic = it != null && it;
            } catch (Exception ignored) {
            }
        }
        // 删除所有 run
        for (int i = p.getRuns().size() - 1; i >= 0; i--) {
            p.removeRun(i);
        }
        // 创建新 run
        XWPFRun newRun = p.createRun();
        if (fontFamily != null) newRun.setFontFamily(fontFamily);
        if (fontSize > 0) newRun.setFontSize(fontSize);
        newRun.setBold(bold);
        newRun.setItalic(italic);

        // 支持换行
        if (text == null) text = "";
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) newRun.addBreak();
            newRun.setText(lines[i], i);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }

    private String safeGet(ThrowingSupplier<String> s) {
        try { return s.get(); } catch (Exception e) { return null; }
    }

    private Integer safeGetInt(ThrowingSupplier<Integer> s) {
        try { return s.get(); } catch (Exception e) { return null; }
    }

    private Boolean safeGetBool(ThrowingSupplier<Boolean> s) {
        try { return s.get(); } catch (Exception e) { return null; }
    }

    /** 设置表格单元格内容（cell 为 null 跳过） */
    private void setCellText(XWPFTableCell cell, String text) {
        if (cell == null) return;
        if (text == null) text = "";
        XWPFParagraph firstP = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        for (int i = cell.getParagraphs().size() - 1; i > 0; i--) {
            cell.removeParagraph(i);
        }
        replaceParagraphText(firstP, text);
    }

    /** 安全获取指定列；遇到合并位置或越界返回 null */
    private XWPFTableCell safeCell(XWPFTableRow row, int col) {
        if (row == null) return null;
        try {
            XWPFTableCell c = row.getCell(col);
            if (c != null) return c;
            // 兼容合并：通过 tcArray 取
            int n = row.getTableCells().size();
            if (col < n) return row.getTableCells().get(col);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 首页表填充 (9 行 × 4 列；其中 c1/c2/c3 通常为合并单元格，每份模板只装 1 次教案，写入 c1 即可) */
    private void fillFirstPageTable(XWPFTable table, LessonPlanData data) {
        List<LessonPlanSession> sessions = data.getSessions();
        if (sessions.isEmpty()) throw new BusinessException("至少需要 1 次教案数据");

        LessonPlanSession s = sessions.get(0);  // 每份模板只装 1 次教案

        // 行 0: 班别  → 写入 c1
        setCellText(safeCell(table.getRow(0), 1), s.getClassName());
        // 行 1: 时间
        setCellText(safeCell(table.getRow(1), 1), "第 " + s.getWeek() + " 周");
        // 行 2: 课题
        setCellText(safeCell(table.getRow(2), 1), s.getTitle());
        // 行 3: 教学目标
        setCellText(safeCell(table.getRow(3), 1), composeGoals(s));
        // 行 4: 重点难点
        setCellText(safeCell(table.getRow(4), 1), composeKeyDifficult(s));
        // 行 5: 组织形式
        setCellText(safeCell(table.getRow(5), 1), s.getOrganizationForm());
        // 行 6: 教学方法
        setCellText(safeCell(table.getRow(6), 1), s.getTeachingMethod());
        // 行 7 特殊：c0=教学资源(标签), c1=资源内容, c2=课外作业(标签), c3=作业内容
        setCellText(safeCell(table.getRow(7), 1), data.getTeachingResource());
        setCellText(safeCell(table.getRow(7), 3), data.getHomework());
        // 行 8: 学情分析
        setCellText(safeCell(table.getRow(8), 1), s.getStudentSituation());
    }

    private String composeGoals(LessonPlanSession s) {
        StringBuilder sb = new StringBuilder();
        sb.append("知识目标：").append(nullSafe(s.getKnowledgeGoal())).append("\n")
                .append("能力目标：").append(nullSafe(s.getAbilityGoal())).append("\n")
                .append("素养目标：").append(nullSafe(s.getLiteracyGoal()));
        if (s.getIdeologicalGoal() != null && !s.getIdeologicalGoal().isBlank()) {
            sb.append("\n").append("思政目标：").append(s.getIdeologicalGoal());
        }
        return sb.toString();
    }

    private String composeKeyDifficult(LessonPlanSession s) {
        return "教学重点：" + nullSafe(s.getKeyPoints()) + "\n"
                + "教学难点：" + nullSafe(s.getDifficultPoints());
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    /** 教学过程表填充 (8 行 × 4 列；每份模板只承载 1 次教案的过程) */
    private void fillProcessTable(XWPFTable table, LessonPlanData data) {
        LessonPlanSession primary = data.getSessions().get(0);
        List<TimeSlot> tl = primary.getTimeline();

        for (int rowIdx = 1; rowIdx <= 6; rowIdx++) {
            TimeSlot slot = (rowIdx - 1) < tl.size() ? tl.get(rowIdx - 1) : null;
            setCellText(safeCell(table.getRow(rowIdx), 0), slot == null ? "" : slot.getTime());
            setCellText(safeCell(table.getRow(rowIdx), 1), slot == null ? "" : slot.getTeacherAction());
            setCellText(safeCell(table.getRow(rowIdx), 2), slot == null ? "" : slot.getStudentAction());
        }

        // 行 7: 教学反思与教学诊改 - c0 是标签，c1/c2/c3 通常是合并的，写入 c1
        String reflectText = "教学反思：" + nullSafe(primary.getReflection()) + "\n"
                + "教学诊改：" + nullSafe(primary.getImprovement());
        setCellText(safeCell(table.getRow(7), 1), reflectText);
    }
}
