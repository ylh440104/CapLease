package com.caplease.manager.kernel

enum class Capability(val bit: Long, val title: String, val description: String, val dangerous: Boolean) {
    FS_READ_APP(
        1L shl 0,
        "读取应用数据",
        "读取其他应用的私有目录内容",
        false
    ),
    FS_WRITE_APP(
        1L shl 1,
        "写入应用数据",
        "修改或删除其他应用的私有目录内容",
        false
    ),
    NET_RAW(
        1L shl 2,
        "原始网络套接字",
        "构造原始 IP 报文，用于抓包与网络诊断",
        false
    ),
    NET_ADMIN(
        1L shl 3,
        "网络管理",
        "修改路由表、防火墙与网络接口状态",
        true
    ),
    MOUNT(
        1L shl 4,
        "挂载文件系统",
        "挂载或重新挂载文件系统，可覆盖系统路径",
        true
    ),
    MODULE(
        1L shl 5,
        "加载内核模块",
        "向内核注入代码，等同于完全控制",
        true
    ),
    KERNEL_LOG(
        1L shl 6,
        "读取内核日志",
        "读取内核环形缓冲区，可能包含敏感地址信息",
        false
    ),
    PTRACE(
        1L shl 7,
        "调试其他进程",
        "附加到其他进程，读写其内存",
        false
    ),
    SET_IDENTITY(
        1L shl 8,
        "切换身份",
        "以其他用户身份执行操作，包含 root",
        true
    ),
    RELAX_LABEL(
        1L shl 9,
        "放宽访问标签",
        "绕过强制访问控制策略",
        true
    ),
    AUDIT_READ(
        1L shl 10,
        "读取审计记录",
        "查看权限授予与使用历史",
        false
    ),
    DEVICE_NODE(
        1L shl 11,
        "访问设备节点",
        "打开通常受限的设备文件",
        false
    );

    companion object {
        val all: Long = entries.fold(0L) { acc, item -> acc or item.bit }

        val dangerousMask: Long = entries.filter { it.dangerous }.fold(0L) { acc, item -> acc or item.bit }

        fun describe(mask: Long): List<Capability> = entries.filter { (mask and it.bit) != 0L }

        fun names(mask: Long): List<String> = describe(mask).map { it.title }

        fun isDangerous(mask: Long): Boolean = (mask and dangerousMask) != 0L
    }
}