package com.teacheragent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.teacheragent.entity.GenerationTask;
import com.teacheragent.entity.LessonPlanHistory;
import com.teacheragent.entity.QuestionBankHistory;
import com.teacheragent.entity.QuestionItem;
import com.teacheragent.entity.User;
import com.teacheragent.mapper.GenerationTaskMapper;
import com.teacheragent.mapper.LessonPlanHistoryMapper;
import com.teacheragent.mapper.QuestionBankHistoryMapper;
import com.teacheragent.mapper.QuestionItemMapper;
import com.teacheragent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserMapper userMapper;
    private final GenerationTaskMapper taskMapper;
    private final LessonPlanHistoryMapper lessonMapper;
    private final QuestionBankHistoryMapper questionMapper;
    private final QuestionItemMapper questionItemMapper;

    public Map<String, Object> stats() {
        Map<String, Object> r = new LinkedHashMap<>();
        // 用户统计
        long totalUsers = userMapper.selectCount(new LambdaQueryWrapper<>());
        long teachers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "teacher"));
        long admins = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "admin"));
        long enabled = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, "enabled"));
        Map<String, Object> userStats = new LinkedHashMap<>();
        userStats.put("total", totalUsers);
        userStats.put("teachers", teachers);
        userStats.put("admins", admins);
        userStats.put("enabled", enabled);
        userStats.put("disabled", totalUsers - enabled);
        r.put("users", userStats);

        // 任务统计
        long totalTasks = taskMapper.selectCount(new LambdaQueryWrapper<>());
        long success = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getStatus, "success"));
        long failed = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getStatus, "failed"));
        long running = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>().in(GenerationTask::getStatus, "running", "pending"));
        long lessonTasks = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getType, "lesson_plan"));
        long questionTasks = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getType, "question_bank"));

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todayTasks = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>().ge(GenerationTask::getCreateTime, startOfDay));
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long monthTasks = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>().ge(GenerationTask::getCreateTime, startOfMonth));

        Map<String, Object> taskStats = new LinkedHashMap<>();
        taskStats.put("total", totalTasks);
        taskStats.put("success", success);
        taskStats.put("failed", failed);
        taskStats.put("running", running);
        taskStats.put("lesson", lessonTasks);
        taskStats.put("question", questionTasks);
        taskStats.put("today", todayTasks);
        taskStats.put("month", monthTasks);
        r.put("tasks", taskStats);

        // 历史统计
        long lessonHistory = lessonMapper.selectCount(new LambdaQueryWrapper<>());
        long questionHistory = questionMapper.selectCount(new LambdaQueryWrapper<>());
        Map<String, Object> historyStats = new LinkedHashMap<>();
        historyStats.put("lesson", lessonHistory);
        historyStats.put("question", questionHistory);
        r.put("history", historyStats);

        // 最近 7 天每日任务数
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            long c = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                    .ge(GenerationTask::getCreateTime, d.atStartOfDay())
                    .lt(GenerationTask::getCreateTime, d.plusDays(1).atStartOfDay()));
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("count", c);
            daily.add(day);
        }
        r.put("daily", daily);

        // 最近 7 天成功任务数
        List<Map<String, Object>> successDaily = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            long c = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                    .eq(GenerationTask::getStatus, "success")
                    .ge(GenerationTask::getCreateTime, d.atStartOfDay())
                    .lt(GenerationTask::getCreateTime, d.plusDays(1).atStartOfDay()));
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("count", c);
            successDaily.add(day);
        }
        r.put("successDaily", successDaily);

        // LLM 厂商使用次数
        QueryWrapper<LessonPlanHistory> qw1 = new QueryWrapper<>();
        qw1.select("llm_provider as provider, count(*) as cnt").groupBy("llm_provider");
        List<Map<String, Object>> llmStats = lessonMapper.selectMaps(qw1);
        QueryWrapper<QuestionBankHistory> qw2 = new QueryWrapper<>();
        qw2.select("llm_provider as provider, count(*) as cnt").groupBy("llm_provider");
        List<Map<String, Object>> qbStats = questionMapper.selectMaps(qw2);
        Map<String, Long> mergedLlm = new LinkedHashMap<>();
        for (var m : llmStats) {
            String p = (String) m.get("provider");
            if (p == null) continue;
            Number c = (Number) m.get("cnt");
            if (c == null) continue;
            mergedLlm.merge(p, c.longValue(), Long::sum);
        }
        for (var m : qbStats) {
            String p = (String) m.get("provider");
            if (p == null) continue;
            Number c = (Number) m.get("cnt");
            if (c == null) continue;
            mergedLlm.merge(p, c.longValue(), Long::sum);
        }
        r.put("llmUsage", mergedLlm);

        // TOP 10 教师
        QueryWrapper<GenerationTask> qw3 = new QueryWrapper<>();
        qw3.select("owner_id as ownerId, count(*) as cnt")
                .groupBy("owner_id").orderByDesc("cnt").last("LIMIT 10");
        List<Map<String, Object>> topUsers = taskMapper.selectMaps(qw3);
        List<Long> ownerIds = topUsers.stream()
                .map(m -> m.get("ownerId"))
                .filter(java.util.Objects::nonNull)
                .map(oid -> ((Number) oid).longValue())
                .toList();
        if (!ownerIds.isEmpty()) {
            Map<Long, User> userMap = new LinkedHashMap<>();
            userMapper.selectBatchIds(ownerIds).forEach(u -> userMap.put(u.getId(), u));
            for (var m : topUsers) {
                Object oid = m.get("ownerId");
                if (oid != null) {
                    User u = userMap.get(((Number) oid).longValue());
                    if (u != null) {
                        m.put("username", u.getUsername());
                        m.put("realName", u.getRealName());
                    }
                }
            }
        }
        r.put("topUsers", topUsers);

        // 题型分布统计：优先用 question_item 表 GROUP BY 聚合（实际生成的题目，更准确）；
        // 兼容回退：若 question_item 为空再扫描 question_bank_history.questionTypes JSON
        Map<String, Long> knowledgeStats = new LinkedHashMap<>();
        knowledgeStats.put("单选题", 0L);
        knowledgeStats.put("多选题", 0L);
        knowledgeStats.put("判断题", 0L);
        knowledgeStats.put("编程题", 0L);
        QueryWrapper<QuestionItem> qiTypeQw = new QueryWrapper<>();
        qiTypeQw.select("type, COUNT(*) AS cnt").groupBy("type");
        List<Map<String, Object>> qiTypeRows = questionItemMapper.selectMaps(qiTypeQw);
        boolean hasItems = false;
        for (var m : qiTypeRows) {
            String type = (String) m.get("type");
            Number cnt = (Number) m.get("cnt");
            if (type == null || cnt == null) continue;
            hasItems = true;
            String label = mapItemTypeToLabel(type);
            knowledgeStats.merge(label, cnt.longValue(), Long::sum);
        }
        // 回退：question_item 表为空时扫描 question_bank_history（仅取 question_types 字段，避免加载全部历史）
        if (!hasItems) {
            QueryWrapper<QuestionBankHistory> qbTypesQw = new QueryWrapper<>();
            qbTypesQw.select("question_types");
            List<Map<String, Object>> qbTypeRows = questionMapper.selectMaps(qbTypesQw);
            for (var m : qbTypeRows) {
                String qt = (String) m.get("question_types");
                if (qt == null || qt.isBlank()) continue;
                try {
                    Map<String, Integer> tc = JSON.parseObject(qt, new TypeReference<Map<String, Integer>>() {});
                    if (tc != null) mergeTypeCount(knowledgeStats, tc);
                } catch (Exception ignored) {}
            }
        }
        r.put("knowledgeStats", knowledgeStats);

        // 难度分布统计：直接用 question_item 表 GROUP BY 聚合，避免全表加载
        Map<String, Long> diffStats = new LinkedHashMap<>();
        diffStats.put("简单", 0L);
        diffStats.put("一般", 0L);
        diffStats.put("困难", 0L);
        QueryWrapper<QuestionItem> qiDiffQw = new QueryWrapper<>();
        qiDiffQw.select("difficulty, COUNT(*) AS cnt").groupBy("difficulty");
        List<Map<String, Object>> qiDiffRows = questionItemMapper.selectMaps(qiDiffQw);
        for (var m : qiDiffRows) {
            String diff = (String) m.get("difficulty");
            Number cnt = (Number) m.get("cnt");
            if (cnt == null) continue;
            if (diff == null || diff.isBlank()) diff = "一般";
            diffStats.merge(diff, cnt.longValue(), Long::sum);
        }
        r.put("difficultyStats", diffStats);

        return r;
    }

    /** question_item.type 字段（single/multi/judge/program/essay）→ 中文标签 */
    private String mapItemTypeToLabel(String type) {
        return switch (type == null ? "" : type) {
            case "single", "单选题" -> "单选题";
            case "multi", "多选题" -> "多选题";
            case "judge", "判断题" -> "判断题";
            case "program", "编程题" -> "编程题";
            case "essay", "问答题" -> "问答题";
            case "fill", "填空题" -> "填空题";
            default -> type;
        };
    }

    private void mergeTypeCount(Map<String, Long> stats, Map<String, Integer> tc) {
        var mapping = Map.of("single", "单选题", "multi", "多选题", "judge", "判断题", "program", "编程题");
        for (var e : tc.entrySet()) {
            String label = mapping.getOrDefault(e.getKey(), e.getKey());
            int cnt = e.getValue() != null ? e.getValue() : 0;
            stats.merge(label, (long) cnt, Long::sum);
        }
    }
}
