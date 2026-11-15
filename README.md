# Wujee's AI

本地运行的 Android AI 助手。自填 API，直连任意 **OpenAI 兼容** 服务（七牛云、千问兼容模式、DeepSeek、OpenAI、Ollama / LM Studio 等）。
文字、语音、视觉三种对话模式，带本地知识库，界面为紫蓝白浅色渐变。

## 功能

| 模块 | 说明 |
|---|---|
| 对话 | 流式输出、多轮上下文、图片输入、深度思考内容（reasoning_content）展示、历史会话本地保存 |
| 语音 | 系统 ASR 连续识别 → 自动发送 → 系统 TTS 自动朗读，含音量动效球 |
| 视觉 | CameraX 后置预览（可切前置），默认开启语音；说完一句自动抓帧连同语音文本发给多模态模型 |
| 知识库 | 上传 PDF / DOCX / PPTX / TXT，提取全文后分块存在本机，提问时自动检索并注入上下文；可随时删除 |
| 书目 | 输入 ISBN 查询公开书目信息（书名、作者、出版社、简介、分类）并存入知识库 |
| 设置 | 多供应商管理、API Key 掩码、连通性检测、模型同步、温度 / 上下文轮数 / 语速 |
| 交互 | 左侧边缘右滑返回上一页 |

## 构建

环境要求：JDK 17、Android SDK（compileSdk 34）、Gradle 8.7+。

### Android Studio（推荐）

1. `File → Open` 选择本目录
2. 等待 Gradle Sync（首次会自动下载依赖）
3. 连接手机或启动模拟器 → Run

### 命令行

```bash
# 若本机没有 wrapper 可执行文件，先生成
gradle wrapper --gradle-version 8.7

export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug

# 产物
# app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 仓库里只包含 `gradle-wrapper.properties`，没有 `gradle-wrapper.jar`（二进制）。用 Android Studio 打开，或执行上面的 `gradle wrapper` 生成。

### GitHub Actions 云端打包（不需要本地环境）

`.github/workflows/android.yml` 已配好，推到 GitHub 即自动编译：

```bash
git init -b main
git add .
git commit -m "init"
git remote add origin https://github.com/<用户名>/lingxi.git
git push -u origin main

# 打标签 → 自动出 Release + 可下载的 APK
git tag v1.0.0
git push origin v1.0.0
```

- 普通 push：在 Actions 页面的 Artifacts 里下载 `LingXi-apk`
- 推 `v*` 标签：生成 Release，手机浏览器直接打开 `https://github.com/<用户名>/lingxi/releases` 下载安装
- APK 用 debug 签名，个人设备可直接装（需允许「安装未知来源应用」）

## 首次使用

1. 打开 App → 右上角 ⚙ 设置
2. 点「添加供应商」，选预设或手填：
   - 七牛云：`https://api.qnaigc.com`
   - 千问兼容：`https://maas.qianwenaiapi.com/compatible-mode/v1`
   - DeepSeek：`https://api.deepseek.com`
3. 填 API Key → 点「检测」
4. 点「同步模型」→ 在模型列表里选一个
5. 返回对话页开始聊

## 技术栈

- Kotlin 1.9.24 + Jetpack Compose（Material 3）
- OkHttp 4.12 手写 SSE 解析（不依赖第三方 OpenAI SDK，避免版本兼容问题）
- CameraX 1.3.4
- 系统 `SpeechRecognizer` / `TextToSpeech`
- 存储：SharedPreferences（设置）+ `filesDir/chats/*.json`（会话）+ `filesDir/images/*.jpg`

## 目录结构

```
app/src/main/java/com/lingxi/ai/
├── MainActivity.kt          入口
├── MainViewModel.kt         全局状态：供应商、会话、发送与流式接收
├── data/Models.kt           数据模型 + 设置/会话持久化
├── net/ApiClient.kt         OpenAI 兼容客户端（/models、/chat/completions 流式）
├── audio/SpeechInput.kt     连续语音识别
├── audio/TtsPlayer.kt       语音合成（自动清洗 Markdown）
├── camera/CameraController.kt  CameraX 预览 + 抓帧
├── media/MediaUtil.kt       图片压缩 / 降采样 / base64
├── kb/DocumentParser.kt     PDF / DOCX / PPTX / TXT 文本提取
├── kb/KnowledgeStore.kt     分块、索引、bigram 检索
├── kb/IsbnLookup.kt         ISBN → 书目元数据
└── ui/                      界面（Chat / Voice / Video / Settings / Knowledge）
```

## 已知边界

- **语音识别走系统服务**：多数国行机需要安装「Google 语音服务」或厂商语音服务；无可用识别器时 App 会提示，不影响文字对话。
- **视觉模式是按轮抓帧**，不是逐帧视频流。想改成实时连续帧，改 `VideoScreen.kt` 里的触发逻辑为定时器 + `camera.snap()` 即可，但会显著增加 token 消耗。
- **API Key 明文存本机** SharedPreferences，仅适用于个人设备。
- 图片以 base64 内联发送，取决于所选模型是否支持 `image_url`（模型列表里带「图」标记的才保证支持）。
- **知识库只做本地检索（字符 bigram 相似度）**，没有 embedding 模型，长文档召回率不如向量检索，但零依赖、零联网。
- **ISBN 只查询公开书目元数据**（Google Books / Open Library），不提供、不抓取书籍正文。要基于某本书问答，请上传你自己拥有的电子版。
- 扫描版 PDF 没有文字层，提取会得到空内容，需要先 OCR 再导入。
- 应用开启了 `usesCleartextTraffic`，以便连接局域网内的 Ollama / LM Studio（http）。不需要可删掉该属性。

## 改配色

全部颜色在 `ui/theme/Theme.kt` 顶部：`Purple` / `Blue` / `Ink` / `InkSoft`。渐变在 `GradientBackground()` 里。
