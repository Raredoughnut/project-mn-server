package com.raredonut.mnarchive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final MnOidcUserService oidcUserService;

    public SecurityConfig(MnOidcUserService oidcUserService) {
        this.oidcUserService = oidcUserService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 북마클릿 업로드는 세션이 아니라 바디의 토큰으로 인증한다(ImportService 가 처리).
                // cross-site 요청이라 세션 쿠키가 실리지 않으므로 여기서는 permitAll 이어야 한다.
                .requestMatchers("/api/imports").permitAll()
                .requestMatchers("/", "/login/**", "/oauth2/**", "/error").permitAll()
                .anyRequest().authenticated()
            )
            // /api/imports 는 쿠키를 쓰지 않으므로 CSRF 대상이 아니다. 나머지는 CSRF 유지.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/imports"))
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(ui -> ui.oidcUserService(oidcUserService))
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }
}
