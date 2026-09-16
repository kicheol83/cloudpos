plugins { id("cloudpos.java-conventions") }

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-web:3.5.0")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa:3.5.0")
    compileOnly("org.springframework.boot:spring-boot-starter-aop:3.5.0")
}
