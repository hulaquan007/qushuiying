# 去水印 (Watermark Eraser) – Android 项目

用手指涂抹图片中要去除的区域(水印、杂物等),应用在本地用纯 Kotlin 修复算法填补,并保存到 `图片/WatermarkEraser`。

## 使用
1. 用 Android Studio(Koala 或更新)打开本目录,等待 Gradle 同步(首次会自动生成 Gradle Wrapper 并下载依赖)。
2. 连接手机或模拟器(Android 10 / API 29 及以上),点击 Run。
3. 选择图片 → 涂抹水印 → 去除 → 保存。

## 算法说明
`Inpainter.kt`:扩大遮罩 → 从边界向内逐层用邻近已知像素均值填充 → 多轮平滑。
- 适合:细小、半透明水印,背景较平滑或渐变。
- 不适合:大面积水印压在复杂纹理上(结果会模糊)。需要更好效果时,可换成 OpenCV `Photo.inpaint` 或 LaMa 等模型。

## 使用须知
请只处理你本人拥有或已获授权编辑的图片。去除他人作品上的版权水印可能违反法律或平台条款。
