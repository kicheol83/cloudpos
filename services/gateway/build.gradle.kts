plugins { id("cloudpos.spring-service") }

dependencyManagement {
    imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0") }
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    testImplementation("io.projectreactor:reactor-test")
}
