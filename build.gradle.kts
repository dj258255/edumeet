val queryDslVersion = "5.0.0"

plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.practice"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Swagger (OpenAPI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

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
    // testImplementation("junit:junit:4.13.2") // 제거 (JUnit 5 기반으로 충분)

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:$queryDslVersion:jakarta")
    implementation("com.querydsl:querydsl-core:$queryDslVersion")
    annotationProcessor("com.querydsl:querydsl-apt:$queryDslVersion:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // DTO ↔ Entity Mapping
    implementation("org.modelmapper:modelmapper:3.1.0")
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

// Optional: bootJar 설정 (Docker 사용 시 유용)
// tasks.jar {
//     enabled = false
// }
// tasks.bootJar {
//     archiveFileName.set("app.jar")
// }

