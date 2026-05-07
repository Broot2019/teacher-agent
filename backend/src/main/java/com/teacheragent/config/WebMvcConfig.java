package com.teacheragent.config;

import com.teacheragent.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /** 根路径到 index.html，hash 模式 SPA 入口 */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /**
     * 静态资源处理 + SPA fallback：
     * 1. 命中物理静态文件优先返回（assets/*.js、css 等）
     * 2. 未命中时若是 GET 非 API 路径，fallback 到 index.html，让前端路由处理
     * 这样未来若切换为 history 模式，刷新任意路径也不会出现 404
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 显式排除 API 路径与已知扩展名，避免把所有 404 都转向 index.html
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("error")) {
                            return null;
                        }
                        if (hasFileExtension(resourcePath)) {
                            return null;  // 真实静态资源缺失，让 NoResourceFoundException 处理器返回 404
                        }
                        // 看起来是前端路由路径，fallback 到 index.html
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }

                    private boolean hasFileExtension(String path) {
                        int slash = path.lastIndexOf('/');
                        int dot = path.lastIndexOf('.');
                        return dot > slash;
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/", "/index.html", "/favicon.ico",
                        "/assets/**", "/static/**",
                        "/error"
                );
    }
}
