val queryDslVersion = "5.1.0"

plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.edu"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
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
    // Spring Boot Core
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 컨테이너 healthcheck 와 운영 관측에 쓴다. 노출 엔드포인트는 application.yml 에서 제한한다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // /actuator/prometheus 는 이 레지스트리가 있어야 생긴다. (#28)
    // exposure.include 에 prometheus 를 적어둬도 레지스트리가 없으면 404 였다.
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.data:spring-data-redis:3.2.5")
    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    // 채팅. STOMP + SimpleBroker 로 시작한다 - 기본 구성의 한계를 먼저 재기 위해서다. (#33)
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // 스키마 마이그레이션. 운영은 ddl-auto=none 이라 스키마 변경 경로가 없었다. (#29)
    // 평문 SQL 이라 DBA 리뷰와 수동 실행이 가능하다. 단일 DB(MySQL)라 Liquibase 의 추상화 이점이 없다.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Swagger (OpenAPI)
    // API Documentation(Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")           // 운영환경용 MySQL
    // H2 는 테스트에만 둔다. 운영 클래스패스에 있으면 datasource 설정이 없을 때
    // 조용히 임베디드 H2 로 떠서, 설정 실수가 부팅 실패가 아니라 "이상한 DB" 로 나타난다. (#49)
    testRuntimeOnly("com.h2database:h2")                 // 테스트환경용 H2 인메모리
    testRuntimeOnly("com.mysql:mysql-connector-j")

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:$queryDslVersion:jakarta")
    implementation("com.querydsl:querydsl-core:$queryDslVersion")
    annotationProcessor("com.querydsl:querydsl-apt:$queryDslVersion:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // AWS S3
    implementation("io.awspring.cloud:spring-cloud-aws-starter:3.4.0")
    implementation("software.amazon.awssdk:s3:2.32.9")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 마이그레이션 검증은 진짜 MySQL 이어야 한다.
    // H2 로는 engine=InnoDB, enum(...) 같은 MySQL 문법을 확인할 수 없다. (#29)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // DTO ↔ Entity Mapping
    implementation("org.modelmapper:modelmapper:3.1.0")

    // 썸네일 라이브러리
    implementation("net.coobird:thumbnailator:0.4.20")

    // JJWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // OAuth
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // LiveKit Server SDK
    implementation("io.livekit:livekit-server:0.8.2")
}

// 기본 테스트 태스크 (MySQL 테스트 제외)
tasks.named<Test>("test") {
    useJUnitPlatform()

    systemProperty("spring.profiles.active", "test")

    // 테스트 결과 로깅
    testLogging {
        events("passed", "skipped", "failed")
    }

    // 테스트 파일 패턴 지정
    include("**/*Test.class", "**/*Tests.class", "**/*IT.class")
}

// QueryDSL Q클래스 생성을 위한 소스 경로 설정
sourceSets {
    named("main") {
        java.srcDirs("src/main/java", "build/generated/sources/annotationProcessor/java/main")
    }
    named("test") {
        java.srcDirs("src/test/java")
        resources.srcDirs("src/test/resources")
    }
    // ★ 부하 측정 전용 소스셋. 운영 jar 에 들어가지 않는다. (#57)
    //
    //   여기에는 벤치마크 엔드포인트, 시드 데이터, 개선 전 코드(UnsafeJoinService),
    //   그리고 /api/perf/** 를 여는 보안 설정이 있다.
    //   @Profile("perf") 로 활성화는 막혀 있었지만 코드 자체는 운영 jar 에 실려 있었다.
    //   #49 의 H2 와 같은 종류의 문제다 - 운영에 있을 이유가 없는 것이 있으면
    //   언젠가 설정 실수 하나로 켜진다.
    create("perf") {
        java.srcDirs("src/perf/java")
        compileClasspath += named("main").get().output
        runtimeClasspath += named("main").get().output
    }
}

// perf 소스셋이 main 과 같은 의존성을 쓰게 한다.
configurations["perfImplementation"].extendsFrom(configurations.implementation.get())
configurations["perfRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())
configurations["perfAnnotationProcessor"].extendsFrom(configurations.annotationProcessor.get())
// Lombok 은 compileOnly 다. 이걸 빼면 @Slf4j 부터 못 찾는다.
configurations["perfCompileOnly"].extendsFrom(configurations.compileOnly.get())

// 부하 측정용 실행 jar. scripts/run-*.sh 가 이걸 쓴다.
//   ./gradlew perfBootJar  ->  build/libs/EduMeet-<version>-perf.jar
tasks.register<org.springframework.boot.gradle.tasks.bundling.BootJar>("perfBootJar") {
    group = "build"
    description = "부하 측정용 실행 jar. 벤치마크 엔드포인트를 포함한다."
    mainClass.set("com.edu.edumeet.EduMeetApplication")
    classpath(sourceSets["perf"].runtimeClasspath)
    archiveClassifier.set("perf")
    // 커스텀 BootJar 는 플러그인이 자동으로 채워주지 않는다.
    targetJavaVersion.set(java.targetCompatibility)
}

// Clean 시 QueryDSL 생성 파일도 삭제
tasks.named("clean") {
    doLast {
        file("build/generated/sources/annotationProcessor/java/main").deleteRecursively()
    }
}