package com.raredonut.mnarchive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;

@Configuration
public class SecurityConfig {

    private final MnOidcUserService oidcUserService;
    private final String frontendUrl;

    public SecurityConfig(MnOidcUserService oidcUserService,
                          @Value("${app.frontend-url}") String frontendUrl) {
        this.oidcUserService = oidcUserService;
        this.frontendUrl = frontendUrl;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 북마클릿 업로드: 세션이 아니라 바디의 토큰으로 인증한다(ImportService 가 처리).
                        // cross-site 요청이라 세션 쿠키가 실리지 않으므로 permitAll 이어야 한다.
                        .requestMatchers("/api/imports").permitAll()

                        // /api/me 는 미로그인 상태에서도 '401'을 돌려줘야 한다.
                        // 인증을 강제하면 로그인 페이지로 리다이렉트되어, 프론트가 상태를 판별할 수 없다.
                        .requestMatchers("/api/me").permitAll()

                        .requestMatchers("/", "/login/**", "/oauth2/**", "/error").permitAll()
                        // API 문서. 개발 중에는 열어두고, 운영 배포 시 차단을 검토한다.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )

                // API 요청(XHR/fetch)에는 로그인 페이지로 리다이렉트하지 않고 401 을 준다.
                // 리다이렉트를 주면 프론트의 fetch 가 구글 로그인 HTML 을 받아버려 CORS 오류로 보인다.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"))
                )

                // /api/imports 는 쿠키를 쓰지 않으므로 CSRF 대상이 아니다. 나머지는 CSRF 유지.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/imports"))

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(ui -> ui.oidcUserService(oidcUserService))
                        // 로그인이 끝나면 백엔드가 아니라 프론트로 돌려보낸다.
                        // 사용자는 세션 쿠키를 지닌 채 프론트 화면으로 복귀한다.
                        .defaultSuccessUrl(frontendUrl, true)
                )

                .logout(logout -> logout
                        .logoutSuccessUrl(frontendUrl)
                );

        return http.build();
    }
}
