package com.teacheragent.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码服务。
 * <p>使用 hutool LineCaptcha 生成 4 位字母数字组合 PNG，base64 返回前端。</p>
 * <p>采用进程内 ConcurrentHashMap 存储 key→(code, expireAt)，5 分钟过期；
 * 单机部署够用；多实例部署需替换为 Redis 实现。</p>
 */
@Slf4j
@Service
public class CaptchaService {

    /** 验证码有效期 */
    private static final Duration EXPIRE = Duration.ofMinutes(5);
    /** 单 key 最多被消费一次（防重放） */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private static final class Entry {
        final String code;
        final Instant expireAt;
        Entry(String code, Instant expireAt) { this.code = code; this.expireAt = expireAt; }
    }

    /**
     * 生成新的验证码。
     * @return Map: key（临时 ID，前端登录时回传）/ image（data URL，可直接 src 使用）/ expireSec
     */
    public Map<String, Object> generate() {
        cleanupExpired();
        // 130x44 的小验证码，4 位字符，2 条干扰线
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 44, 4, 2);
        String code = captcha.getCode().toLowerCase();  // 大小写不敏感比较
        String key = UUID.randomUUID().toString();
        store.put(key, new Entry(code, Instant.now().plus(EXPIRE)));
        String dataUrl = "data:image/png;base64," + captcha.getImageBase64();
        return Map.of(
                "key", key,
                "image", dataUrl,
                "expireSec", EXPIRE.getSeconds()
        );
    }

    /**
     * 校验并消费一次验证码。校验后立刻从存储中移除（无论成败），防止重放。
     * @return true=正确；false=过期/不存在/不匹配
     */
    public boolean verifyOnce(String key, String userInput) {
        if (key == null || userInput == null) return false;
        Entry entry = store.remove(key);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expireAt)) return false;
        return entry.code.equalsIgnoreCase(userInput.trim());
    }

    /** 清理已过期 entry，避免内存泄漏 */
    private void cleanupExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expireAt));
    }
}
