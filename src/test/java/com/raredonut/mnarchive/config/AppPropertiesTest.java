package com.raredonut.mnarchive.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * app.* 설정의 기본값과 환경변수 덮어쓰기.
 *
 * <p>이 프로젝트는 프로필 파일이 아니라 환경변수로 환경 차이를 다룬다. 기본값은 전부
 * 로컬 개발에 맞춰져 있어서, <b>운영 배포에서 빠뜨리면 조용히 잘못 동작한다</b> —
 * 기동은 멀쩡히 되고 증상만 나중에 나타난다. 어떤 값이 그런지 여기에 못박아 둔다.
 */
class AppPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void 기본값은_로컬_개발에_맞춰져_있다() {
        runner.run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("app.frontend-url")).isEqualTo("http://localhost:3000");
            assertThat(env.getProperty("app.api-base")).isEqualTo("http://localhost:8080");
            assertThat(env.getProperty("app.allowed-origins")).isEqualTo("http://localhost:3000");
            assertThat(env.getProperty("app.cookie-domain")).isEmpty();
            assertThat(env.getProperty("app.api-docs-enabled")).isEqualTo("true");
        });
    }

    /** 스크립트는 고칠 일이 드물어 CDN 이 기본이다. 로컬에서 고칠 때만 덮어쓴다. */
    @Test
    void cdn_url_기본값은_운영_CDN_이다() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty("app.cdn-url"))
                .isEqualTo("https://cdn.raredonut.com/popn-sync.js"));
    }

    @Test
    void 환경변수로_덮어쓸_수_있다() {
        runner.withPropertyValues(
                "APP_API_BASE=https://api.raredonut.com",
                "APP_CDN_URL=http://localhost:9000/popn-sync.js",
                "FRONTEND_URL=https://mn.raredonut.com",
                "APP_COOKIE_DOMAIN=raredonut.com",
                "API_DOCS_ENABLED=false"
        ).run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("app.api-base")).isEqualTo("https://api.raredonut.com");
            assertThat(env.getProperty("app.cdn-url")).isEqualTo("http://localhost:9000/popn-sync.js");
            assertThat(env.getProperty("app.frontend-url")).isEqualTo("https://mn.raredonut.com");
            assertThat(env.getProperty("app.cookie-domain")).isEqualTo("raredonut.com");
            assertThat(env.getProperty("app.api-docs-enabled")).isEqualTo("false");
        });
    }

    /** allowed-origins 는 중첩 플레이스홀더로 frontend-url 을 따라간다. 둘이 어긋나면 로그인 후 CORS 로 막힌다. */
    @Test
    void allowed_origins_는_frontend_url_을_따라간다() {
        runner.withPropertyValues("FRONTEND_URL=https://mn.raredonut.com")
                .run(context -> assertThat(context.getEnvironment().getProperty("app.allowed-origins"))
                        .isEqualTo("https://mn.raredonut.com"));
    }

    /** 오리진이 여러 개 필요할 때만 별도로 지정한다. */
    @Test
    void allowed_origins_는_따로_지정하면_frontend_url_을_무시한다() {
        runner.withPropertyValues(
                "FRONTEND_URL=https://mn.raredonut.com",
                "CORS_ALLOWED_ORIGINS=https://mn.raredonut.com,https://preview.raredonut.com"
        ).run(context -> assertThat(context.getEnvironment().getProperty("app.allowed-origins"))
                .isEqualTo("https://mn.raredonut.com,https://preview.raredonut.com"));
    }
}
