# CapLease

Android 权限管理器。把 root 从"有或没有"改成一笔有时限的授权。

## 为什么

现有授权方案只有两种状态：给了，或者没给。给了就是永久、全权、跟着 UID 走。

CapLease 换一种模型：每次授权是一张租约，写明能用哪些能力、能用多久、给谁用。
到期自动收回，进程退出自动收回。

## 模型

| | 常见做法 | CapLease |
|---|---|---|
| 授权内容 | 全部或没有 | 12 项能力，按位选择 |
| 有效期 | 永久 | 到期失效 |
| 归属 | UID | 进程 |
| 子进程继承 | 全量复制 | 只能更小 |
| 单次执行 | 无 | 有 |
| 凭据 | 静态密钥 | 无，一次性挑战 |
| 记录 | 部分 | 内核侧环形缓冲 |

## 十二项能力

```
读取应用数据    写入应用数据    原始套接字      网络管理
挂载文件系统    加载内核模块    读取内核日志    调试其他进程
切换身份        放宽访问标签    读取审计记录    访问设备节点
```

其中加载内核模块、切换身份、放宽访问标签、挂载、网络管理五项会绕过系统隔离，
界面上单独标记。

## 工作方式

### 通道

用系统调用号 435。这个号在 arm64 表里没有实现，没装模块时返回 ENOSYS，装了由
钩子接走。不占用任何在用的调用号，卸载后没有残留。

### 握手

口令经 PBKDF2-HMAC-SHA256 跑 120000 轮，配合本机盐得到密钥。这个密钥只用来算
一次性挑战的 HMAC，不能拿去提权。会话令牌有独立生存期。

### 租约

每条租约记录持有者、能力位、到期时间、标志位。内核在每次系统调用进入时分发，
到期或持有者退出即回收。

### 单次执行

调用方进程身份不变，权限只注入派生的子进程，子进程退出就没了。

## 结构

```
app/src/main/cpp/
  protocol.h          线格式
  sha256.{h,cpp}      哈希
  hmac.{h,cpp}        HMAC、PBKDF2、常量时间比较
  supercall.{h,cpp}   系统调用通道
  types.h             能力与类型
  bridge.cpp          JNI 桥

app/src/main/java/com/caplease/manager/
  kernel/             状态码、能力、记录解码、JNI 声明
  core/               凭据保管、口令校验、客户端
  ui/                 状态、视图模型、主题、组件、界面

kpm/
  protocol.h          线格式（与用户态一致）
  state.{h,c}         会话、租约、审计环
  main.c              模块入口、钩子、分发
  build/caplease.kpm

PROTOCOL.md           接口约定
```

## 构建

推送到 main 后 GitHub Actions 会自动跑，产出 APK 和 KPM 两个 artifact。

本地构建 APK：

```
./gradlew :app:assembleDebug
```

需要 Android SDK 和 NDK，只编 arm64-v8a。

本地构建 KPM：

```
cd kpm
KP=/path/to/KernelPatch TARGET_COMPILE=aarch64-linux-gnu- make
```

KPM 依赖 KernelPatch 源码树的头文件。

## 安装

1. 刷入带 KernelPatch 的 boot 镜像
2. 把 `caplease.kpm` 放到设备上，用 kptools 装载
3. 装 APK

模块装载后在 `el0_svc_common` 挂前置钩子，接管 435 号调用。应用启动时会探测，
探测不到就显示内核服务不可用。

## 已知限制

内核配置 `CONFIG_KALLSYMS_ALL` 关闭，数据符号取不到，所以模块只引用函数符号。

`CONFIG_KPROBES` 关闭，钩子全部走内联跳转。

`CONFIG_USER_NS` 关闭，依赖用户命名空间的方案在这台设备上不可用。

## 不做什么

不申请 Android 权限，不读写外部存储，不参与系统备份，口令不落盘。

## 许可

GPL-2.0