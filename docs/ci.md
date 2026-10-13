# CI/CD 与仓库结构约定

> 本文是**仓库结构与发布流程的权威说明**。工作流 `guard.yml` 会校验本文的存在与内容，
> 改结构前先读这里。

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

现在工程在根，workflow 统一在根执行，`guard.yml` 会阻止 `fo/` 和嵌套 `.github/` 复现。

## 工作流一览

| 文件 | 触发 | 职责 |
|---|---|---|
| `fo-build-base.yml` | 被调用 | 共用构建：检出 → JDK → Gradle 缓存 → 编译 → 收集 jar |
| `ci.yml` | push/PR 到 main | 质量把关：编译 + 单元测试 + 测试报告留档 |
| `release.yml` | 推 tag（如 `6.32`） | 发版：校验版本 → 构建 → 发到 GitHub Releases |
| `guard.yml` | push/PR 到 main | 结构与二进制守门 |

版本号、JDK 版本、产物文件名**全部**从 `gradle/libs.versions.toml` 解析，
workflow 里不允许出现硬编码版本串（`guard.yml` 会检查）。升版本只改 toml 一处。

## 发版流程

```bash
# 1. 改 gradle/libs.versions.toml 的 mod-version（不带 V 前缀）
# 2. 提交 + 打 tag（tag 就是版本号，同样不带 V）
git add gradle/libs.versions.toml
git commit -m "chore: 版本号 6.32 -> 6.33"
git tag 6.33
git push origin main --tags
```

推 tag 后 `release.yml` 自动：校验 tag 与 `mod-version` 一致 → 跑测试 → 构建 →
发 GitHub Release（附件 `FO-<版本>-<MC版本>.jar`）。

tag 与 `mod-version` 不一致会**直接失败**，不会发出半成品 Release。

最后在 `releases/README.md` 补一行版本记录。

## 为什么 jar 不进仓库

构建产物是可重现的：任何人都能用 `git checkout <commit>` + `./gradlew build` 得到同一个 jar。
把 jar 存进 git 只会带来三个坏处：

1. **仓库体积暴涨**——本仓库历史上曾提交 110 个 `releases/*.jar`，共 **148.6 MiB**；
2. **历史无法清理**——`.gitignore` 只对未跟踪的文件生效，对已进入历史的 blob 无效，
   即使后来 `git rm` 也只是新增一个「删除它」的提交，`.git` 不会变小；
3. **版本与产物双份维护**——`releases/README.md` 里的版本号和 jar 文件名可能对不上。

所以：产物只存在于 GitHub Releases，仓库里只留版本→commit→说明的清单。

2026-10-13 已用 `git filter-repo` 把这 110 个 jar 从**全部历史提交**中剥离，
并对 `.git` 做了 gc。`guard.yml` 会阻止复发。

## 仓库里为什么仍然有二进制文件

只剩三类，性质与「构建产物」不同，**不许顺手删**：

| 文件 | 大小 | 为什么必须入库 |
|---|---|---|
| `gradle/wrapper/gradle-wrapper.jar` | 43 KB | Gradle Wrapper 的标准组件，删了 `./gradlew` 无法自举 |
| `libs/baritone-api-fabric-1.21.11-SNAPSHOT.jar` | 1.6 MB | Baritone 未发布到任何公开 Maven 仓库，只能文件依赖；且它是 SNAPSHOT，没有可锁定的版本号可写进 toml |
| `src/main/resources/natives/windows/cubiomes.dll`<br>`src/main/resources/natives/windows/CubiomesJNI.dll` | 1.1 MB | JNI 原生库。导出符号是 `Java_com_custom_addon_util_CubiomesJNI_*`，**与 `com.custom.addon.util` 包名硬绑定**——改包名会直接链接失败，换 Maven 坐标也无法保留这个符号约定 |

除此之外还有 58 个古城结构 `.nbt`（共 397 KB）——这是**游戏数据**而非二进制依赖，
是 `AncientResourceSource` 从 jar 内直读的核心资源，必须入库。

新增任何二进制前先回答：它是**构建产物**（→ 不入库，走 Releases）
还是**源码级依赖**（→ 入库，但要在本表登记原因，否则 `guard.yml` 会拦截）。
