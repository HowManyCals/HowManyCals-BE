package ksu.finalproject.global.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.x에서 H2ConsoleAutoConfiguration이 제거됨에 따라
 * H2 웹 콘솔 서블릿을 수동으로 등록합니다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
public class H2ConsoleConfig {

    /**
     * H2 웹 콘솔 서블릿을 /h2-console/* 경로에 등록합니다.
     * webAllowOthers=true: EC2 등 원격 환경에서도 접근 허용
     */
    @Bean
    public ServletRegistrationBean<JakartaWebServlet> h2ConsoleServlet() {
        ServletRegistrationBean<JakartaWebServlet> registration =
                new ServletRegistrationBean<>(new JakartaWebServlet(), "/h2-console", "/h2-console/*");
        registration.addInitParameter("webAllowOthers", "true");
        registration.setLoadOnStartup(1);
        return registration;
    }
}


