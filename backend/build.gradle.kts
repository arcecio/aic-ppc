plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.lacity"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val jjwtVersion = "0.12.6"

ext["testcontainers.version"] = "1.21.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // PDF text extraction (plan parsing) + PDF report generation.
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // DOCX parsing for narrative/supporting documents.
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // OpenAPI / Swagger UI for the integration API documentation.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
}

// Convenience task that points Testcontainers at Colima's Docker daemon, matching
// the local dev setup (see docs/07-testing.md).
tasks.register<Test>("testAll") {
    description = "Runs all tests, pointing Testcontainers at Colima's Docker daemon."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    val colimaSocket = "${System.getProperty("user.home")}/.colima/default/docker.sock"
    environment("DOCKER_HOST", "unix://$colimaSocket")

    doFirst {
        require(file(colimaSocket).exists()) {
            "Colima Docker socket not found at $colimaSocket. Start Colima first: `colima start`."
        }
    }
}
