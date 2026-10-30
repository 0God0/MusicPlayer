# TF Music

TF Music 是从 `TestMod` 音乐模块独立迁移出的 Forge 1.20.1 Mod。工程不依赖原项目的模块化机器、能源、流体或多方块系统。

## 环境

- Minecraft 1.20.1
- Forge 47.4.5
- JDK 17（64 位；编译和运行 Gradle 均使用此版本）
- Gradle 8.8（仓库自带 Wrapper，无需另装 Gradle）
- ForgeGradle 6.0.54（固定版本）
- Mod ID：`tf_music`

## 内容

- `tf_music:music_burner`：刻录直链或哔哩哔哩音频。
- `tf_music:music_disc`：保存来源、名称、时长、地址及分 P 数据。
- `tf_music:music_playlist`：保存最多 64 首已刻录歌曲，支持顺序、列表循环、单曲循环和随机播放。
- `tf_music:music_player`、`tf_music:portable_speaker`：共用播放器界面，支持放入／取出唱片或歌单、播放、暂停、停止、上一首、下一首、拖动进度、0.5x 至 2.0x 倍速及歌单编辑。
- 两种播放器均可切换“全局／空间”：全局模式让客户端已加载该播放器的所有玩家听到无距离衰减的立体声；空间模式保留 64 格半径的距离衰减，便携式持有者仍听到立体声。旧设备默认空间模式。

## 使用流程

1. 将空白网络音乐唱片放入音乐刻录机。
2. 选择“音乐直链”或“哔哩哔哩”，填写歌曲信息并点击“刻录”。
3. 手持歌单卡右键打开编辑器，将已刻录唱片放入来源槽并加入歌单；源唱片不会被消耗。
4. 右键网络音乐播放器或便携式音箱，在媒体槽放入唱片或歌单卡，点击播放。
5. 点击媒体槽下方的“空间／全局”按钮切换声音模式；放入歌单卡后可选择播放顺序或点击“编辑”实时修改歌单。
6. 在媒体槽取出媒体即可停止；拆除方块播放器会掉落其中的媒体。

直链支持 HTTP/HTTPS 的 MP3 和 M4A。哔哩哔哩来源使用 BV 号和 1 至 128 的分 P，并通过公开 API 获取音频流。音频缓存在客户端的 `tf_music/cache/audio`。

## 多人同步与持久化

- 服务端只同步歌曲元数据、播放进度、倍率和播放器位置；每个客户端独立下载、缓存和解码。
- 固定播放器在区块卸载或服务器重启后从保存进度恢复。
- 全局播放的范围取决于每个客户端实际加载的声源：方块播放器所在区块卸载，或便携式音箱所属玩家实体卸载时立即停止；再次加载后从服务端当前进度恢复。全局／空间设置保存在各自设备上，切换时保留播放进度、倍速和暂停状态。
- 便携式音箱定期保存进度，跨维度时会重建跟随声音，离开玩家背包时自动暂停。
- 歌单内容、修订号和播放模式保存于歌单卡；当前条目、进度和随机顺序保存于播放设备，编辑时当前正在播放的音频快照会播放至结束。
- 网络控制在服务端验证音箱 UUID 所有权，并约束拖动进度和播放倍率的取值范围。

## 兼容模式风险

本工程按迁移要求保留原项目的宽松联网行为：允许任意 HTTP/HTTPS 主机和端口，且不设置单文件大小及缓存总量上限。在不受信任的多人服务器中，这会带来客户端访问内网地址和磁盘占用失控的风险。部署前应确认服务器玩家可信，并在后续版本中通过配置启用地址和容量限制。

## 构建

首次接手项目时，先安装 JDK 17，在仓库根目录完成一次命令行构建，再导入 IDE：

```powershell
.\gradlew.bat clean test build
```

Linux / macOS：

```sh
bash ./gradlew clean test build
```

Gradle 运行时由命令行的 `JAVA_HOME` 或 IDEA 的 **Gradle JVM** 明确指定，`settings.gradle` 会检查其必须为 Java 17；Java Toolchain 则将编译、测试和开发运行任务固定为 Java 17。请将 `JAVA_HOME` 指向自己的 JDK 17 安装目录。项目不保存任何人的 JDK 绝对路径，也不使用会覆盖 IDE 选择的 Daemon JVM 自动发现配置，避免本机存在多个 JDK 17 时选中无法写入项目目录的安装。

首次构建需要联网访问 Gradle 分发站、Gradle Plugin Portal、Forge Maven、Maven Central 和 Mojang 下载站，下载并生成 Minecraft 映射依赖，可能需要数分钟。`*_mapped_official_*` 是 ForgeGradle 本地生成的依赖，无需另加第三方 Maven 镜像或复制别人的 `.gradle` 缓存。

项目固定 ForgeGradle、Forge、Minecraft 映射和 Gradle Wrapper 版本，并校验 Gradle 分发包 SHA-256。命令行构建默认禁用长期复用的守护进程和配置缓存，JVM 编码统一为 UTF-8；`.gitattributes` 固定 Wrapper 脚本的换行格式，避免跨平台检出后无法执行。GitHub Actions 会在 Windows / Linux 的 JDK 17 环境中从空 Gradle 缓存执行构建与测试。

### IntelliJ IDEA

1. 完成上面的首次命令行构建后，打开仓库根目录。
2. 在 Gradle 设置中选择使用项目的 **Gradle Wrapper**，将 **Gradle JVM** 设为 JDK 17；项目 SDK 同样选择 JDK 17。
3. 点击 **Reload All Gradle Projects**，等待依赖导入结束。IDE 编译运行委托给 Gradle。
4. 需要启动开发客户端时，运行 `runClient`，或先执行 `genIntellijRuns` 生成运行配置。

`.idea/` 是各人的本地配置，不随仓库分发；其中的 Maven Runner JRE 设置不控制 Gradle 使用的 JVM。

### IDEA 提示缓存锁文件“拒绝访问”时

若命令行可以构建，但 IDEA 提示 `.gradle/8.8/fileHashes/fileHashes.lock (拒绝访问。)`，先核对两者使用的 JDK **安装目录**。在 Windows 上，不同位置的 `java.exe` 可能有不同的文件访问结果，即使都属于 Java 17。

在 IDEA 的 **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM** 中，选择命令行已验证可用的 JDK 17，或指向它的 `JAVA_HOME`，然后重新加载 Gradle 项目。实际运行的 Java 路径可在 Gradle 守护进程日志的 `javaHome=` 中确认；仅确认项目 SDK 为 Java 17 不足以确定 Gradle 使用的是哪套安装。

### 映射依赖生成失败时

若日志先出现 `Project services has been closed`，随后提示找不到 `net.minecraft.client.main.Main` 或 `forge:..._mapped_official_...`，通常是 ForgeGradle 在 IDE 复用的守护进程中保留了上一次已关闭的项目服务。IDE 可能自行复用守护进程，即使项目设置了 `org.gradle.daemon=false`。

停止当前构建，在仓库根目录执行以下命令，再重新加载 IDE 的 Gradle 项目：

```powershell
.\gradlew.bat --stop
.\gradlew.bat --no-daemon clean test build
```

Linux / macOS 使用 `bash ./gradlew --stop` 和 `bash ./gradlew --no-daemon clean test build`。不要直接修改或删除 `build/tmp` 中生成的 `Start.java`；它的缺类报错是上游映射依赖生成失败的结果。若最早的错误是下载超时或无法连接，应先排查对应下载站的网络连接，再重试构建。

可发布文件为 `build/libs/tf_music-1.0.0.jar`。`tf_music-1.0.0-slim.jar` 不包含 LavaPlayer 及原生解码库，不能单独使用。

## 安装与发布

1. 客户端和服务端均安装 Minecraft 1.20.1、Forge 47.4.5 或兼容的 47.x 版本。
2. 将 `tf_music-1.0.0.jar` 放入客户端和服务端各自的 `mods` 目录。
3. 联机时所有参与者必须安装相同版本；无需另外安装 LavaPlayer。
4. 不要分发或安装 `tf_music-1.0.0-slim.jar`。

运行测试：

```powershell
.\gradlew.bat test
```

可选哔哩哔哩实时下载与解码测试：

```powershell
.\gradlew.bat test "-DtfMusicBiliTest=true" --rerun-tasks
```

可选真实音频解码测试：

```powershell
.\gradlew.bat test "-DtfMusicTestAudio=D:\path\to\test.mp3"
```
