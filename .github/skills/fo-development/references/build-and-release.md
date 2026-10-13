# 构建、CI 与发版

> 本文是**构建与发布流程的权威说明**。改结构前先读这里。

## 构建

```bash
./gradlew build      # 构建 + 测试（JUnit 5随 build 一起跑）
./gradlew test       # 只跑测试
./gradlew assemble   # 只编译打包，不触发 test（CI 用这个）
```

- JDK 21。版本号与 JDK 版本的**唯一权威来源**是 `gradle/libs.versions.toml`
  （`mod-version` / `minecraft` / `jdk`），`build.gradle.kts` 的 toolchain 与 CI 都从那里读，
  **不在别处写死版本串**。
- ⚠️ **不要在 `gradle.properties` 里写死 `org.gradle.java.home`**（2026-10 从仓库移除；
  原先写死了 Linux agent 的路径，会让 Windows / CI 构建失败）。本地用
  `-Dorg.gradle.java.home=<本地 JDK 路径>` 覆盖。
- Windows 上构建：见 [windows-build.md](windows-build.md)（含国内镜像、坑 1~6、一分钟速查）。
- 产物：`build/libs/FO-<mod版本>-<MC版本>.jar`
  （`build.gradle.kts` 的 `base { version = "$modVersion-$minecraftVersion" }` 自动拼）。
  当前 = `FO-6.32-1.21.11.jar`。
- 交付：最终 jar 必须通过 `present_files` 交给用户。

## 命名规范（硬性）

**本节是唯一权威来源**；任何窗口 / 任何 AI 接手本项目，一律按本节执行，不得沿用旧写法。

1. **版本号一律不带 `V` 前缀**。正确写法是 `6.32`，不是 `V6.32`。历史遗留的 `V6.25` / `FO-V6.25` 写法已废弃。
2. **构建产物名 = `FO-<mod版本>-<MC版本>.jar`**，例 `mod-version = 6.32` + `minecraft = 1.21.11`
   → **`FO-6.32-1.21.11.jar`**。两段都由 `build.gradle.kts` 自动拼，**不要在别处写死版本串**；
   升 MC 版本时只改 `minecraft` 一处，产物名自动跟随。
3. **Git tag = `<mod版本>`**（如 `6.32`），不再用 `V6.32` 或 `FO-V6.32`。
4. **GitHub Release 标题 = `FO <mod版本>-<MC版本>`**，**附件名必须与构建产物同名**。
5. 插件名统一大写 **FO**；mod 显示名（`fabric.mod.json` 的 `name`）保持 `FO`。
6. `fabric.mod.json` 的 `version` 用**纯 mod 版本**（如 `6.32`），**不带 MC 后缀**
   —— 带 `-1.21.11` 会被 Fabric 的 semver 解析成 6.32 的预发布版，影响依赖判定。由
   `build.gradle.kts` 的 `processResources` 注入。
7. mod id（`fabric.mod.json` 的 `id`）按 Fabric 硬性规范保持小写 `fo`；Java 包名 `com.fo.addon` 保持小写。

| 项目 | 旧（已废弃） | 新（唯一正确） |
| --- | --- | --- |
| mod-version | `V6.32` | `6.32` |
| 构建产物 | `FO-V6.32.jar` | `FO-6.32-1.21.11.jar` |
| Git tag | `V6.32` | `6.32` |
| Release 标题 | `FO V6.32` | `FO 6.32-1.21.11` |
| Release 附件 | `FO-V6.32.jar` | `FO-6.32-1.21.11.jar` |

## 发版流程

每个版本（`x.y`）交付时：

1. 改 `gradle/libs.versions.toml` 的 `mod-version`（不带 V 前缀）。
2. commit（信息含模块名 + 摘要）。
3. 打 tag `<mod版本>`（**不带 V**，指向该版本 commit）。
4. 建 GitHub 正式 Release：标题 `FO <mod版本>-<MC版本>`、说明写入该版本更新介绍、
   jar 作为附件（附件名 = 构建产物名）。
5. 在 `releases/README.md` 版本清单加一行（版本 | commit | 说明）。

```bash
git add gradle/libs.versions.toml
git commit -m "chore: 版本号 6.32 -> 6.33"
git tag 6.33
git push origin main --tags
# 确认 main 的 CI 全绿后：
./gradlew assemble
gh release create 6.33 build/libs/FO-6.33-1.21.11.jar \
  --title "FO 6.33-1.21.11" --notes "<更新说明>"
```

⚠️ **纯文档改动不要发版**：没有新 jar 就不升版本、不建 Release，否则会污染版本历史
（会出现两个内容完全相同的 jar）。

⚠️ **本机 `github.com` 的 git 传输常超时**：`git push` / `git clone` 直连会挂。
推送与发版走 `api.github.com` REST API（见 [windows-build.md](windows-build.md) 第 6 节，
现成脚本 `docs/scripts/gh-deliver.ps1`）；拉取一律浅克隆。

## CI/CD

仓库只有**一个**工作流 `.github/workflows/ci.yml`：

| 触发 | 行为 |
|---|---|
| push 到 main / PR 到 main / 手动 | 解析 JDK → 装 Temurin → `./gradlew assemble` → 打包为 Actions artifact（`FO-jar`，留存 30 天） |

产物可在 Actions 页面直接下载。**不在 CI 里跑单元测试**（`assemble` 不触发 `test`），
单测交付前本地 `./gradlew build` 自检。

**为什么不再拆成多个工作流**：拆成 `ci.yml` / `release.yml` / `fo-build-base.yml`
后，每个文件都要重复「检出 → JDK → Gradle 缓存」的样板，版本号解析也容易各自漂移。

**为什么 Gradle 工程在根目录**：2026-10-13 之前工程被放在 `fo/` 子目录，导致
①每个 workflow 都得写 `working-directory: fo`；②出现了 `fo/.github/workflows/` 里的两份 workflow
——**GitHub 只读根目录的 `.github/`，这两份文件从未执行过**，属于典型的「看着有 CI、其实没有」，
且那两份内容本身也是坏的（没有 `working-directory`、用了 Java 25、`actions/checkout@v7` 并不存在）。

JDK 与版本号**全部**从 `gradle/libs.versions.toml` 解析，workflow 里不出现硬编码版本串。

> 曾写过自动发版工作流（推 tag 触发 + 校验 tag 与 `mod-version` 一致），
> 因发版频率低（约每月一次）而删除。若将来发版变频繁，那段校验值得加回来。

## 为什么 jar 不进仓库

构建产物可重现：任何人都能 `git checkout <commit>` + `./gradlew build` 得到同一个 jar。
存进 git 只有三个坏处：

1. **仓库体积暴涨**——本仓库历史上曾提交 110 个 `releases/*.jar`，共 **148.6 MiB**；
2. **历史无法清理**——`.gitignore` 只对未跟踪文件生效，对已进入历史的 blob 无效，
   即使后来 `git rm` 也只是新增一个「删除它」的提交，`.git` 不会变小；
3. **版本与产物双份维护**——`releases/README.md` 里的版本号和 jar 文件名可能对不上。

所以：产物只存在于 GitHub Releases，仓库里只留「版本 → commit → 说明」清单。
2026-10-13 已用 `git filter-repo` 把这 110 个 jar 从**全部历史提交**中剥离并 gc（18 MB → 3.7 MB），
靠 `.gitignore` 的 `*.jar` 规则 + 白名单例外拦截复发。

历史 jar 已从 git 历史中剥离，**需旧版请去 Releases 下载**。

## 仓库里为什么仍然有二进制文件

只剩三类，性质与「构建产物」不同，**不许顺手删**：

| 文件 | 大小 | 为什么必须入库 |
|---|---|---|
| `gradle/wrapper/gradle-wrapper.jar` | 48 KB | Gradle Wrapper 标准组件，删了 `./gradlew` 无法自举 |
| `libs/baritone-1.21.11-2026.jar` | 4.8 MB | **Baritone 官方不提供 Maven 仓库**，只能文件依赖。必须用原版 `baritone`（mod id `baritone`）而非 Meteor 打包的 `baritone-meteor`——玩家装的是原版，两者运行时实现都不同 |
| `src/main/resources/natives/windows/cubiomes.dll`<br>`src/main/resources/natives/windows/CubiomesJNI.dll` | 1.1 MB | JNI 原生库。导出符号是 `Java_com_custom_addon_util_CubiomesJNI_*`，**与 `com.custom.addon.util` 包名硬绑定**——改包名会直接链接失败，换 Maven 坐标也无法保留这个符号约定 |

除此之外还有 58 个古城结构 `.nbt`（共 397 KB）——这是**游戏数据**而非二进制依赖，
是 `AncientResourceSource` 从 jar 内直读的核心资源，必须入库。

新增任何二进制前先回答：它是**构建产物**（→ 不入库，走 Releases）
还是**源码级依赖**（→ 入库，但要在本表登记原因）。

## License

FO 本体是 **GPL-3.0**（见 `LICENSE`）。

| 项目 | 许可证 |
|---|---|
| **FO**（`LICENSE`） | **GPL-3.0** |
| [RustElytraClient](https://github.com/c4r0d/RustElytraClient)（参考实现） | GPL-3.0 |
| seedfinding（Maven 依赖） | MIT |

FO 本身是 GPL-3.0，所以抄 GPL 代码进 FO 不存在「传染变 GPL」的问题——两者协议兼容。
参考别人的**行为与思路**没问题，但**不要逐行复制其代码**：即使协议兼容，逐行搬运也会带来
「这段代码的作者与版权声明是谁」的溯源麻烦。历次参考（V5.2 的鞘翅四点、6.25 的 Auto Steal）
都是按 FO 自己的结构重写的，保持这个习惯。

⚠️ 反过来要留意：**MIT / Apache 兼容 GPL（可以抄）**，但如果将来引入 **GPL-incompatible 的协议**
（如某些非商业协议、CC-BY-NC），整个 FO 会被迫从 GPLv3 降级或剥离，引用前先查清。