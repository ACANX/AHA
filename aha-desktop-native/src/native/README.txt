AHA 桌面端 · 原生镜像（试验性产物）
版本：${aha.native.version}
生成方式：GraalVM native-image（参数见同目录 native-image-args.txt）

构建报告（不在本包内，另有独立发布包）
--------------------------------------
本次构建的报告由参数里的「emit build report」产出，真实文件名是
<可执行名>-build-report.html，裸文件 34~36 MB。它与「给人运行的二进制」
是两类东西，因此**不打进本包**，而是单独发布：

    AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<版本>.zip

报告里是「下一步该加什么参数」的依据：镜像体积、内嵌资源占用、可达性未决项；
该独立包内同时含原生镜像参数与可达性元数据，是与本包对照的一组材料。

这是什么
--------
把 aha-desktop（JVM 版）编译成的**平台原生可执行文件**：不需要本机安装 JDK 或 JavaFX，
解压后直接运行。代码与 JVM 版完全同源（同一份源码、同一批依赖）。

怎么运行
--------
Windows   双击 aha-desktop-native.exe
Linux     先给执行权限（chmod +x aha-desktop-native），再双击或在终端里 ./aha-desktop-native
macOS     双击 aha-desktop-native；首次可能要去「系统设置 → 隐私与安全性」里放行
          （未签名的可执行文件会被 Gatekeeper 拦住，这是 macOS 的正常行为）

配置与数据
----------
与 JVM 版完全一致，都放在 AHA_HOME（默认 ~/.aha）下：

    Model.yml     供应商与 API Key
    AHA.md        全局身份（系统提示词）
    Data/         会话与记忆（SQLite）
    Log/AHA.log   日志

也就是说：原生镜像与 JVM 版**共用同一份配置和会话数据**，
可以交替使用，不必来回导。

它的定位（请务必读一下）
------------------------
这是**试验性 / 探索性**产物，不是正式发行包：

  * 功能意图与 JVM 版一致，但原生镜像对反射、资源与原生库的约束更严格，
    可能出现 JVM 版没有的问题（启动即崩、图表或对话框异常、某个工具失效…）；
  * 遇到问题时，请先用 JVM 版（aha-desktop-<版本>-<平台>.zip）对照一次，
    以区分「原生镜像特有」与「功能本身」的问题；
  * 正式使用请选 JVM 版；原生镜像的用途是「下载下来点开就试」。

出了问题时怎么反馈
------------------
把这三样附上，定位会快很多：

  1. ~/.aha/Log/AHA.log（原生镜像与 JVM 版共用同一份日志）
  2. 命令行里运行时的控制台输出（stdout/stderr）
  3. 本目录的 native-image-args.txt（构建参数）与独立报告包
     AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<版本>.zip，以及上面的版本号

附：JDK 轴与对比实验（试验性）
----------------------------
同一个版本可能有两个编译产物，文件名里带 JDK 标记：

    ...-jdk25-...   基线：用 JDK 25 编的（默认分支）
    ...-jdk27-...   试验：用 JDK 27 编的（对比 Project Leyden 的 AOT、
                    原始类型预览与 GC 策略对启动速度 / 内存占用的影响）

两者代码完全相同，只有编译用 JDK 与 native-image 参数不同；参数差异可以对比
本目录里的 native-image-args.txt 与 native-image-args-jdk27.txt。
目前该分支**只出 Windows 版**，试验成功后再评估 Linux / macOS。
