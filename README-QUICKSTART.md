# AI Live Overflow 安卓桌宠 · 可直接编译版

这是基于 [Vael-KY/AI-Live-Overflow](https://github.com/Vael-KY/AI-Live-Overflow) 蓝图搭建好的完整 Android 工程，**开箱即用**，不需要你从头写。

## 怎么用

### 1. 打开工程

用 **Android Studio**（推荐 Jellyfish 或更新版本）：

```
File → Open → 选择解压后的文件夹
```

如果提示缺少 Gradle wrapper，点 **「Add wrapper to project」** 或 **「OK」** 让 Android Studio 自动下载。

### 2. 编译并运行

- 插手机 → 打开 USB 调试 → 点 Android Studio 的绿色运行按钮
- 或者菜单 `Build → Build Bundle(s) / APK(s) → Build APK(s)`

Debug APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

### 3. 安装后授权

首次打开 App，依次允许：

1. **悬浮窗权限**（跳到设置里手动开）
2. **使用情况访问权限**（用于检测你在哪个 App）
3. **通知权限**
4. **存储/图片权限**（用于检测截图）

全部允许后点 **「启动桌宠」**，屏幕角落就会出现一只蓝色小猫。

## 功能一览

| 动作 | 效果 |
|------|------|
| 单击桌宠 | 随机说一句话 |
| 双击桌宠 | 跳起来 |
| 长按桌宠 | 脸红害羞 |
| 拖拽桌宠 | 拖到任意位置 |
| 截图 | 自动说「茄子」类的话 |
| 切到某些 App（微信/B站/抖音等）| 触发专属吐槽 |
| 充电/低电量 | 有对应反应 |
| 通知栏 | 每小时换一句碎碎念 |

## 自定义你的桌宠

### 换形象

改 `app/src/main/assets/pet.html` 和 `pet.css` 里的 SVG/CSS。不会画可以让 AI 帮你生成 SVG，塞进去就行。

### 换台词

改 `app/src/main/assets/pet.js` 里的 `tapLines`、`doubleTapLines` 等数组。

### 接入你的 AI / Supabase

打开 `app/src/main/java/com/example/deskpet/service/OverlayService.kt`，找到：

```MainActivity.kt
private const val SUPABASE_URL = ""
private const val SUPABASE_KEY = ""
```

填上你的 Supabase URL 和 anon key，桌宠会把「手势、截图、App 切换」自动上报到 `gesture_log` 和 `app_usage` 表。然后你的 AI 读这些表，就能知道用户做了什么。

Supabase 表结构参考：

```schema.sql
create table gesture_log (
    id bigserial primary key,
    gesture_type text not null,
    x integer,
    y integer,
    created_at timestamptz default now()
);

create table app_usage (
    id bigserial primary key,
    package_name text not null,
    started_at timestamptz default now()
);
```

## 注意事项

- 华为/小米/OPPO 等国内 ROM 杀后台狠，需要在「电池优化」里把本 App 设为「不允许优化」，并允许「自启动」。
- 这是 Debug 版本，正式发布前需要自己签名。
- 本项目仅供个人学习使用，协议继承原项目 CC BY-NC-SA 4.0。

## 技术栈

- Kotlin + Android SDK 26+
- WebView 渲染 SVG/CSS/JS
- WindowManager 悬浮窗
- UsageStatsManager 前台 App 检测
- FileObserver 截图检测
- 可选 Supabase REST 同步

祝你玩得开心 🐾
