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

    // Testcontainers 가 붙을 Docker API 버전. (#153)
    //
    //   Docker 29 는 API 1.40 미만을 거절하는데, Testcontainers 가 쓰는 docker-java 의
    //   기본값은 1.32 다. 그래서 최신 Docker 가 깔린 곳에서는 시험이 통째로 초기화 실패한다.
    //
    //       Docker 19.03  API 1.40
    //       Docker 20.10  API 1.41   <- 오래된 러너도 커버해야 한다
    //       Docker 29     API 1.40 이상만 허용
    //
    //   1.41 이 두 조건을 동시에 만족하는 값이다. 1.44 로 올리면 오래된 쪽이 깨진다.
    //   환경변수(DOCKER_API_VERSION)나 ~/.testcontainers.properties 로는 안 먹는다 -
    //   docker-java 가 읽는 것은 시험 JVM 의 시스템 속성이다.
    systemProperty("api.version", System.getProperty("api.version") ?: "1.41")

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

// ★ 공유 계약 파일을 테스트 입력으로 선언한다. (#91)
//
//   contracts/internal-api.json 은 backend/ 밖에 있어서 Gradle 이 모른다.
//   선언하지 않으면 계약만 바꿨을 때 test 태스크가 UP-TO-DATE 로 건너뛴다 -
//   즉 CI 가 초록인데 Java 와 파이썬의 계약이 갈라져 있는 상태가 된다.
//
//   실제로 확인했다. 계약의 헤더 이름을 바꿔도 Java 테스트는 안 돌았고,
//   --rerun-tasks 를 줘야만 잡혔다. 검출 구조에 난 구멍이었다.
tasks.named<Test>("test") {
    inputs.file(rootProject.file("../contracts/internal-api.json"))
        .withPropertyName("internalApiContract")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// ★ perf 소스셋을 check 에 묶는다.
//
//   묶기 전에는 아무도 컴파일하지 않았다. CI 는 `./gradlew build -x test` 만 돌리는데
//   커스텀 소스셋은 build 가 자동으로 잡지 않는다. 그래서 getRoomInfo 에 권한 검사용
//   파라미터가 추가됐을 때 perf 쪽이 따라가지 않았고, 아무도 몰랐다.
//
//   부하 측정 코드가 썩으면 "측정하려는 순간에" 발견된다. 그때가 제일 나쁘다.
tasks.named("check") { dependsOn("compilePerfJava") }

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