# 贡献指南 / Contributing

## 开发环境

- **Android Studio**（自带 JDK 17 + Android SDK + Gradle）。用 IDE 打开根目录，等待 Gradle sync。
- minSdk 26 / targetSdk 34 / compileSdk 34。

## 架构约定（务必遵守）

- 业务逻辑放纯 Kotlin 的 `:core:domain`，**不依赖任何 `android.*`**，能在 JVM 上跑单测。
- Android 平台脏活（截屏 / 悬浮球 / UsageStats）隔离在 `:core:capture`。
- 持久化与 LLM 厂商实现放 `:core:data`；UI 与 DI 装配放 `:app`，`:app` 只接线不写业务逻辑。
- 依赖单向向内：外层可依赖内层，内层绝不反向依赖外层。
- 详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 跑测试

domain 层是纯 Kotlin，可独立跑：

```bash
./gradlew :core:domain:test
```

新增业务逻辑请在 `:core:domain` 配套单测（参考 `CaptureAgentTest`）。

## 隐私红线

- 任何能拿到用户 API Key / 截图内容的改动都要过 review。Key 只走 `EncryptedSharedPreferences`，**绝不写日志**。
- 不引入任何向 Sift 自有服务器上报数据的代码。

## 提交

- 小步提交，信息清晰。
- PR 描述说明动机与影响范围。
