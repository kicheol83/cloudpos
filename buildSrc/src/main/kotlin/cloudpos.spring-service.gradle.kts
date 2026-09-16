plugins {
    id("cloudpos.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "local")
}