package io.github.foundry4j.quarkforge.notification.entity;

/// ## NotificationType
/// 通知类型枚举，业务模块可以用 `valueOf` 或直接扩展字符串字段
/// 以支持自定义类型（数据库中存为字符串，而非枚举序号）。
public enum NotificationType {
    /// 系统通知：发布公告、维护提醒等。
    SYSTEM,
    /// 任务分配：新任务指派给你。
    TASK,
    /// 评论回复：有人回复了你的评论。
    COMMENT,
    /// 点赞收藏：有人点赞了你的内容。
    LIKE,
    /// 提到：有人在评论中 @了你。
    MENTION,
    /// 审批流程：申请已通过/驳回。
    APPROVAL,
    /// 自定义业务类型。
    CUSTOM
}
