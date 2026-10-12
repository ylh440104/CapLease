# CapLease 内核协议 v1

面向 KernelPatch KPM 侧实现的接口约定。用户态实现位于 `app/src/main/cpp`，
两侧结构体必须逐字节一致。

## 1. 信道

单一入口，复用系统调用号 **45**（truncate）。

```
syscall(45, exchange_ptr, tag)
```

`tag = (0x434C5031 | op)`，其中 `0x434C5031` 为协议魔数，`op` 见下表。

45 号在 Android 上始终可调用，未装载模块时原样转发给内核的 truncate 实现，
装载后由钩子按 tag 前缀识别并接管，卸载后立即恢复原生行为。

## 2. 操作码

| op | 名称 | 认证要求 | 说明 |
|---|---|---|---|
| 1 | Hello | 无 | 探活，返回魔数 |
| 2 | Info | 无 | 返回内核与服务能力概况 |
| 3 | Challenge | 无 | 申请一次性挑战随机数 |
| 4 | Authenticate | 挑战 | 提交证明，换取会话令牌 |
| 5 | SessionDrop | 令牌 | 主动结束会话 |
| 6 | Pair | 无 | 首次配对，写入验证子 |
| 7 | Unpair | 无 | 清除配对状态 |
| 16 | LeaseAcquire | 令牌 | 签发租约 |
| 17 | LeaseRelease | 令牌 | 释放指定租约 |
| 18 | LeaseQuery | 令牌 | 列举活跃租约 |
| 19 | LeaseExtend | 令牌 | 延长租约有效期 |
| 20 | LeaseRevokeAll | 令牌 | 撤销全部租约 |
| 32 | Exec | 令牌 | 一次性执行 |
| 48 | AuditRead | 令牌 | 读取审计环 |
| 49 | AuditClear | 令牌 | 清空审计环 |

## 3. 状态码

| 值 | 名称 | 含义 |
|---|---|---|
| 0 | OK | 成功 |
| -1 | NOT_READY | 内核侧未就绪 |
| -2 | BAD_MAGIC | 魔数不匹配 |
| -3 | BAD_VERSION | 协议版本不匹配 |
| -4 | UNKNOWN_OP | 未知操作码 |
| -5 | INVALID_ARGUMENT | 参数非法 |
| -6 | UNAUTHENTICATED | 缺少有效会话令牌 |
| -7 | BAD_CREDENTIAL | 证明校验失败 |
| -8 | CHALLENGE_MISSING | 未先申请挑战 |
| -9 | CHALLENGE_EXPIRED | 挑战超出时间窗 |
| -10 | ALREADY_PAIRED | 已处于配对状态 |
| -11 | NOT_PAIRED | 尚未配对 |
| -12 | CAPABILITY_DENIED | 请求的能力不可授予 |
| -13 | LEASE_MISSING | 租约不存在 |
| -14 | LEASE_EXPIRED | 租约已到期 |
| -15 | LEASE_LIMIT_REACHED | 槽位耗尽 |
| -16 | TARGET_DENIED | 目标不受策略允许 |
| -17 | INTERNAL | 内核内部错误 |
| -18 | NOT_SUPPORTED | 当前内核不支持该能力 |
| -19 | BUFFER_TOO_SMALL | 输出缓冲不足 |

## 4. 结构体

所有字段按小端序，无隐式填充，两侧均须做 `static_assert` 校验尺寸。

### Request

```
offset  size  field
0       4     magic          固定 0x434C5031
4       4     version        固定 1
8       4     op             操作码
12      4     payloadLength  载荷有效长度
16      8     arg0
24      8     arg1
32      8     arg2
40      8     arg3
48      32    token          会话令牌
80      512   payload        载荷
```

总长 592 字节。

### Response

```
offset  size  field
0       4     status         状态码
4       4     payloadLength  载荷有效长度
8       8     value          标量返回值
16      512   payload        载荷
```

总长 528 字节。

## 5. 载荷布局

### InfoPayload（32 字节）

```
0    4    kernelPatchVersion   major<<16 | minor<<8 | patch
4    4    kernelVersion         同上编码
8    4    protocolVersion
12   4    leaseSlots
16   8    capabilityMask
24   8    bootSeconds
```

### ChallengePayload（48 字节）

```
0    32   nonce
32   8    issuedSeconds
40   4    windowSeconds
44   4    reserved
```

### AuthenticatePayload（64 字节）

```
0    32   proof
32   32   clientNonce
```

### PairPayload（48 字节）

```
0    32   verifier
32   16   salt
```

### LeaseRecord（48 字节，LeaseQuery 返回数组）

```
0    8    leaseId
8    8    capabilities
16   8    expirySeconds
24   8    cookie
32   4    ownerPid
36   4    ownerUid
40   4    flags
44   4    reserved
```

### AuditRecord（32 字节，AuditRead 返回数组）

```
0    8    timestampSeconds
8    8    value
16   4    kind
20   4    actorUid
24   4    actorPid
28   4    detail
```

## 6. 能力位

| 位 | 名称 | 语义 |
|---|---|---|
| 0 | FS_READ_APP | 读取其他应用私有目录 |
| 1 | FS_WRITE_APP | 修改其他应用私有目录 |
| 2 | NET_RAW | 原始套接字 |
| 3 | NET_ADMIN | 网络配置 |
| 4 | MOUNT | 挂载文件系统 |
| 5 | MODULE | 加载内核模块 |
| 6 | KERNEL_LOG | 读取内核日志 |
| 7 | PTRACE | 调试其他进程 |
| 8 | SET_IDENTITY | 切换身份 |
| 9 | RELAX_LABEL | 放宽访问标签 |
| 10 | AUDIT_READ | 读取审计环 |
| 11 | DEVICE_NODE | 访问设备节点 |

高风险集合为 `MODULE | SET_IDENTITY | RELAX_LABEL | MOUNT | NET_ADMIN`。

## 7. 租约标志

| 位 | 名称 | 语义 |
|---|---|---|
| 0 | ONE_SHOT | 执行一次后立即回收 |
| 1 | INHERIT | 允许子进程继承，继承时能力只能收缩 |
| 2 | NO_AUDIT | 不写入审计环 |

## 8. 握手流程

```
客户端                                     内核
  |                                          |
  |-- Pair(verifier, salt) ----------------->|   首次配对
  |<-- OK -----------------------------------|
  |                                          |
  |-- Challenge() -------------------------->|
  |<-- OK, nonce, issuedSeconds, window -----|
  |                                          |
  |   proof = HMAC-SHA256(secret, nonce || clientNonce)
  |                                          |
  |-- Authenticate(proof, clientNonce) ----->|
  |<-- OK, sessionToken ---------------------|
  |                                          |
  |-- LeaseAcquire(caps, seconds, flags) --->|
  |<-- OK, leaseId --------------------------|
  |                                          |
  |-- SessionDrop() ------------------------>|
  |<-- OK -----------------------------------|
```

`secret` 由口令经 PBKDF2-HMAC-SHA256（120000 轮）与本地盐派生，仅在内存中驻留。

## 9. 内核侧需挂接的函数

以下符号来自目标设备 `boot_a.bin` 重建的符号表，偏移在运行时由
`kallsyms_lookup_name` 解析，基址随机化不影响。

| 用途 | 符号 | 静态偏移 |
|---|---|---|
| 系统调用总入口 | `el0_svc_common` | `0x809b0e0` |
| 未实现调用回退 | `sys_ni_syscall` | `0x80e2128` |
| 进程派生 | `copy_process` | `0x80b3cf8` |
| 进程退出 | `do_exit` | `0x80bab9c` |
| 任务回收 | `release_task` | `0x80ba2f4` |
| 凭据准备 | `prepare_creds` | `0x80e3f04` |
| 凭据提交 | `commit_creds` | `0x80e4204` |
| 临时覆盖 | `override_creds` | `0x80e4470` |
| 撤销覆盖 | `revert_creds` | `0x80e449c` |
| 访问判定拒绝 | `avc_denied` | `0x8469650` |
| 随机数 | `get_random_bytes` | `0x85a377c` |
| 摘要 | `crypto_shash_digest` | `0x8491b58` |
| 单调时间 | `ktime_get_seconds` | `0x8151978` |
| 内存分配 | `__get_free_pages` | `0x8209a14` |
| 可执行内存 | `vmalloc_exec` | `0x824a530` |

`el0_svc_common` 内部以 `cmp w20, #0x1b2` 判定系统调用号上界。钩子在该函数入口处
先比对 `arg0` 是否为 45 且 `arg2` 是否带协议前缀，两者都命中才接管；否则原样放行，
不干扰正常 truncate 调用。

## 10. 约束

目标内核 `CONFIG_KALLSYMS_ALL` 未启用，数据符号不可解析。因此本协议不依赖
任何数据符号，全部通过函数挂钩与寄存器读取实现：

- 当前任务指针由 `sp_el0` 读取，不查 `init_task`
- 权限判定拦截落在 `avc_denied`，不触碰 `selinux_state`
- 无 `CONFIG_KPROBES`，挂钩一律使用内联跳转

协议不定义任何用于持久化的落盘格式，配对验证子与审计环均只存在于内核内存，
设备重启后需要重新握手。