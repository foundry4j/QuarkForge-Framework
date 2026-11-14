package io.github.foundry4j.quarkforge.security;

import io.github.foundry4j.quarkforge.core.exception.BizException;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/// ## RequiresRoleInterceptor
/// `@RequiresRole` 的角色校验拦截器。
/// 未登录时由 [AuthContext#currentUser()] 抛出 `401`，角色不足抛出 `403`。
///
/// 与 [RequiresPermissionInterceptor] 拆分为两个拦截器：CDI 要求拦截器上的
/// 全部 binding 类型都出现在目标上才生效，合并为一个拦截器会导致
/// 只标一种注解的方法被静默放行。
@RequiresRole
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
public class RequiresRoleInterceptor {

    @Inject
    AuthContext authContext;

    @Inject
    Event<SecurityAuditEvent> auditEvents;

    @AroundInvoke
    Object check(InvocationContext ctx) throws Exception {
        RequiresRole rule = RbacSupport.resolve(ctx.getMethod(), RequiresRole.class);
        if (rule == null || rule.value().length == 0) {
            return ctx.proceed();
        }
        LoginUser user = authContext.currentUser();
        try {
            RbacSupport.require(user.roles(), rule.value(), rule.logical(), "角色");
        } catch (BizException e) {
            auditEvents.fire(SecurityAuditEvent.accessDenied(user, ctx.getMethod(),
                    "缺少角色: " + String.join(",", rule.value())));
            throw e;
        }
        return ctx.proceed();
    }
}
