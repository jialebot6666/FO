# CI/CD 与仓库结构约定

> 本文是**仓库结构与发布流程的权威说明**。改结构前先读这里。

## 目录结构

```
FO/                          ← 仓库根，Gradle 工程就在这里（不再有 fo/ 子目录）
├── build.gradle.kts
├── settings.gradle.kts       rootProject.name = "FO"
├── gradle/libs.versions.toml ← 版本号唯一权威来源
├── gradlew
├── libs/                     ← 源码级二进制依赖（见下文白名单）
├── src/main/java|resources
├── .github/workflows/        ← 唯一的 .github，嵌套 .github 是死文件
├── docs/
├── releases/README.md        ← 只放版本清单，不放 jar
└── AGENTS.md
```

### 为什么 Gradle 工程在根目录

2026-10-13 之前工程被放在 `fo/` 子目录，导致：

- 每个 workflow 都得写 `working-directory: fo`，配置分散、极易漏写；
- 出现了 `fo/.github/workflows/` 里的两份 workflow——**GitHub 只读根目录的 `.github/`，
  这两份文件从未执行过**，属于典型的「看着有 CI、其实没有」；
- 且那两份内容本身也是坏的：没有 `working-directory`（在仓库根跑 `./gradlew` 必然失败）、
  用了 Java 25（工程 toolchain 是 21）、`actions/checkout@v7` 这个版本并不存在。

现在工程在根，workflow 统一在根执行，不需要任何 `working-directory`。

## 工作流一览

仓库只有**一个**工作流 `.github/workflows/ci.yml`：

| 触发 | 行为 |
|---|---|
| push 到 main / PR 到 main / 手动 | 解析 JDK → 装 Temurin → `./gradlew assemble` → 打包为 Actions artifact（`FO-jar`，留存 30 天） |

产物可在 Actions 页面直接下载。**不在 CI 里跑单元测试**（`assemble` 不触发 `test`），
单测交付前本地 `./gradlew test` 自检。

**为什么不再拆成多个工作流**：拆成 `ci.yml` / `release.yml` / `fo-build-base.yml`
后，每个文件都要重复「检出 → JDK → Gradle 缓存」的样板，版本号解析也容易各自漂移。
合并成一个后，构建逻辑只有一份。

JDK 与版本号**全部**从 `gradle/libs.versions.toml` 解析，workflow 里不出现硬编码
版本串。升 JDK / 升版本都只改 toml 一处。

## 发版流程

发版走手动命令（当前**没有**自动发版工作流）：

```bash
# 1. 改 gradle/libs.versions.toml 的 mod-version（不带 V 前缀）
# 2. 提交 + 打 tag（tag 就是版本号，同样不带 V）
git add gradle/libs.versions.toml
git commit -m "chore: 版本号 6.32 -> 6.33"
git tag 6.33
git push origin main --tags
```

确认 main 的 CI 全绿后，再建 Release：

```bash
./gradlew assemble
gh release create 6.33 build/libs/FO-6.33-1.21.11.jar \
  --title "FO 6.33-1.21.11" --notes "<更新说明>"
```

最后在 `releases/README.md` 补一行版本记录。

> 曾写过自动发版工作流（推 tag 触发 + 校验 tag 与 `mod-version` 一致），
> 因发版频率低（约每月一次）、不想维护两套流程而删除。若将来发版变频繁，
> 那段「tag 与版本号不一致就失败」的校验值得加回来。

## 为什么 jar 不进仓库

构建产物是可重现的：任何人都能用 `git checkout <commit>` + `./gradlew build` 得到同一个 jar。
把 jar 存进 git 只会带来三个坏处：

1. **仓库体积暴涨**——本仓库历史上曾提交 110 个 `releases/*.jar`，共 **148.6 MiB**；
2. **历史无法清理**——`.gitignore` 只对未跟踪的文件生效，对已进入历史的 blob 无效，
   即使后来 `git rm` 也只是新增一个「删除它」的提交，`.git` 不会变小；
3. **版本与产物双份维护**——`releases/README.md` 里的版本号和 jar 文件名可能对不上。

所以：产物只存在于 GitHub Releases，仓库里只留版本→commit→说明的清单。

2026-10-13 已用 `git filter-repo` 把这 110 个 jar 从**全部历史提交**中剥离，
并对 `.git` 做了 gc（18 MB → 3.7 MB）。目前靠 `.gitignore` 的 `*.jar` 规则
与白名单例外拦截复发。

## 仓库里为什么仍然有二进制文件

只剩三类，性质与「构建产物」不同，**不许顺手删**：

| 文件 | 大小 | 为什么必须入库 |
|---|---|---|
| `gradle/wrapper/gradle-wrapper.jar` | 48 KB | Gradle Wrapper 的标准组件，删了 `./gradlew` 无法自举 |
| `libs/baritone-1.21.11-2026.jar` | 4.8 MB | **Baritone 官方不提供 Maven 仓库**，只能文件依赖。必须用原版 `baritone`（mod id `baritone`）而非 Meteor 打包的 `baritone-meteor`——玩家装的是原版，两者运行时实现都不同 |
| `src/main/resources/natives/windows/cubiomes.dll`<br>`src/main/resources/natives/windows/CubiomesJNI.dll` | 1.1 MB | JNI 原生库。导出符号是 `Java_com_custom_addon_util_CubiomesJNI_*`，**与 `com.custom.addon.util` 包名硬绑定**——改包名会直接链接失败，换 Maven 坐标也无法保留这个符号约定 |

除此之外还有 58 个古城结构 `.nbt`（共 397 KB）——这是**游戏数据**而非二进制依赖，
是 `AncientResourceSource` 从 jar 内直读的核心资源，必须入库。

新增任何二进制前先回答：它是**构建产物**（→ 不入库，走 Releases）
还是**源码级依赖**（→ 入库，但要在本表登记原因）。
