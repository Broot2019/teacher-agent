package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.entity.KnowledgeBase;
import com.teacheragent.entity.KnowledgeChunk;
import com.teacheragent.mapper.KnowledgeBaseMapper;
import com.teacheragent.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper baseMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final FileParseService fileParseService;

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    public List<KnowledgeBase> listMine() {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        return baseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getOwnerId, userId)
                .orderByDesc(KnowledgeBase::getCreateTime));
    }

    public KnowledgeBase getById(Long id) {
        KnowledgeBase kb = baseMapper.selectById(id);
        if (kb == null) throw new BusinessException("知识库不存在");
        Long userId = CurrentUserHolder.currentId();
        if (!CurrentUserHolder.isAdmin() && userId != null && !userId.equals(kb.getOwnerId())) {
            throw new BusinessException(403, "无权操作");
        }
        return kb;
    }

    @Transactional
    public KnowledgeBase upload(String title, MultipartFile file) {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        if (file == null || file.isEmpty()) throw new BusinessException("请上传文件");

        String fileName = file.getOriginalFilename();
        String text;
        try {
            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "kb_upload");
            Files.createDirectories(tmpDir);
            Path tmpFile = tmpDir.resolve(System.currentTimeMillis() + "_" + fileName);
            file.transferTo(tmpFile.toFile());
            text = fileParseService.parseAuto(tmpFile.toFile());
            Files.deleteIfExists(tmpFile);
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        if (text == null || text.isBlank()) throw new BusinessException("文件内容为空或无法解析");

        List<String> chunks = splitIntoChunks(text);
        List<String> keywords = extractKeywords(text);

        KnowledgeBase kb = new KnowledgeBase();
        kb.setOwnerId(userId);
        kb.setTitle(title != null && !title.isBlank() ? title : fileName);
        kb.setFileName(fileName);
        kb.setFileType(fileName != null && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1) : "unknown");
        kb.setFileSize(file.getSize());
        kb.setChunkCount(chunks.size());
        baseMapper.insert(kb);

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setBaseId(kb.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setKeywords(i == 0 ? String.join(",", keywords) : "");
            chunkMapper.insert(chunk);
        }

        return kb;
    }

    @Transactional
    public void deleteById(Long id) {
        KnowledgeBase kb = getById(id);
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getBaseId, id));
        baseMapper.deleteById(id);
    }

    /**
     * 检索与 query 最相关的 topK 个文本块
     */
    public List<String> search(String query, int topK) {
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return Collections.emptyList();

        List<KnowledgeChunk> allChunks = chunkMapper.selectList(new LambdaQueryWrapper<>());
        if (allChunks.isEmpty()) return Collections.emptyList();

        record ScoredChunk(double score, String content) {}

        List<ScoredChunk> scored = allChunks.stream().map(chunk -> {
            String content = chunk.getContent();
            List<String> docTerms = tokenize(content);
            if (docTerms.isEmpty()) return new ScoredChunk(0, content);

            long matchCount = queryTerms.stream()
                    .filter(qt -> docTerms.stream().anyMatch(dt -> dt.contains(qt) || qt.contains(dt)))
                    .count();

            double tf = (double) matchCount / docTerms.size();
            double score = tf * queryTerms.size();
            return new ScoredChunk(score, content);
        }).sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
          .limit(topK)
          .toList();

        return scored.stream()
                .filter(sc -> sc.score > 0)
                .map(ScoredChunk::content)
                .collect(Collectors.toList());
    }

    /**
     * 检索并拼接为一段文本（用于 prompt 注入）
     */
    public String searchAndConcat(String query, int topK, int maxChars) {
        List<String> chunks = search(query, topK);
        if (chunks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String chunk : chunks) {
            if (sb.length() + chunk.length() > maxChars) break;
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(chunk);
        }
        return sb.toString();
    }

    private List<String> splitIntoChunks(String text) {
        String[] paragraphs = text.split("(\\n\\s*\\n|\\r\\n\\s*\\r\\n)");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() > CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString().trim());
                // overlap: keep last portion
                String tail = current.length() > CHUNK_OVERLAP
                        ? current.substring(current.length() - CHUNK_OVERLAP)
                        : current.toString();
                current = new StringBuilder(tail);
            }
            current.append(trimmed).append("\n\n");
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<String> extractKeywords(String text) {
        List<String> tokens = tokenize(text);
        Map<String, Long> freq = tokens.stream()
                .filter(t -> t.length() >= 2)
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(30)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final Pattern WORD = Pattern.compile("[a-zA-Z0-9_.]+");

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        var m1 = CJK.matcher(text);
        while (m1.find()) {
            String s = m1.group();
            for (int i = 0; i + 2 <= s.length(); i++) {
                tokens.add(s.substring(i, Math.min(i + 4, s.length())));
            }
        }
        var m2 = WORD.matcher(text);
        while (m2.find()) tokens.add(m2.group().toLowerCase());
        return tokens;
    }
}
