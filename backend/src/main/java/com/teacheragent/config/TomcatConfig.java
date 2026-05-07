package com.teacheragent.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    /** 强制 Tomcat Connector + Context 使用 UTF-8 解析 multipart 字段 */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatUtf8Customizer() {
        return factory -> {
            factory.addContextCustomizers((TomcatContextCustomizer) context -> {
                context.setRequestCharacterEncoding("UTF-8");
                context.setResponseCharacterEncoding("UTF-8");
            });
            factory.addConnectorCustomizers((TomcatConnectorCustomizer) connector -> {
                connector.setURIEncoding("UTF-8");
                connector.setProperty("useBodyEncodingForURI", "true");
            });
        };
    }
}
