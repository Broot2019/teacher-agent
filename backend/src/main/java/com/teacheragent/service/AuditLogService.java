package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.CurrentUser;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.entity.AuditLog;
import com.teacheragent.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public void log(String action, String targetType, Object targetId, Object detail, String status, String errorMsg) {
        try {
            AuditLog l = new AuditLog();
            CurrentUser u = CurrentUserHolder.get();
            if (u != null) {
                l.setUserId(u.getId());
                l.setUsername(u.getUsername());
            }
            l.setAction(action);
            l.setTargetType(targetType);
            l.setTargetId(targetId == null ? null : String.valueOf(targetId));
            if (detail != null) {
                String s = detail.toString();
                l.setDetail(s.length() > 4000 ? s.substring(0, 4000) : s);
            }
            l.setStatus(status == null ? "success" : status);
            l.setErrorMsg(errorMsg);
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest req = attrs.getRequest();
                    l.setIp(getIp(req));
                    String ua = req.getHeader("User-Agent");
                    if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
                    l.setUserAgent(ua);
                }
            } catch (Exception ignored) { }
            auditLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("写审计日志失败: {}", e.getMessage());
        }
    }

    public void logSuccess(String action, String targetType, Object targetId, Object detail) {
        log(action, targetType, targetId, detail, "success", null);
    }

    public void logFailure(String action, String targetType, Object targetId, String error) {
        log(action, targetType, targetId, null, "failed", error);
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getRemoteAddr();
        return ip;
    }

    public List<AuditLog> list(int limit, String action, Long userId) {
        LambdaQueryWrapper<AuditLog> q = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) q.eq(AuditLog::getAction, action);
        if (userId != null) q.eq(AuditLog::getUserId, userId);
        q.orderByDesc(AuditLog::getCreateTime).last("LIMIT " + Math.min(limit, 500));
        return auditLogMapper.selectList(q);
    }
}
