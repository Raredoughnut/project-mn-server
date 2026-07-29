plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.raredonut"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    // JPA. spring-jdbc 를 함께 끌고 오므로 JdbcClient 를 별도 의존성 없이 쓸 수 있다.
    // 조회 = JPA, 임포트 배치 = JdbcClient. 같은 트랜잭션 안에서 섞어 쓴다.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // 스키마는 손으로 psql 치지 않는다. 로컬과 운영이 어긋나는 순간이 반드시 온다.
    // 변경 전
    implementation("org.flywaydb:flyway-core")

// 변경 후 — Boot 4에서는 스타터가 있어야 자동설정 모듈(spring-boot-flyway)이 들어온다
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

// API 문서 자동 생성. Boot 4 에는 반드시 3.x 라인 (2.x 는 Boot 3 용).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Testcontainers 2.x 에서 모듈명이 바뀌었다 (1.x 의 org.testcontainers:postgresql).
    // 옛 좌표를 쓰면 BOM 이 버전을 못 붙여 테스트 컴파일 자체가 실패한다.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
