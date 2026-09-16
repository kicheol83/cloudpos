plugins { id("cloudpos.spring-service") }

dependencyManagement {
    imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0") }
}

dependencies {

    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
}
