plugins {
    java
    jacoco
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    systemProperty("testcontainers.reuse.enable", "true")
}
