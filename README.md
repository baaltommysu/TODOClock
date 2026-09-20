# TODOClock

一款支持横竖屏自动旋转的黑色极简翻页时钟，结合天气和今日事项。

## 功能

- 杭州本地与南京天气并排显示
- 天气自动每 10 分钟刷新，并缓存上次成功数据
- 全屏翻页时钟，每分钟自动更新，保持屏幕常亮
- 今日事项的新增、完成、删除和清除已完成
- 每天自动清理已完成事项，保留未完成事项
- 横竖屏自动旋转：横屏左右分栏，竖屏上下堆叠

## 普通版与专业版

项目通过 Android product flavor 输出两个可并存安装的版本：

| 版本 | 包名 | 功能 |
| --- | --- | --- |
| 普通版 | `com.todoclock` | 最多同时保留 3 条未完成事项 |
| 专业版 | `com.todoclock.pro` | 无限事项 |

两版使用不同的应用包名和桌面名称，普通版可以保留并升级到专业版而不互相覆盖。

## 构建

使用 Android Studio 打开项目，或在项目根目录执行：

```bash
./gradlew assembleFreeDebug
./gradlew assembleProDebug
```

输出文件：

- `app/build/outputs/apk/free/debug/app-free-debug.apk`
- `app/build/outputs/apk/pro/debug/app-pro-debug.apk`

当前版本已适配华为平板并支持横竖屏自动旋转，最低支持 Android 6.0（API 23）。

## 发布前工作

- 使用正式签名证书构建 release 包
- 按应用市场要求补充隐私政策、应用图标和截图
- 为商业发行确认天气数据服务的授权与配额
