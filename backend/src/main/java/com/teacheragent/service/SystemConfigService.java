package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.entity.SystemConfig;
import com.teacheragent.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper configMapper;

    public String get(String key, String def) {
        SystemConfig c = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key).last("LIMIT 1"));
        return c == null || c.getConfigValue() == null ? def : c.getConfigValue();
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def))); } catch (Exception e) { return def; }
    }

    public boolean getBool(String key, boolean def) {
        String v = get(key, String.valueOf(def));
        if (v == null) return def;
        v = v.trim().toLowerCase();
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    /**
     * 兼容读取布尔配置：优先读 newKey，未配置则回退 oldKey，再回退默认值。
     *
     * <p>用于配置项语义升级（Phase 2）：例如把
     * {@code lesson_plan_self_critique_enabled} 升级为
     * {@code lesson_plan_batch_review_enabled}，旧 key 仍能被识别。
     */
    public boolean getBoolWithFallback(String newKey, String oldKey, boolean def) {
        SystemConfig n = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, newKey).last("LIMIT 1"));
        if (n != null && n.getConfigValue() != null && !n.getConfigValue().isBlank()) {
            return parseBool(n.getConfigValue(), def);
        }
        SystemConfig o = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, oldKey).last("LIMIT 1"));
        if (o != null && o.getConfigValue() != null && !o.getConfigValue().isBlank()) {
            return parseBool(o.getConfigValue(), def);
        }
        return def;
    }

    private boolean parseBool(String v, boolean def) {
        if (v == null) return def;
        v = v.trim().toLowerCase();
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    public Map<String, String> list() {
        List<SystemConfig> list = configMapper.selectList(new LambdaQueryWrapper<SystemConfig>().orderByAsc(SystemConfig::getId));
        Map<String, String> map = new LinkedHashMap<>();
        for (SystemConfig c : list) map.put(c.getConfigKey(), c.getConfigValue());
        return map;
    }

    @Transactional
    public void save(Map<String, String> map) {
        for (var e : map.entrySet()) {
            SystemConfig c = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, e.getKey()).last("LIMIT 1"));
            if (c == null) {
                c = new SystemConfig();
                c.setConfigKey(e.getKey());
                c.setConfigValue(e.getValue());
                configMapper.insert(c);
            } else {
                c.setConfigValue(e.getValue());
                configMapper.updateById(c);
            }
        }
    }
}
