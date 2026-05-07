package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.entity.GenerationTask;
import com.teacheragent.entity.User;
import com.teacheragent.mapper.GenerationTaskMapper;
import com.teacheragent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UserMapper userMapper;
    private final GenerationTaskMapper taskMapper;
    private final SystemConfigService systemConfigService;

    /**
     * 检查用户本月配额，剩余 0 抛异常
     */
    public void checkQuota(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        // admin 配额豁免：默认 true（管理员日常运维频繁调用，配额限制无意义），可通过 system_config 关闭
        if ("admin".equals(user.getRole())
                && systemConfigService.getBool("admin_skip_quota_check", true)) {
            return;
        }
        Integer quota = user.getMonthlyQuota();
        if (quota == null || quota <= 0) return;  // 0 或 null 表示不限
        int used = countMonthlyTasks(userId);
        if (used >= quota) {
            throw new BusinessException("本月生成次数已达上限 " + quota + " 次，请联系管理员调整");
        }
    }

    public int countMonthlyTasks(Long userId) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getOwnerId, userId)
                .ge(GenerationTask::getCreateTime, startOfMonth));
        return count == null ? 0 : count.intValue();
    }
}
