package io.cloudpos.tenancy;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
public class TenancyAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilter() {
        FilterRegistrationBean<TenantContextFilter> bean =
                new FilterRegistrationBean<>(new TenantContextFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    public RlsTenantAspect rlsTenantAspect(EntityManager entityManager) {
        return new RlsTenantAspect(entityManager);
    }
}
