package com.raredonut.mnarchive;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 컨텍스트 로딩 테스트.
 * ddl-auto: validate 이므로, 엔티티와 Flyway 스키마가 어긋나면 이 테스트가 먼저 깨진다.
 * 실행하려면 Testcontainers 설정(또는 로컬 PostgreSQL)이 필요하다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MnArchiveApplicationTests {

    @Test
    void contextLoads() {
    }
}
