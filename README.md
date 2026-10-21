# 开源搜索 · OpenSourceSearch

[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Target](https://img.shields.io/badge/targetSdk-34%20(Android%2014)-3DDC84?logo=android)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Material 3](https://img.shields.io/badge/Material%20You-3-6750A4?logo=material-design)](https://m3.material.io/)

一款基于 **GitHub 公共 API** 的 Android 开源仓库搜索客户端。打开软件即是搜索框，输入关键词即可在海量 GitHub 开源仓库中按**星标数量从高到低**发现好项目，一键跳转 **GitHub 客户端**查看；未安装客户端时自动使用**内置 WebView 浏览器**打开。

![应用图标](art/icon_512.png)

## 下载安装

> [**最新 Release（已签名 APK）**](https://github.com/donghuaigu123/open-source-search/releases/latest)
>
> 下载 `开源搜索-release.apk`（Release 资产名为 `OpenSourceSearch-release.apk`）传到手机，点击安装（需允许安装未知来源应用）。支持 Android 7.0 ~ 14。

## 功能特性

### 搜索
- **打开即搜**：首屏即搜索框，支持回车 / 点击搜索
- **星标排序**：调用 GitHub Search API，`sort=stars&order=desc`，展示结果**不超过 30 条**
- **结果卡片**：仓库全名、描述、所有者头像、主语言（带语言主题色）、星标 / Fork 数（k/M 格式化）、相对更新时间
- **跳转 GitHub 客户端**：点击卡片优先唤起 GitHub App（`com.github.android`）查看仓库；未安装时自动降级到内置浏览器
- **搜索历史**：自动保留最近 **15 条**记录（去重、最新在前），支持单条删除与一键清空
- **热门推荐**：内置常用技术关键词快捷搜索

### 内置浏览器（WebView）
- 多标签页管理（新建 / 切换 / 关闭 / 计数角标）
- 前进 / 后退 / 刷新（加载中变为停止）/ 主页
- 地址栏智能识别：网址直接访问，关键词跳转 GitHub 搜索
- 下拉刷新、加载进度条、标题同步
- **下载管理**：经系统 DownloadManager 下载并显示通知
- **页内查找**：关键词高亮、上/下一个匹配
- **桌面 / 移动 UA 切换**（持久化）
- 书签（添加 / 查看 / 清空）、复制链接、系统浏览器打开、分享
- `target=_blank` 新窗口自动放入新标签页；文件上传（`<input type=file>`）支持

### 主题与设计
- **日间 / 夜间 / 跟随系统**三种模式，工具栏一键切换 + 菜单选择
- **Material You**：Android 12+ 自动动态取色，低版本使用品牌靛紫配色
- Material 3 组件：MaterialToolbar、MaterialCardView、Chip、TextInputLayout、LinearProgressIndicator 等
- 透明状态栏 / 导航栏、沉浸式边缘、RTL 支持

### 应用图标
- **圆弧自适应图标（Adaptive Icon）**：紫蓝渐变圆角背景 + 白色 GitHub 章鱼猫图标 + 叠加放大镜，支持 Android 13+ 单色主题图标（Monochrome）
- 提供各密度传统 PNG 图标（圆形 / 圆角方形）

## 适配与权限

| 项目 | 说明 |
| --- | --- |
| targetSdk | 34（Android 14） |
| minSdk | 24（Android 7.0） |
| 编译 | JDK 17 / AGP 8.5.2 / Gradle 8.7 |
| 权限 | `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`POST_NOTIFICATIONS`（Android 13+ 运行时申请，用于下载通知） |
| 软件包可见性 | 声明 `<queries>` 以检测 GitHub 客户端是否安装（Android 11+） |

> **API 限额提示**：匿名调用 GitHub Search API 约 **10 次/分钟**，超限会提示稍后再试。

## 构建

### 环境要求
- JDK 17+
- Android SDK（Platform 34、Build-Tools 34.0.0）

### 命令行构建
```bash
git clone https://github.com/<your-name>/open-source-search.git
cd open-source-search

# 配置签名（可选，不配置则产出未签名包）
# 生成密钥：keytool -genkeypair -alias youralias -keyalg RSA -keysize 2048 -validity 10000 -keystore your.jks
cat > keystore.properties <<EOF
storeFile=/absolute/path/to/your.jks
storePassword=your_store_password
keyAlias=your_alias
keyPassword=your_key_password
EOF

./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/开源搜索-1.0.0-release.apk`（已签名）

## 技术栈

- 纯 Java，无 Kotlin
- AndroidX AppCompat 1.7 / Material Components 1.12（Material 3）
- RecyclerView + ViewBinding + Glide
- 系统 WebView + DownloadManager + ClipboardManager
- GitHub REST API v3（`/search/repositories`，`Accept: application/vnd.github+json`）

## 目录结构

```
app/src/main/java/com/opensourcesearch/
├── SearchApp.java          # Application：动态取色 + 主题恢复
├── MainActivity.java       # 首页：搜索框 / 历史 / 结果列表 / 主题菜单
├── BrowserActivity.java    # 内置浏览器
├── RepoAdapter.java        # 仓库卡片适配器
├── GitHubApi.java          # GitHub Search API 封装
├── Repo.java               # 仓库模型 + 数字/时间/语言色格式化
├── HistoryStore.java       # 搜索历史（≤15 条）
├── BookmarkStore.java      # 浏览器书签
├── TabManager.java         # 多标签管理
└── Prefs.java              # 主题 / UA 偏好
```

## 签名说明

发布用的 JKS 密钥库（`opensourcesearch.jks`，RSA 2048，有效期 10000 天）**不在本仓库中**，由构建者本地保管。仓库内 `keystore.properties` 已被 `.gitignore` 忽略，克隆后需按上文自行生成并配置。

## 免责声明

本项目为非官方 GitHub 客户端，数据来自 GitHub 公共 API，与 GitHub Inc. 无任何关联。使用时请遵守 GitHub API 使用条款。

## License

[MIT](LICENSE)
