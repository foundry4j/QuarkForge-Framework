package io.github.foundry4j.quarkforge.excel;

/// ## ExcelExportStatus
/// 异步导出任务的生命周期。
public enum ExcelExportStatus {

    /// 已提交，等待执行。
    PENDING,

    /// 正在导出。
    RUNNING,

    /// 导出完成，可以下载。
    SUCCEEDED,

    /// 导出失败，原因见 [ExcelExportTask#message()]。
    FAILED
}
