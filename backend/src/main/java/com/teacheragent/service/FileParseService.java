package com.teacheragent.service;

import com.teacheragent.common.BusinessException;
import com.teacheragent.service.cache.FileParseCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 文件解析服务 - 支持 ppt/pptx/pdf/docx
 */
@Slf4j
@Service
public class FileParseService {

    @Autowired(required = false)
    private FileParseCacheService cacheService;

    /** 按扩展名自动分发 */
    public String parseAuto(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) throw new BusinessException("文件名为空");
        String lower = name.toLowerCase();
        try (InputStream is = file.getInputStream()) {
            if (lower.endsWith(".pptx")) return parsePptx(is);
            if (lower.endsWith(".ppt")) return parsePpt(is);
            if (lower.endsWith(".pdf")) return parsePdf(is);
            if (lower.endsWith(".docx")) return parseDocx(is);
            throw new BusinessException("不支持的文件格式: " + name);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("解析文件 {} 失败", name, e);
            throw new BusinessException("解析文件失败: " + e.getMessage());
        }
    }

    /** 按文件路径解析（带缓存：相同 sha256 文件直接返回内存/磁盘缓存结果） */
    public String parseAuto(File file) {
        if (file == null || !file.exists()) throw new BusinessException("文件不存在");
        if (cacheService != null) {
            return cacheService.getOrCompute(file, this::doParseAuto);
        }
        return doParseAuto(file);
    }

    /** 实际解析逻辑（不带缓存，供 cacheService 在 miss 时回调） */
    private String doParseAuto(File file) {
        String lower = file.getName().toLowerCase();
        try (InputStream is = new FileInputStream(file)) {
            if (lower.endsWith(".pptx")) return parsePptx(is);
            if (lower.endsWith(".ppt")) return parsePpt(is);
            if (lower.endsWith(".pdf")) return parsePdf(is);
            if (lower.endsWith(".docx")) return parseDocx(is);
            throw new BusinessException("不支持的文件格式: " + file.getName());
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("解析文件 {} 失败", file.getName(), e);
            throw new BusinessException("解析文件失败: " + e.getMessage());
        }
    }

    /** PPT (.ppt) 解析 — 保留标题与缩进层级 */
    public String parsePpt(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (HSLFSlideShow ppt = new HSLFSlideShow(is)) {
            int idx = 1;
            for (HSLFSlide slide : ppt.getSlides()) {
                sb.append("\n[幻灯片 ").append(idx++).append("]\n");
                String slideTitle = slide.getTitle();
                if (slideTitle != null && !slideTitle.isBlank()) {
                    sb.append("# ").append(slideTitle.trim()).append("\n");
                }
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape ts) {
                        // 标题已在 slide.getTitle() 中输出过；此处跳过 TITLE/CENTER_TITLE 类型避免重复
                        try {
                            org.apache.poi.sl.usermodel.Placeholder ph = ts.getPlaceholder();
                            if (ph == org.apache.poi.sl.usermodel.Placeholder.TITLE
                                    || ph == org.apache.poi.sl.usermodel.Placeholder.CENTERED_TITLE) {
                                continue;
                            }
                        } catch (Exception ignored) { }
                        for (var paragraph : ts.getTextParagraphs()) {
                            int level = 0;
                            try { level = Math.max(0, paragraph.getIndentLevel()); } catch (Exception ignored) { }
                            StringBuilder line = new StringBuilder();
                            for (var run : paragraph.getTextRuns()) {
                                String t = run.getRawText();
                                if (t != null) line.append(t);
                            }
                            String text = line.toString().replace('', ' ').trim();
                            if (text.isEmpty()) continue;
                            sb.append(indentMark(level)).append(' ').append(text).append('\n');
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /** PPTX (.pptx) 解析 — 保留标题与缩进层级 */
    public String parsePptx(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(is)) {
            int idx = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("\n[幻灯片 ").append(idx++).append("]\n");
                String slideTitle = slide.getTitle();
                if (slideTitle != null && !slideTitle.isBlank()) {
                    sb.append("# ").append(slideTitle.trim()).append("\n");
                }
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        try {
                            org.apache.poi.sl.usermodel.Placeholder ph = ts.getTextType();
                            if (ph == org.apache.poi.sl.usermodel.Placeholder.TITLE
                                    || ph == org.apache.poi.sl.usermodel.Placeholder.CENTERED_TITLE) {
                                continue;
                            }
                        } catch (Exception ignored) { }
                        for (var paragraph : ts.getTextParagraphs()) {
                            int level = 0;
                            try { level = Math.max(0, paragraph.getIndentLevel()); } catch (Exception ignored) { }
                            String text = paragraph.getText();
                            if (text == null) continue;
                            text = text.replace('', ' ').trim();
                            if (text.isEmpty()) continue;
                            sb.append(indentMark(level)).append(' ').append(text).append('\n');
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 缩进层级标记：0 → "•"，1 → "  --"，2+ → "    ◦" */
    private String indentMark(int level) {
        return switch (Math.min(3, level)) {
            case 0 -> "•";
            case 1 -> "  --";
            case 2 -> "    ◦";
            default -> "      ·";
        };
    }

    /** PDF 解析 */
    public String parsePdf(InputStream is) throws Exception {
        try (PDDocument doc = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    /** DOCX 解析 */
    public String parseDocx(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(is)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) sb.append(t).append("\n");
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder line = new StringBuilder();
                    for (var cell : row.getTableCells()) {
                        line.append(cell.getText()).append(" | ");
                    }
                    sb.append(line).append("\n");
                }
            }
        }
        return sb.toString();
    }
}
