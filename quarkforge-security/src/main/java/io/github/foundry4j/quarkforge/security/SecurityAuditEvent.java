package io.github.foundry4j.quarkforge.security;

import java.lang.reflect.Method;

/// ## SecurityAuditEvent
/// 安全审计事件，通过 CDI 事件发布：
///
/// - `LOGIN_SUCCESS` / `LOGIN_FAILURE`：登录结果（auth 模块发布）
/// - `LOGOUT`：登出并吊销刷新令牌（auth 模块发布）
/// - `TOKEN_REVOKED`：改密/封号等场景批量吊销用户令牌（auth 模块发布）
/// - `ACCESS_DENIED`：角色/权限校验失败（security 拦截器发布）
///
/// `quarkforge-audit` 会监听该事件并写入审计日志（`entity_type=SECURITY`）；
/// 未引入 audit 模块时没有观察者，事件不会影响业务。
///
/// @param action 事件类型
/// @param actor  操作人（用户名或用户 ID）
/// @param target 操作对象（用户 ID 或 `类名#方法名`）
/// @param detail 补充说明，例如失败原因、缺失的角色/权限
public record SecurityAuditEvent(String action, String actor, String target, String detail) {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    public static SecurityAuditEvent loginSuccess(String userId, String username) {
        return new SecurityAuditEvent(LOGIN_SUCCESS, username, userId, null);
    }

    public static SecurityAuditEvent loginFailure(String username, String reason) {
        return new SecurityAuditEvent(LOGIN_FAILURE, username, null, reason);
    }

    public static SecurityAuditEvent logout(String userId) {
        return new SecurityAuditEvent(LOGOUT, userId, userId, null);
    }

    public static SecurityAuditEvent tokenRevoked(String userId, String reason) {
        return new SecurityAuditEvent(TOKEN_REVOKED, userId, userId, reason);
    }

    public static SecurityAuditEvent accessDenied(LoginUser user, Method method, String requirement) {
        String target = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        return new SecurityAuditEvent(ACCESS_DENIED, user.username(), target, requirement);
    }
}
