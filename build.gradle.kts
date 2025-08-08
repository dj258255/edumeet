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
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.data:spring-data-redis:3.2.5")
    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Swagger (OpenAPI)
    // API Documentation(Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")           // 운영환경용 MySQL
    runtimeOnly("com.h2database:h2")
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
}

// Clean 시 QueryDSL 생성 파일도 삭제
tasks.named("clean") {
    doLast {
        file("build/generated/sources/annotationProcessor/java/main").deleteRecursively()
    }
}