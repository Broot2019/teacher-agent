package com.teacheragent.service.questionbank;

import com.teacheragent.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库 xlsx 模板填充器
 *
 * 模板格式:
 *   行 1: 填写须知（保留）
 *   行 2: 表头 [知识点, 题干, 题型, 难易度, 答案, 解析, 选项A, 选项B, 选项C, 选项D, ...]
 *   行 3-8: 示例数据（生成时清空）
 *   行 3+: 实际题目
 */
@Slf4j
@Component
public class QuestionBankXlsxFiller {

    /**
     * 填充题库
     *
     * @param templateStream 模板流
     * @param questions      题目列表
     * @return 渲染后字节数组
     */
    public byte[] fill(InputStream templateStream, List<Question> questions) {
        try (Workbook wb = new XSSFWorkbook(templateStream)) {
            Sheet sheet = wb.getSheetAt(0);

            // 1. 解析表头列位置
            Row headerRow = sheet.getRow(1);  // 行号 0-based，模板的行 2 = index 1
            if (headerRow == null) {
                throw new BusinessException("题库模板格式异常: 第 2 行表头缺失");
            }
            Map<String, Integer> colMap = new HashMap<>();
            int maxOptionIndex = 0;
            for (Cell c : headerRow) {
                String v = getCellString(c).trim();
                if (v.isEmpty()) continue;
                colMap.put(v, c.getColumnIndex());
                if (v.startsWith("选项") && v.length() == 3) {
                    char letter = v.charAt(2);
                    if (letter >= 'A' && letter <= 'Z') {
                        maxOptionIndex = Math.max(maxOptionIndex, letter - 'A' + 1);
                    }
                }
            }
            if (!colMap.containsKey("题干") || !colMap.containsKey("题型")) {
                throw new BusinessException("题库模板表头缺少必要字段: 题干/题型");
            }

            // 2. 删除示例行（行 3-8 = index 2-7），保留表头
            int startDataRow = 2;
            int lastRowNum = sheet.getLastRowNum();
            for (int i = lastRowNum; i >= startDataRow; i--) {
                Row r = sheet.getRow(i);
                if (r != null) sheet.removeRow(r);
            }

            // 3. 写入题目
            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            int rowIdx = startDataRow;
            for (Question q : questions) {
                Row row = sheet.createRow(rowIdx++);
                writeCell(row, colMap, "知识点", q.getKnowledge(), wrapStyle);
                writeCell(row, colMap, "题干", q.getStem(), wrapStyle);
                writeCell(row, colMap, "题型", mapTypeToTemplate(q.getType()), wrapStyle);
                writeCell(row, colMap, "难易度", q.getDifficulty(), wrapStyle);
                writeCell(row, colMap, "答案", q.getAnswer(), wrapStyle);
                writeCell(row, colMap, "解析", q.getExplanation(), wrapStyle);

                List<String> opts = q.getOptions();
                if (opts != null) {
                    for (int i = 0; i < opts.size() && i < maxOptionIndex; i++) {
                        char letter = (char) ('A' + i);
                        writeCell(row, colMap, "选项" + letter, opts.get(i), wrapStyle);
                    }
                }
            }

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                wb.write(bos);
                return bos.toByteArray();
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("填充题库 xlsx 失败", e);
            throw new BusinessException("填充题库 xlsx 失败: " + e.getMessage());
        }
    }

    /** 内部题型 → 模板支持的题型枚举 */
    private String mapTypeToTemplate(String type) {
        if (type == null) return "单选题";
        return switch (type) {
            case "single"  -> "单选题";
            case "multi"   -> "多选题";
            case "judge"   -> "判断题";
            case "program" -> "问答题";  // 编程题映射为问答题（兼容现有题库系统）
            case "essay"   -> "问答题";
            case "fill"    -> "客观填空题";
            default        -> type;
        };
    }

    private void writeCell(Row row, Map<String, Integer> colMap, String header, String value, CellStyle style) {
        Integer col = colMap.get(header);
        if (col == null) return;
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private String getCellString(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> "";
        };
    }
}
