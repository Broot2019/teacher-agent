package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.entity.PointLog;
import com.teacheragent.entity.User;
import com.teacheragent.mapper.PointLogMapper;
import com.teacheragent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserMapper userMapper;
    private final PointLogMapper pointLogMapper;
    private final SystemConfigService systemConfigService;

    public int lessonPlanCost(Integer weekStart, Integer weekEnd) {
        int base = systemConfigService.getInt("lesson_plan_cost", 10);
        int perWeek = systemConfigService.getInt("lesson_plan_range_cost", 5);
        if (weekStart != null && weekEnd != null && weekEnd >= weekStart) {
            int weeks = weekEnd - weekStart + 1;
            return base + Math.max(0, weeks - 1) * perWeek;
        }
        return base;
    }

    /** per_file 模式：base + (totalSessions-1) × perPlan，复用 lesson_plan_range_cost 作为单份增量 */
    public int lessonPlanCostPerFile(int totalSessions) {
        int base = systemConfigService.getInt("lesson_plan_cost", 10);
        int perPlan = systemConfigService.getInt("lesson_plan_range_cost", 5);
        if (totalSessions <= 0) return base;
        return base + Math.max(0, totalSessions - 1) * perPlan;
    }

    /**
     * 题库积分成本（两段式）：
     * <pre>cost = question_bank_cost（基础） + totalQuestions × question_bank_per_question_cost（每题增量）</pre>
     *
     * <p>例：base=2, perQuestion=1 → 10 道 = 12 分；50 道 = 52 分。
     * 默认值（base=5, perQuestion=1）下，10 道 = 15 分；50 道 = 55 分。
     *
     * @param totalQuestions 本次生成的题目总数（≤ 0 视为 0）
     */
    public int questionBankCost(int totalQuestions) {
        int base = systemConfigService.getInt("question_bank_cost", 5);
        int perQuestion = systemConfigService.getInt("question_bank_per_question_cost", 1);
        int n = Math.max(0, totalQuestions);
        return base + n * perQuestion;
    }

    /** 老签名：保留兼容（无题量上下文时用基础值；生产代码请用 {@link #questionBankCost(int)}） */
    public int questionBankCost() {
        return systemConfigService.getInt("question_bank_cost", 5);
    }

    @Transactional
    public void consume(Long userId, int amount, String reason, String relatedType, String relatedId) {
        if (amount <= 0) return;
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException("用户不存在");
        // admin 是否豁免：默认 false（管理员也按规则扣分），可通过 system_config 显式开启豁免
        if ("admin".equals(u.getRole())
                && systemConfigService.getBool("admin_skip_points_consume", false)) {
            return;
        }
        int balance = u.getPoints() == null ? 0 : u.getPoints();
        if (balance < amount) throw new BusinessException("积分不足：当前 " + balance + "，需要 " + amount + "。请联系管理员充值");
        // 原子更新：WHERE id=? AND points>=amount，防止并发超扣
        int rows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .ge(User::getPoints, amount)
                .setSql("points = points - " + amount));
        if (rows == 0) throw new BusinessException("积分不足或并发冲突，请重试");
        User updated = userMapper.selectById(userId);
        addLog(userId, -amount, updated.getPoints(), reason, relatedType, relatedId);
    }

    @Transactional
    public void grant(Long userId, int amount, String reason) {
        if (amount == 0) return;
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException("用户不存在");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("points = COALESCE(points, 0) + " + amount));
        User updated = userMapper.selectById(userId);
        addLog(userId, amount, updated.getPoints(), reason == null ? "管理员调整积分" : reason, "admin_grant", null);
    }

    @Transactional
    public void refund(Long userId, int amount, String reason, String relatedType, String relatedId) {
        if (userId == null || amount <= 0) return;
        User u = userMapper.selectById(userId);
        if (u == null) return;
        // admin 在 consume 时若被豁免（admin_skip_points_consume=true）则未扣分，refund 也必须同步跳过，
        // 否则会凭空增加积分。consume 与 refund 必须在同一豁免策略下保持对称。
        if ("admin".equals(u.getRole())
                && systemConfigService.getBool("admin_skip_points_consume", false)) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("points = COALESCE(points, 0) + " + amount));
        User updated = userMapper.selectById(userId);
        addLog(userId, amount, updated.getPoints(), reason, relatedType, relatedId);
    }

    private void addLog(Long userId, int change, int balance, String reason, String relatedType, String relatedId) {
        PointLog log = new PointLog();
        log.setUserId(userId);
        log.setChangeAmount(change);
        log.setBalance(balance);
        log.setReason(reason);
        log.setRelatedType(relatedType);
        log.setRelatedId(relatedId);
        pointLogMapper.insert(log);
    }

    public List<PointLog> list(Long userId, int limit) {
        LambdaQueryWrapper<PointLog> q = new LambdaQueryWrapper<>();
        if (userId != null) q.eq(PointLog::getUserId, userId);
        q.orderByDesc(PointLog::getCreateTime).last("LIMIT " + Math.min(limit, 500));
        return pointLogMapper.selectList(q);
    }
}
