package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.dto.LlmConfigSaveRequest;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.mapper.LlmConfigMapper;
import com.teacheragent.service.llm.LlmClient;
import com.teacheragent.service.llm.LlmClientFactory;
import com.teacheragent.service.llm.TestResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private final LlmConfigMapper llmConfigMapper;
    private final LlmClientFactory llmClientFactory;

    public List<LlmConfig> list() {
        return llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>().orderByAsc(LlmConfig::getId)
        );
    }

    public LlmConfig getByProvider(String provider) {
        return llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getProvider, provider)
        );
    }

    @Transactional
    public LlmConfig save(LlmConfigSaveRequest req) {
        LlmConfig existing = getByProvider(req.getProvider());
        if (existing == null) {
            existing = new LlmConfig();
            existing.setProvider(req.getProvider());
            existing.setIsActive(0);
            existing.setLastTestStatus("untested");
        }
        if (req.getApiKey() != null) {
            String trimmed = req.getApiKey().trim();
            // 防止非 admin list 接口返回的 mask 字符串被回写覆盖真实 key
            if (trimmed.contains("****")) {
                throw new BusinessException("API Key 含掩码字符（****），请输入完整明文 Key 后再保存");
            }
            // 防止历史 enc: 加密残留被错误地再次入库
            if (trimmed.startsWith("enc:")) {
                throw new BusinessException("不允许保存以 enc: 开头的历史加密残留 Key，请输入新的明文 Key");
            }
            existing.setApiKey(trimmed);
        }
        if (req.getModelName() != null && !req.getModelName().isBlank()) {
            existing.setModelName(req.getModelName().trim());
        }
        if (req.getBaseUrl() != null) existing.setBaseUrl(req.getBaseUrl().trim());
        if (req.getMaxConcurrent() != null) existing.setMaxConcurrent(req.getMaxConcurrent());
        if (req.getRpmLimit() != null) existing.setRpmLimit(req.getRpmLimit());
        if (req.getDefaultMaxTokens() != null) existing.setDefaultMaxTokens(req.getDefaultMaxTokens());
        if (existing.getId() == null) {
            llmConfigMapper.insert(existing);
        } else {
            llmConfigMapper.updateById(existing);
        }
        return existing;
    }

    public TestResult test(String provider) {
        LlmConfig config = getByProvider(provider);
        if (config == null) {
            return TestResult.fail("未找到配置: " + provider);
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            return TestResult.fail("尚未填写 API Key");
        }
        if (config.getModelName() == null || config.getModelName().isBlank()) {
            return TestResult.fail("尚未填写模型名");
        }
        LlmClient client = llmClientFactory.create(config);
        TestResult result = client.test();
        // 更新测试状态
        config.setLastTestStatus(result.isSuccess() ? "success" : "failed");
        config.setLastTestTime(LocalDateTime.now());
        config.setLastTestMessage(result.getMessage());
        llmConfigMapper.updateById(config);
        return result;
    }

    @Transactional
    public void activate(String provider) {
        LlmConfig config = getByProvider(provider);
        if (config == null) throw new BusinessException("未找到配置: " + provider);
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BusinessException("该模型尚未配置 API Key，无法激活");
        }
        // 先全部置为未激活
        llmConfigMapper.update(null,
                new LambdaUpdateWrapper<LlmConfig>().set(LlmConfig::getIsActive, 0));
        // 再激活当前
        config.setIsActive(1);
        llmConfigMapper.updateById(config);
    }

    public LlmConfig getActive() {
        return llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getIsActive, 1).last("LIMIT 1")
        );
    }

    public void delete(Long id) {
        llmConfigMapper.deleteById(id);
    }
}
