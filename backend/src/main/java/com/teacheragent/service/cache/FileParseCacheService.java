package com.teacheragent.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.teacheragent.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.Function;

/**
 * 文件解析缓存：内存（Caffeine）+ 磁盘 sha256 backup。
 *
 * <p>设计目标：教案/题库重跑、教案重生、批量任务等场景下，相同的 PPT/PDF/DOCX 文件
 * 不必反复解析（POI 解析大 PPT 5-15 秒、PDF 也常 1-3 秒）。命中缓存后秒级返回。
 *
 * <p>缓存粒度：以文件 sha256 为 key（同一份文件即便存放路径不同，也共享缓存）。
 * 内存层 LRU 200 条 + 2 小时未访问过期；磁盘层 {@code {cacheDir}/parse/{sha256}.txt}
 * 永不过期（除非用户主动清空 ./data/cache）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseCacheService {

    private final AppProperties props;

    private Cache<String, String> memCache;

    @PostConstruct
    public void init() {
        this.memCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(Duration.ofHours(2))
                .build();
        // 确保磁盘缓存目录存在
        try {
            Path dir = Paths.get(props.getCacheDir(), "parse");
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (Exception e) {
            log.warn("创建文件解析缓存目录失败: {}", e.getMessage());
        }
    }

    /**
     * 获取文件解析结果：内存命中 → 磁盘命中 → 调用 parser 重新解析并写回两层缓存。
     */
    public String getOrCompute(File file, Function<File, String> parser) {
        if (file == null || !file.exists()) {
            // 走原解析逻辑（让 parser 自身抛业务异常）
            return parser.apply(file);
        }
        String sha = sha256File(file);
        if (sha == null) {
            // 计算 sha 失败，降级为不缓存
            return parser.apply(file);
        }
        // 内存层
        String cached = memCache.getIfPresent(sha);
        if (cached != null) {
            log.debug("文件解析缓存命中（内存） sha={} file={}", shortSha(sha), file.getName());
            return cached;
        }
        // 磁盘层
        Path diskPath = Paths.get(props.getCacheDir(), "parse", sha + ".txt");
        if (Files.exists(diskPath)) {
            try {
                String text = Files.readString(diskPath, StandardCharsets.UTF_8);
                memCache.put(sha, text);
                log.debug("文件解析缓存命中（磁盘） sha={} file={}", shortSha(sha), file.getName());
                return text;
            } catch (Exception e) {
                log.warn("读取磁盘缓存失败 sha={}: {}", shortSha(sha), e.getMessage());
            }
        }
        // miss：调用 parser
        String text = parser.apply(file);
        if (text != null) {
            memCache.put(sha, text);
            try {
                Files.writeString(diskPath, text, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("写入磁盘缓存失败 sha={}: {}", shortSha(sha), e.getMessage());
            }
        }
        return text;
    }

    private String sha256File(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file.toPath());
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算文件 sha256 失败 path={}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private String shortSha(String sha) {
        return sha == null ? "null" : sha.substring(0, Math.min(8, sha.length()));
    }
}
