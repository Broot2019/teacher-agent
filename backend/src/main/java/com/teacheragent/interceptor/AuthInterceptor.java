package com.teacheragent.interceptor;

import com.teacheragent.common.AdminOnly;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUser;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 鉴权拦截器
 *
 * 白名单：登录、注册、调试接口、静态资源
 * 普通接口：需要登录（JWT 有效）
 * @AdminOnly 接口：需要 admin 角色
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> WHITE_LIST_PREFIX = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/captcha",
            "/api/password-reset"
    );

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        // OPTIONS 直接放行（CORS 预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 白名单
        for (String w : WHITE_LIST_PREFIX) {
            if (uri.startsWith(w)) return true;
        }

        // 静态资源 / 非 /api 路径放行（前端 SPA）
        if (!uri.startsWith("/api/")) return true;

        String token = extractToken(request);
        if (token == null) {
            throw new BusinessException(401, "未登录或令牌已过期");
        }

        CurrentUser user = jwtService.parse(token);
        if (user == null) {
            throw new BusinessException(401, "登录令牌无效");
        }
        CurrentUserHolder.set(user);

        // @AdminOnly 检查
        if (handler instanceof HandlerMethod hm) {
            AdminOnly methodAnno = hm.getMethodAnnotation(AdminOnly.class);
            AdminOnly classAnno = hm.getBeanType().getAnnotation(AdminOnly.class);
            if ((methodAnno != null || classAnno != null) && !user.isAdmin()) {
                throw new BusinessException(403, "需要管理员权限");
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserHolder.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7).trim();
        // 兼容直接传 token 的客户端
        String t = request.getHeader("X-Token");
        if (t != null && !t.isBlank()) return t.trim();
        // SSE 接口兜底：原生 EventSource 不能设自定义 header，允许从查询参数 ?token=xxx 取
        // 仅对 SSE 路径开放，避免 token 被记录到普通 access log 暴露
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/api/task/sse/")) {
            String qt = request.getParameter("token");
            if (qt != null && !qt.isBlank()) return qt.trim();
        }
        return null;
    }
}
