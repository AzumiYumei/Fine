# Fine 轻播

轻量 Android M3U8 播放与离线下载应用，支持 Android 6.0 及以上。

## 使用

1. 输入或粘贴完整的 HTTP / HTTPS HLS 链接，可填写视频名称。
2. 点击「播放」在线播放，播放器自带暂停、拖动进度及音轨/字幕控制。
3. 点击「下载」解析点播内容，选择设备支持的音视频轨道并后台下载。下载使用当前网络，包括移动数据。
4. 在「我的下载」查看进度、暂停、继续、重试或删除；完成后点「离线播放」。

支持没有 `.m3u8` 后缀的签名链接。链接中的查询参数原样保留。相同链接不会重复创建下载。

## 范围与行为

- Java + 原生 Android Views + Media3 ExoPlayer/HLS，没有 WebView、Compose、FFmpeg、账号或广告 SDK。
- 下载保存的是完整 HLS 离线资源（播放列表、选中轨道分片及必要密钥），并非单个 MP4；目前不提供导出到相册或共享文件。
- 离线播放禁止回源请求；下载文件存于应用私有目录，卸载或清除应用数据会移除。
- 同时执行一个下载，支持断点继续。暂停状态和任务列表持久化；重新打开应用后恢复未暂停的任务。
- 下载服务在后台显示进度通知；系统强制停止或重启后需要重新打开应用。厂商省电策略可能限制后台下载。
- 直播支持在线播放，不支持录制；不支持需要登录 Cookie、定制 Referer/请求头或 DRM 授权的视频。
- 视频编解码依赖设备解码器；网络错误、链接失效、磁盘空间不足会显示失败状态。
- 播放离开前台会释放解码器，返回后恢复位置；旋转屏幕保留播放器。系统杀死进程后不恢复播放位置。
- 为兼容 HTTP 视频源启用明文网络；不会申请广泛存储访问权限。

## 构建

固定工具版本：JDK 17、Gradle 8.7、Android Gradle Plugin 8.5.2、Android SDK 34、Media3 1.4.1。固定版本便于复现，不代表最新版。当前 targetSdk 为 34；如需上架，请单独升级目标 SDK 并验证平台行为。

使用 Android Studio 打开本目录，安装 SDK 34 / Build Tools 34.0.0，并选择 JDK 17。或设置 `JAVA_HOME`，在 `local.properties` 写入本机的 `sdk.dir` 后运行：

```powershell
./build.ps1
# 或标准 Gradle 命令
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

`build.ps1` 默认生成经过压缩、使用调试密钥签名的可安装体验包：`app/build/outputs/apk/preview/app-preview.apk`。这是测试版本，不用于商店正式发布。

标准 `assembleDebug` 命令生成调试安装包：`app/build/outputs/apk/debug/app-debug.apk`。

```powershell
./build.ps1 -Release
```

Release 开启 R8 代码压缩及资源裁剪，生成 `app/build/outputs/apk/release/app-release-unsigned.apk`。分发前需用自己的密钥签名；仓库不包含发布密钥。

## 验证

首版已通过压缩体验包构建、4 项链接单元测试和 Android Lint 检查。当前环境未连接安卓设备，实际在线播放、后台下载和飞行模式离线播放尚未做设备验收。

单元测试覆盖链接校验、签名参数保留、HTTP 兼容以及下载任务标识。Lint 检查 Android API 和资源使用。设备验收步骤：

1. 输入有效点播 HLS，确认画面、声音、暂停、拖动、横屏和切后台再返回。
2. 下载期间暂停/继续，断网/恢复网络，退出后重新打开检查进度保存。
3. 下载完成，开启飞行模式，播放完整视频并拖到末尾，确认不依赖网络。
4. 删除下载并确认列表和磁盘空间释放；重复添加同一链接不产生重复任务。
5. 测试直播、404、签名过期、不支持的格式及空间不足，确认错误可见。

实现参考：[Android Media3 下载与离线播放文档](https://developer.android.com/media/media3/exoplayer/downloading-media)。
