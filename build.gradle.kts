val queryDslVersion = "5.0.0"


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
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Swagger (OpenAPI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Devtools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:$queryDslVersion:jakarta")
    implementation("com.querydsl:querydsl-core:$queryDslVersion")
    annotationProcessor("com.querydsl:querydsl-apt:$queryDslVersion:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // DTO ↔ Entity Mapping
    implementation("org.modelmapper:modelmapper:3.1.0")

    // 썸네일 라이브러리
    implementation("net.coobird:thumbnailator:0.4.20")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    include("**/*Test.class", "**/*Tests.class", "**/*IT.class")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

sourceSets {
    named("main") {
        java.srcDirs("src/main/java", "build/generated/sources/annotationProcessor/java/main")
    }
    named("test") {
        java.srcDirs("src/test/java")
        resources.srcDirs("src/test/resources")

    }
}
