package io.cloudpos.tenancy;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(100)
public class RlsTenantAspect {

    private final EntityManager entityManager;

    public RlsTenantAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("@within(org.springframework.transaction.annotation.Transactional)"
            + " || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object bindTenant(ProceedingJoinPoint pjp) throws Throwable {
        entityManager
                .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", TenantContext.require().toString())
                .getSingleResult();
        return pjp.proceed();
    }
}
