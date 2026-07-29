package com.raredonut.mnarchive.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CORS 규칙은 틀려도 서버가 뜨는 데는 지장이 없고, 브라우저에서만 조용히 깨진다.
 * 그래서 컨텍스트 없이 규칙 자체를 직접 확인해 둔다.
 */
class CorsConfigTest {

    /** getCorsConfigurations() 가 protected 라 하위 클래스로 열어 쓴다. */
    private static class ExposedRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configs() {
            return getCorsConfigurations();
        }
    }

    private CorsConfiguration resolve(String path, String... allowedOrigins) {
        ExposedRegistry registry = new ExposedRegistry();
        new CorsConfig(allowedOrigins).addCorsMappings(registry);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.setCorsConfigurations(registry.configs());
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }

    @Test
    void 프론트엔드_오리진은_설정값을_따른다() {
        CorsConfiguration cors = resolve("/api/songs", "https://mn.raredonut.com");

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://mn.raredonut.com");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void 오리진이_여러_개면_모두_허용한다() {
        CorsConfiguration cors = resolve("/api/songs",
                "http://localhost:3000", "https://preview.raredonut.com");

        assertThat(cors.getAllowedOrigins())
                .containsExactly("http://localhost:3000", "https://preview.raredonut.com");
    }

    /**
     * Spring 은 등록 순서대로 훑다가 처음 매칭되는 설정을 반환한다(더 구체적인 쪽이 아니다).
     * /api/** 를 먼저 등록하면 북마클릿 규칙이 통째로 가려지므로 순서를 고정해 둔다.
     */
    @Test
    void 북마클릿_규칙이_와일드카드_매핑에_가려지지_않는다() {
        CorsConfiguration cors = resolve("/api/imports", "https://mn.raredonut.com");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://p.eagate.573.jp");
        assertThat(cors.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    /**
     * app.allowed-origins 는 중첩 플레이스홀더로 frontend-url 을 기본값 삼는다.
     * 둘이 어긋나면 로그인 후 돌아온 프론트가 곧바로 CORS 로 막히므로 실제 yml 로 확인한다.
     */
    @Test
    void allowed_origins_는_기본적으로_frontend_url_을_따라간다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    var env = context.getEnvironment();
                    assertThat(env.getProperty("app.allowed-origins"))
                            .isEqualTo(env.getProperty("app.frontend-url"))
                            .isEqualTo("http://localhost:3000");
                });
    }
}
