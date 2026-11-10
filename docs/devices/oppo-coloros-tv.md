# OPPO K9 65 寸（ColorOS TV 2.4，`OPPOCNM652`）

一台 65 寸 OPPO K9：ColorOS TV 2.4（基于 Android 9），MediaTek m7642，2 GB 内存，只能装
32 位应用（`PPR2.180905.006.A1`，固件 2023-01-12）。下面写的都是这台机器上实测的结果，
不是从别的 OPPO 机型推断的。

- 原桌面：`com.heytap.tv.launcher`（这台已禁用，系统的兜底是 FallbackHome）。
- 现在用的桌面：Aerial 的 `standard` 版，改成 `com.oneplus.tv.launcher2` 后安装。
- 实测版本：**1.47 起**（1.48 是 versionCode 56）。

## 先打开 ADB

1. 设置 → 系统 → 关于本机 → **设备型号**，在遥控器上按 上、上、右、右、确定。
   页面会提示「还需执行 N 步」，按对了才有开发者选项。
2. 设置 → 系统 → **开发者选项** → 打开「USB 调试」。
3. 电脑上执行 `adb connect <电视IP>:5555`。

- 不用配对码，也没有「无线调试」（Android 9 没有）；开一次就一直是开的。
- **待机（熄屏）时电视会断网，ADB 跟着断**，唤醒后重新 connect 一次。
  如果重启后 `adb connect` 报 `No route to host`，但网络是通的，先 `adb kill-server` 再连。

## 为什么不能直接装

OPPO 只允许三个固定包名的应用声明「我是桌面」。别的包名只要带了 HOME，安装就会被拒绝：

```
INSTALL_FAILED_DUPLICATE_PERMISSION: App installation is banned, because of launcher categories
```

这句报错既不是权限冲突，也不是签名问题。能用的包名只有
`com.heytap.tv.launcher`、`com.oneplus.tv.launcher2`、`com.oppo.tv.launcher2`。

所以要把 Aerial 的 `standard` 版改成其中一个包名再签名安装。`guard` 版不声明桌面，
可以用原包名直接装（实测），但它接管桌面的效果没在这台机器上验证过。

### 改包名时，另外两处内部名字也要改

用源码编译时，改 `applicationId` 就够了；如果是用 apktool 拆开改 `AndroidManifest.xml`，
下面两处要手动一起改，否则会出问题：

1. **广播权限** `<新包名>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。
   不改的话，一打开就闪退，报 `Permission … is required`。
2. **ContentProvider** `app.orchard.tv.androidx-startup`（AndroidX 启动组件）。
   不改的话，官方包和 `guard` 版会装不进来，报
   `INSTALL_FAILED_CONFLICTING_PROVIDER … is already used by …`。

这两处都是打包时按 `applicationId` 生成的，所以源码改包名不用管。

## 不要停用原桌面（#862）

另一台 K9（`OPPOCNM632`，ColorOS TV 2.4 / Android 9，m7642，2026-10-29 报告人实测）**原桌面 `com.heytap.tv.launcher` 保持启用**也能开机直进 Aerial：改包名、在 HOME 里加下面的 `MODERN_HOME` 标记，然后
`cmd package set-home-activity com.oneplus.tv.launcher2/app.orchard.tv.MainActivity`，`resolve-activity` 显示 `isDefault=true`。

- 停用原桌面后，如果第三方桌面没被系统认可，系统只剩 FallbackHome，会一直停在开机动画（系统其实已经起来了，`adb` 还能连）。所以先 `cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME -c com.heytap.tv.launcher.category.MODERN_HOME` 确认 Aerial 在里面，再设默认桌面；原桌面一直留着当兜底。
- 想退回原桌面：`adb shell cmd package set-home-activity com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher`（`...modern.ModernLauncher` 不能当桌面）。
- 用 apktool 改包名时漏了下面说的广播权限，守护每 30 秒崩一次，默认桌面过 40–50 秒被系统清掉、弹出「选择桌面」，表现是电视卡、遥控器没反应。

## 开机要认的桌面标记

OPPO 开机时只认带 `com.heytap.tv.launcher.category.MODERN_HOME` 的桌面。没这个标记的
第三方桌面，系统会一直等「该起来的桌面」，电视卡在开机动画里，不会自己进桌面。

把它加进 HOME 的 intent-filter 就好。加好之后，1.47 起实测：进程启动到第一帧 0.7–0.8 秒，
第一帧和开机动画结束几乎同时（相差 0.06–0.4 秒）；开机约 16 秒出第一帧，约 19 秒桌面可用。

（这是正常冷启动的数。刚升级完的第一次会慢一些，系统要重新编译、重画磁贴。）

## 装好之后

需要授权这几项，否则守护、开机拉起和亮度都不工作：

```bash
adb shell pm grant com.oneplus.tv.launcher2 android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.oneplus.tv.launcher2 SYSTEM_ALERT_WINDOW allow
adb shell appops set com.oneplus.tv.launcher2 WRITE_SETTINGS allow
adb shell cmd notification allow_listener com.oneplus.tv.launcher2/app.orchard.tv.guard.RevivalListener
adb shell cmd package set-home-activity com.oneplus.tv.launcher2/app.orchard.tv.MainActivity
```

无障碍里的「Aerial 桌面守护」由 Aerial 自己打开（依赖上面的 `WRITE_SECURE_SETTINGS`）。
如果没开，去 设置 → 辅助功能 里手动打开。

确认装对了：

```bash
adb shell dumpsys package com.oneplus.tv.launcher2 | grep -E 'versionName|lastUpdateTime'
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

第二行应该返回 `com.oneplus.tv.launcher2/app.orchard.tv.MainActivity`。

## 开机时的两个老问题（1.47 起已修）

### 解锁前崩溃，HOME 被重启两次

1.46 及以前，Home Guard 在用户解锁前就绑定了，它读设置时碰到还不能读的凭据加密存储，
整个进程崩掉。Android 把 HOME 重启，再崩一次，第三次才起来。表现是开机时黑屏闪两下。

上游在 #481 里修了：解锁前不读这部分设置，等解锁后再补上。1.47 起冷启动都是
0 次崩溃、Aerial 只启动一次。

### 解锁时黑屏 2–3 秒

以前 MainActivity 在解锁时会 `recreate()`：旧窗口销毁，新窗口 1.5 秒后才建出来，
中间那 2–3 秒没有任何画面。上游在 #469 里改成在同一个窗口里继续启动。1.47 起冷启动都没有黑帧，肉眼也看不到闪；
解锁前后是同一扇窗口。

## 实测结果

除注明外都是 1.47 起在这台上实测的。

| 项目 | 结果 |
|---|---|
| 安装 | 成功；同签名覆盖安装，壁纸、照片、图标排序都保留 |
| 授权 | `WRITE_SECURE_SETTINGS`、通知监听、无障碍、悬浮窗都正常 |
| 开机 | 自动进 Aerial，不卡开机动画；1.47 起冷启动 0 崩溃、1 次进程启动、无黑屏 |
| 待机唤醒 | 回到 Aerial；熄屏几分钟后的长待机没测（待机断网，ADB 跟不上） |
| 按 HOME | 直接回 Aerial；原桌面已禁用，不会先闪原桌面 |
| 被强制停止后 | 约 0.5 秒被通知监听拉回来，8 秒回到前台，15 秒左右守护重新连上（1.11 前后实测） |
| 航拍屏保 | 正常；Apple 的影片走国内 CDN，换片不黑屏 |
| 天气 | 正常 |
| 手机配置页 | 正常打开（扫码） |

## 升级

和第一次安装一样，换成覆盖安装：

```bash
adb install -r -g Aerial-<版本>-oppo.apk
```

升级要保留原来的签名，否则装不上；壁纸、照片和排序会保留。装完按「装好之后」里的命令
确认一次。1.46 及以前的版本在开机时有上面两个问题，升到 1.47 及以后就不需要再打补丁。

## OPPOCNM632 / m7332：本机安装记录（2026-10-31）

另一台 `OPPOCNM632` 实读 `ro.board.platform=m7332`（不要套用前面报告人的 m7642），
Android 9 / SDK 28，固件 `PPR2.180905.006.A1 / 2301131057`，系统报告内存 1787 MB，
`ro.config.low_ram=true`，仅 `armeabi-v7a,armeabi`，Mali-G52；系统界面 1920×1080 / 60 Hz。

- 从 1.74（versionCode 94）源码构建 `oppo` flavor：`applicationId=com.oneplus.tv.launcher2`，
  启动类仍是 `app.orchard.tv.MainActivity`，HOME 过滤器带 `MODERN_HOME`。
  `assembleOppoRelease` 和 release 的 lintVital 通过；本机用现有 JBR 21 构建。
- 源码改 `applicationId` 后，广播权限、文件 Provider 和 AndroidX Startup 的 authority 均随包名生成，
  在 APK 中核对过。安装输出 `Success`，OPPO 的 HOME + MODERN_HOME 查询列出 Aerial。
- 原桌面保持启用。授予用户明确同意的 `WRITE_SECURE_SETTINGS`、悬浮窗、写设置、用量统计后，
  守护自动追加并绑定；`set-home-activity` 成功，HOME 解析到
  `com.oneplus.tv.launcher2/app.orchard.tv.MainActivity`。从系统设置按 HOME 回到 Aerial，截图和窗口焦点确认。
- 启动后执行 `cmd package compile -m speed -f com.oneplus.tv.launcher2`，结果 `Success`；
  系统 dexopt 状态为 `arm: [status=speed]`。效果「自动」识别为低端设备并开启流畅模式。
- 调用了通知监听授权，但诊断仍为 `allowed=false`，没有绑定监听（Android 9 低内存固件的限制）；
  不要反复写入监听列表。守护正常运行，长待机后的同步复活还没有实测。
- 没有主动修改系统屏保、亮度、ADB 常开选项，也没有停用或卸载任何系统应用。

安装前先在 **local/** 记录默认桌面和各项原值，并保留同一签名密钥；这次是本地签名的定制包，
不能用不同签名或官方包直接覆盖。实际测试过回滚：撤销 Aerial 的安全设置授权、通知监听和守护，
恢复原默认桌面 `com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher`，原桌面正常显示；
再重新授权和设置 Aerial 为默认，仍正常。专用回滚脚本保留 Aerial 及其数据，并只移除本包的服务，
不覆盖其他无障碍或通知服务。普通 `tools/restore.sh` 的包名固定为 `app.orchard.tv`，不能直接用于此定制包。

经用户同意重启一次，ADB 恢复后读到 `sys.boot_completed=1`，前台窗口和默认 HOME 均为 Aerial，
守护也仍在无障碍列表且实际绑定。ADB 大约 105 秒后才重连成功，这是连网和 ADB 的观测时间，不能当成桌面首帧耗时。
长待机、HDMI 实际切换和航拍播放尚未验证。

## OPPOCNM632：原桌面误开工厂测试入口（2026-10-31）

本机出现启动时的 `Android init Success` 提示和左上角黄色底蓝色 `K`。
窗口归属为 `com.konka.factory.at` 和 `com.cvte.factory.service`，Aerial 仍是默认桌面。
`com.cvte.factory.at/.job.StartActivity` 声明了 `android.intent.category.CVTE_AT_HOME`，
被守护的「以 `_HOME` 结尾就是厂商桌面」规则误收，应用列表于是多出一个同名「原桌面」。
这是工厂测试入口，会开启持久工厂模式，不是 OPPO 的桌面。

`HomeGuardService.isVendorHomeCategory` 现在排除 `CVTE_AT_HOME`；安装 release 修正版并
speed 编译后，真机守护候选只剩 `com.heytap.tv.launcher` 的两个正常桌面 Activity。
现有工厂状态通过本机固件的 `IFacApiCommon.unInitATWorkEnvFunction()` 退出，返回成功，
`persist.sys.konka_mode`、`persist.sys.cus.mode`、`debug.cvte.cusat` 均从 `1` 变成 `0`。
结束遗留调试进程后，截图确认黄色 K 消失、Aerial 在前台。一次性修复入口已从源码移除，
电视换回不带该入口的修正版；没有卸载、停用系统应用或清除数据。
从网格的「系统应用」点击唯一的「原桌面」，实际打开 OPPO `HomeLauncher`，按 HOME 回到 Aerial，
三个标记仍为 `0`。重启一次后没有按键，系统已完成启动，默认 HOME 和前台均为 Aerial，
两个持久标记仍为 `0`，临时 `debug.cvte.cusat` 已不存在；没有工厂悬浮窗口或 KonkaService，
守护已绑定。启动日志只有工厂服务的正常 BootCompletedReceiver 进程，没有启动测试 MainService。
截图确认重启后黄色 K 未复现；ADB 接通前的启动画面没有录像。
修复包、退出前状态和实测证据保存在本机 `local/`，不要提交。

## OPPOCNM632：开机网络图标偶尔显示离线（2026-11-01）

户主反馈 Aerial 开机偶尔显示离线，打开系统网络设置却已经连接，返回 Aerial 就恢复。
本次读取的系统日志中，Wi-Fi 在开机时自动选中已保存网络，关联、认证和 DHCP 均成功；
没有记录到真实的自动连接失败，也没有通过修改 Wi-Fi 开关或休眠策略来修复。

发现一处能造成这种现象的竞态：`HomeViewModel.refreshSystem()` 先读取网络状态，
再等信号源、默认桌面和亮度探测结束才写回。如果这时 Wi-Fi 连接回调已经把界面更新成已连接，
较早的离线结果会覆盖它；之后没有新网络事件，直到返回首页重新读取才恢复。
修正为网络单独在主线程读取并立即更新，和网络回调串行，慢探测不再写网络状态；
诊断日志只记录网络类型和 VPN 状态变化，不写网络名称。

- 回归测试阻塞真实 `HomeViewModel` 调用的电视信号源服务，分别在等待期间连接、断开 Wi-Fi。
  旧代码两项都错误地恢复成旧状态，修正版两项通过；加上原来的 18 项网络检测，共 20 项通过。
- 同签名 `1.74-oppo / 94` release 覆盖安装，`cmd package compile -m speed -f` 返回成功，
  系统 dexopt 确认 `speed`。APK 构建通过；完整 `lintOppoRelease` 报 24 项已有错误，
  涉及 API 兼容、RestrictedApi、Media3 注解、翻译及原 OPPO 清单等，没有本次新增的 lint 错误。
- 修正后两次真机重启，系统均自动联网，连接事件耗时分别 849 ms、671 ms，
  Aerial 日志均记录 `Offline -> Wifi`。第一次没有进入设置，首页截图的 Wi-Fi 图标正确，守护和默认 HOME 正常。
  第二次网络状态日志在户主进入设置之前已经正确；截图时户主正在网络设置中，不能算独立的首页显示验收。
  本次没有自然复现旧版的错误图标，竞态的前后对照来自回归测试；长待机未测。

现场日志、截图、测试前后结果及修正版 APK 保存在本机 `local/oppo-wifi-2026-11-01/`，不要提交。

## OPPOCNM632：同步 1.76 与只读升级提示（2026-11-01）

- 本机仓库快进到官方 main `9129859`：正式版号 `1.76 / 97`，包含 1.76 发布后的
  航拍玻璃、编译状态复查和小米守护修正。保留 `oppo` flavor、`MODERN_HOME`、原签名，
  以及前述网络状态竞态修复。工厂入口过滤采用上游 #1028 实现，排除所有 `_AT_HOME`。
- 定制包不能用官方 APK 升级。控制中心继续检查新版，有新版时显示版本和
  「定制版本，请手动更新」，这一行没有 `Pressable`、点击处理或遥控器焦点，
  不再打开官方升级页及其通用安装指引。以 `Updates.canInstall(context)` 判断，非按品牌硬编码。
  英文、中文、俄语、乌克兰语、哈萨克语均已加入提示；官方包保持原升级行为。
- 163 项单元测试通过；`assembleOppoRelease` 及其 NewApi fatal 检查通过。
  真机测试包临时显示更新提示：从个性化继续向下不会选中更新提示，直接点击文字仍停在控制中心。
  测完已去掉临时触发条件，最终包只在真实发现新版时显示提示。
- 最终同签名 `1.76-oppo / 97` 覆盖安装，speed 编译成功，实际 APK 的 SHA-256 与本机最终包一致。
  默认 HOME、守护绑定、Wi-Fi 均正常，两个持久工厂标记仍为 `0`；未改网络、亮度或户主内容。
  没有对这次同步后的全部上游功能做真机回归，也没有再次测试重启或长待机。

同步前补丁和未跟踪文件备份、测试截图、日志、最终 APK 在本机 `local/oppo-update-2026-11-01/`。

## 已知问题

- **升级一版后，桌面守护偶尔不会自己回来。** 按 HOME 或开机时不再自动拉起 Aerial，
  手动打开一次就恢复。最简单的修法是重启电视；不想重启就执行
  `adb shell am force-stop com.oneplus.tv.launcher2`，通知监听会把它拉回来，守护也会重新连上。
- 装第二个「桌面」应用会清掉默认桌面的选择。装完把默认桌面指回 Aerial：
  `adb shell cmd package set-home-activity com.oneplus.tv.launcher2/app.orchard.tv.MainActivity`。
  这台机器的设置里也有「设置主屏幕」一项，可以当开关用。

## 「原桌面」误开工厂模式（#1028，OPPOCNM632，m7332）

CVTE 代工固件里的工厂测试 `com.cvte.factory.at/.job.StartActivity` 声明了 `android.intent.category.CVTE_AT_HOME`，守护按「以 `_HOME` 结尾的类别是厂商桌面」把它算进原桌面，点「原桌面」磁贴就进了工厂模式（左上角黄底 K，`persist.sys.konka_mode` 等置 1，报告人用固件的退出接口关掉）。1.75 之后排除 `_AT_HOME`。报告人用的是改了包名的构建，没在官方包上复现。
