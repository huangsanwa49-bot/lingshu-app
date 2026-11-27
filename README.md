# 灵枢系统 · 安卓 App v0.1

家人用的手机入口：登录即识别是哪位家人，和笨笨同学对话，退到后台后以悬浮小助理常驻，播报问候、天气和通知。连接家里的灵枢系统（Hub）。

## 第一版功能

| 功能 | 说明 |
|---|---|
| 配对 | 填迷你主机地址，核对证书指纹（与运维门户总览页显示的一致）后配对；之后只认这把公钥 |
| 登录 | 用户名 + 密码 + 动态口令（验证器 App 离线生成，不走短信）。登录后灵枢按会话识别家人 |
| 今日 | 问候、天气、关心提醒，一键播报；下面列出发给本人的通知，可逐条标记已读 |
| 对话 | 文字对话，经灵枢转给该成员的 Hermes profile；Hermes 接入前是模拟回复 |
| 悬浮小助理 | App 退到后台时显示“家”字小圆球：点一下弹出快捷对话；长按播报问候与天气；可拖动、自动贴边 |
| 播报 | 按时段问候（早、中、下午、晚上各一次，夜里不出声）；有新通知时圆球旁弹出一句话并朗读，同时发系统通知；常驻通知上可一键“停止” |

现阶段只能在家里的 Wi-Fi 下用（App 直连迷你主机的局域网地址）。

## 安全设计

- **只连配对过的灵枢**：自签证书 + 公钥固定（SPKI SHA-256）。配对时人工核对指纹；之后换证书、中间人、连错设备一律拒绝。禁止明文 HTTP，不信任用户自装的 CA。
- **凭据**：登录会话 token 用 Android 密钥库里的 AES-256-GCM 硬件密钥加密保存；密码与动态口令不落盘。会话 30 天到期，管理员可在门户里随时注销；被注销后 App 自动回到登录页。退出登录时清掉已发出的系统通知。
- **不留痕**：对话只在内存里，关掉 App 或退出登录即清空；禁止系统备份与设备迁移；界面禁止截屏录屏（FLAG_SECURE），最近任务里不显示内容；锁屏上的通知只显示“灵枢系统有新通知”。
- **最小权限**：联网、悬浮窗、前台服务、通知。不申请位置、通讯录、麦克风、存储。除快捷对话由本应用内部打开外，没有导出的组件。
- **灵枢侧**：手机入口（8443）只放行 `/v1/app/*`、登录登出、健康检查；只签发家庭 App 会话，拿不到门户会话、MCP 工具和管理接口。对话内容不写进灵枢的数据库与审计，审计只记长度和耗时。

## 轻量

- 原生 Kotlin + Jetpack Compose，无第三方库：网络用系统 `HttpsURLConnection`，JSON 用系统 `org.json`，图标自己画，悬浮球用系统 View 绘制（不在悬浮窗里跑 Compose）。
- 发布版开 R8 压缩与资源裁剪。
- 灵枢的响应最多读 1 MB。
- 后台：一个前台服务做长轮询（每次最多挂 25 秒）；连不上时退避到最长 5 分钟一次。

## 模块

```
app/                主程序：配对、登录、首页框架、“我的”；功能登记在 XiaojiaApp.kt 的 Features
core/               连接灵枢（固定公钥 TLS）、安全存储、登录状态、Feature 接口、主题与图标
feature-today/      今日：简报卡片与通知列表（标记已读）
feature-chat/       对话（主界面标签 + 悬浮球弹出的快捷对话窗）
feature-assistant/  悬浮小助理、语音播报、通知长轮询、小助理设置
```

加新功能（设备控制、R3 审批、语音对话）：新建 `feature-xxx` 模块，实现 `core` 的 `Feature` 接口，在 `settings.gradle.kts` 和 `Features.all` 各加一行。灵枢的 `/v1/app/me` 返回 `features` 列表，App 只显示服务端开放的功能。

## 构建

需要 Android Studio（自带 JDK）和 Android SDK 37。

```bash
./gradlew :app:assembleDebug      # 调试版：app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # 发布版：需要 keystore.properties（见下）
```

发布签名：密钥库放在 `keystore/`，口令写在 `keystore.properties`，两者都不进 Git：

```properties
storeFile=keystore/xiaojia-release.jks
storePassword=...
keyAlias=xiaojia
keyPassword=...
```

## 首次使用

1. 灵枢侧生成手机入口证书并重启：`docker compose run --rm -v ./data/tls:/app/data/tls hub xiaojia-hub gen-app-cert --host <迷你主机局域网 IP>`
2. 在运维门户「成员与账号」给家人建账号，家人用验证器 App 扫码。
3. 手机连家里 Wi-Fi，打开灵枢系统 App，填迷你主机 IP，核对指纹与门户总览页一致后配对，再登录。
4. 在「小助理」页按提示打开“显示在其他应用上层”和通知权限。
