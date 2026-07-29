# AI Live Overflow 安卓桌宠 · 手机直出 APK 版

这是基于 [Vael-KY/AI-Live-Overflow](https://github.com/Vael-KY/AI-Live-Overflow) 蓝图补全的完整 Android 工程，并接上了 **GitHub Actions 自动构建**。

## 你要的结果

每次推送到 `main`，GitHub 会自动编译一个 `app-debug.apk`，你可以直接在 **Actions → 最新一次构建 → Artifacts** 里下载。

仓库地址：<https://github.com/X1ESJ/AI-Live-Overflow-Android-APK>

## 功能

- 悬浮窗桌宠
- 单击 / 双击 / 长按 / 拖拽
- 前台 App 检测
- 截图检测
- 充电 / 低电量反应
- 通知栏碎碎念
- 可选 Supabase 同步

## 手机上怎么拿 APK

1. 打开仓库的 **Actions** 标签页
2. 点最新一次 **Build APK**
3. 等它跑完显示绿色对勾
4. 往下滑到 **Artifacts**
5. 下载 `app-debug-apk`
6. 解压后安装里面的 `app-debug.apk`

## 注意

首次安装后，需要手动授予：

- 悬浮窗权限
- 使用情况访问权限
- 通知权限
- 存储/图片权限

否则桌宠不能正常工作。

## 协议

原始思路来自上游项目，遵循其 CC BY-NC-SA 4.0 非商用共享协议。
