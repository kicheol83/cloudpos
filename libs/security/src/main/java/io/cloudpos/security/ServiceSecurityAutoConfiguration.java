package io.cloudpos.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@AutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(ServiceSecurityProperties.class)
public class ServiceSecurityAutoConfiguration {

    @Bean
    public SecurityFilterChain serviceSecurityFilterChain(
            HttpSecurity http, ServiceSecurityProperties properties) throws Exception {

        String[] publicPaths = properties.publicPaths().toArray(String[]::new);

        http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new GatewayAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> {
                    if (publicPaths.length > 0) {
                        requests.requestMatchers(publicPaths).permitAll();
                    }
                    requests.anyRequest().authenticated();
                });

        return http.build();
    }
}
