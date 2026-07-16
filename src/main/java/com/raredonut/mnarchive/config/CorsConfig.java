package com.raredonut.mnarchive.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 북마클릿은 이게이트 오리진에서 실행되므로 cross-origin 이다.
 * 다만 text/plain + 표준 헤더만 쓰므로 프리플라이트가 발생하지 않는다.
 *
 * allowCredentials 는 켜지 않는다 — 토큰이 곧 신원이고 쿠키는 불필요하다.
 * 켜는 순간 보안 표면만 넓어진다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/imports")
                .allowedOrigins("https://p.eagate.573.jp")
                .allowedMethods("POST")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
