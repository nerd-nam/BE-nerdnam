plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.nerdnam"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // 웹 (Spring Boot 4 부터 spring-boot-starter-web → spring-boot-starter-webmvc)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")   // /actuator/prometheus (관리 포트, BE #44)

    // 로그인: Spring Security + OAuth2 소셜 로그인(Google·카카오), 쿠키 세션 (BE-03)
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")

    // 세션 저장소·결과 캐시: Redis (기획서 5.3). 세션을 서버 메모리에 두면 재시작·서버 2대에서 로그인이 풀린다
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")

    // DB: JPA(Hibernate) + Flyway + PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // 보일러플레이트 줄이기 (@Getter, @RequiredArgsConstructor 정도만 쓴다. @Setter/@Data 금지)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // 테스트: JUnit5 + AssertJ + Testcontainers(실제 PostgreSQL+pgvector 컨테이너)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // C-04 계약 YAML을 읽어 실제 JSON 요청·응답과 대조한다
    testImplementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    // 웹 계약은 테스트에서만 내보낸다. 운영 서버에는 문서 엔드포인트를 추가하지 않는다.
    testImplementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.1.1")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("exportWebApiContract") {
    description = "현재 컨트롤러에서 C-03 웹 API 계약을 갱신한다"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter.includeTestsMatching("com.nerdnam.contract.WebApiContractTest.웹_계약이_현재_컨트롤러와_같다")
    systemProperty("nerdnam.contract.update", "true")
    outputs.upToDateWhen { false }
}
