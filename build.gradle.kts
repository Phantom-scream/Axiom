plugins {
  java
  id("org.springframework.boot") version "4.1.1"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.diffplug.spotless") version "8.10.3"
}

group = "com.axiom"

version = "0.1.0-SNAPSHOT"

description = "CI Failure Intelligence"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  runtimeOnly("org.postgresql:postgresql")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-webmvc-test")
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("org.testcontainers:postgresql:1.21.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }

spotless {
  java {
    target("src/*/java/**/*.java")
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle { ktfmt() }
  format("yaml") {
    target("*.yml", "*.yaml", ".github/workflows/*.yml")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
