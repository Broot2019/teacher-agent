package com.teacheragent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在请求最早期强制 UTF-8 编码（影响 multipart form 字段解析）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@org.springframework.boot.web.servlet.ServletComponentScan
public class Utf8RequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String enc = request.getCharacterEncoding();
        if (enc == null || !enc.toLowerCase().contains("utf")) {
            request.setCharacterEncoding("UTF-8");
        }
        response.setCharacterEncoding("UTF-8");
        filterChain.doFilter(request, response);
    }
}
