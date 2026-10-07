# 在 Windows 上构建 / 发版 FO

> 本文记录的是**在没有任何开发环境的 Windows 机器上（例如网吧）从零把 FO 构建出来并发布**的完整流程，
> 包括实际踩过的坑。目标是换一台机器也能照着做，不用重新摸索。
>
> 首次冷启动实测耗时 **29 分 26 秒**（要下 Gradle + Loom + Minecraft + Yarn + Meteor + seedfinding），
> 依赖缓存建好之后**每次约 6 秒**。

---

## 0. 前提：这台机器上没有什么

在一台干净的 Windows 上，以下**全都没有**，不要假设它们存在：

| 工具 | 状态 |
|---|---|
| `java` / `javac` | ❌ 不在 PATH |
| `git` | ❌ 未安装 |
| `gh` | ❌ 未安装 |
| `mvn` / `gradle` | ❌ 未安装 |
| `node` / `pnpm` | ❌ 不在 PATH |

所以本文的两条主线是：**用 Minecraft 启动器自带的 JDK 编译** + **用 GitHub REST API 代替 git 提交和发版**。

---

## 1. JDK：用 Minecraft 启动器自带的那个

Minecraft 启动器会把 JDK 解压到 `.minecraft\runtime\` 下。实测这台机器上是：

```
C:\Users\<用户名>\AppData\Roaming\.minecraft\runtime\java-runtime-delta
```

验证：

```powershell
& "$env:APPDATA\.minecraft\runtime\java-runtime-delta\bin\java.exe" -version
# openjdk version "21.0.7" 2025-04-15 LTS   ← 正好满足 MC 1.21.11 要求的 Java 21
```

> 其它可用的 runtime 名字可能是 `java-runtime-epsilon` / `java-runtime-gamma` 等，**先列目录再选**：
> ```powershell
> Get-ChildItem "$env:APPDATA\.minecraft\runtime" -Directory
> Get-ChildItem "$env:APPDATA\.minecraft\runtime" -Recurse -Filter javac.exe
> ```
> 必须选**带 `javac.exe` 的**那个（有的 runtime 只有 JRE，没有编译器）。

---

## 2. Gradle 9.2.0：必须走国内镜像

`fo/gradle/wrapper/gradle-wrapper.properties` 要求 **Gradle 9.2.0**：

```
distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.0-bin.zip
```

### ⚠️ 坑 1：`services.gradle.org` 在国内会 connection reset

实测直接下会失败：

```
curl: (56) Recv failure: Connection was reset
```

**改用腾讯镜像**（实测 135,534,361 字节，完整）：

```powershell
$ws = "C:\Users\<用户名>\Documents\deepseek-harness\default-workspace"
New-Item -ItemType Directory -Force "$ws\tools" | Out-Null

curl.exe -L --retry 3 --retry-delay 3 --max-time 600 `
  -o "$ws\tools\gradle-9.2.0-bin.zip" `
  "https://mirrors.cloud.tencent.com/gradle/gradle-9.2.0-bin.zip"

Expand-Archive "$ws\tools\gradle-9.2.0-bin.zip" -DestinationPath "$ws\tools" -Force
# 结果：$ws\tools\gradle-9.2.0\bin\gradle.bat
```

> 备用镜像：`https://mirror.nju.edu.cn/gradle/gradle-9.2.0-bin.zip`

**不要用 `gradlew.bat` 走 wrapper**：它还是会去 `services.gradle.org` 下。用解压出来的 `gradle.bat` 直接跑。

---

## 3. ⚠️ 坑 2：`fo/gradle.properties` 里写死了 Linux 路径

```properties
# fo/gradle.properties
org.gradle.java.home=/home/user/jdks/jdk-21.0.12.1+1     # ← 这是另一个 agent 的 Linux 环境
```

Windows 上不处理这一行会**直接构建失败**（找不到那个 Java home）。

**不要改这个文件**（会把另一个 Linux agent 的环境弄坏），用命令行覆盖：

```
-Dorg.gradle.java.home="C:\Users\<用户名>\AppData\Roaming\.minecraft\runtime\java-runtime-delta"
```

`build.gradle.kts` 里的 `java { toolchain { languageVersion = 21 } }` 会自动侦测到这个本地 JDK 21，
不需要 foojay 去联网下 JDK。

---

## 4. 构建

```powershell
$ws     = "C:\Users\<用户名>\Documents\deepseek-harness\default-workspace"
$jdk    = "C:\Users\<用户名>\AppData\Roaming\.minecraft\runtime\java-runtime-delta"
$gradle = "$ws\tools\gradle-9.2.0\bin\gradle.bat"

$env:JAVA_HOME        = $jdk
$env:GRADLE_USER_HOME = "$ws\.gradle-home"     # 依赖缓存放工作区，别塞进 C:\Users 默认位置

& $gradle -p "$ws\FO\fo" build `
    "-Dorg.gradle.java.home=$jdk" `
    --console=plain
```

产物：

```
fo\build\libs\FO-<mod版本>.jar      ← 版本号来自 fo/gradle/libs.versions.toml 的 mod-version
```

### ⚠️ 坑 3：Windows PowerShell 5.1 会把 stderr 当终止错误

如果脚本里写了 `$ErrorActionPreference = 'Stop'`，再 `2>&1 | Tee-Object`，
Gradle 往 stderr 写第一行就会**中断整个脚本**（而且退出码看起来像构建失败，其实构建没跑完）。

**用 `Start-Process` 重定向**，不要用管道：

```powershell
$p = Start-Process -FilePath $gradle -ArgumentList $args -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput "$ws\logs\build.log" -RedirectStandardError "$ws\logs\build.err.log"
Write-Host "exit = $($p.ExitCode)"
```

现成脚本见 [`scripts/build-fo.ps1`](scripts/build-fo.ps1)。

### ⚠️ 坑 4：中文 `.ps1` 必须存成 UTF-8 **带 BOM**

Windows PowerShell 5.1 读无 BOM 的 UTF-8 脚本会**按 ANSI(GBK) 解析**，中文变乱码后引号配不上，直接
`Unexpected token` / `The string is missing the terminator`。

```powershell
$t = [System.IO.File]::ReadAllText("a.ps1", [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText("a-bom.ps1", $t, (New-Object System.Text.UTF8Encoding($true)))
```

读中文文件同理，一律显式指定 UTF-8：

```powershell
[System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
Get-Content $path -Encoding UTF8
```

---

## 5. 测试

`build` 会连带跑 JUnit 5。看结果：

```powershell
Get-ChildItem "$ws\FO\fo\build\test-results\test" -Filter *.xml | ForEach-Object {
    [xml]$d = Get-Content $_.FullName -Encoding UTF8
    "{0,-46} tests={1} failures={2}" -f $d.testsuite.name, $d.testsuite.tests, $d.testsuite.failures
}
```

交付前必须全绿（`failures=0` 且 `errors=0`）。

---

## 6. 提交与发版：不用 git，走 GitHub REST API

这台机器没装 git，所以直接用 HTTP API 建 commit / tag / Release。
**产出的仍然是仓库里真实的 commit 和 tag**，`AGENTS.md` 的「每次改动建 commit」照样满足。

### ⚠️ 坑 5（最危险）：提交前必须比对远端，否则会覆盖别人的提交

本地工作区可能是**很久以前**的副本。实测遇到过：本地是 `V4.58` 时代的源码，而远端 `main` 已经到 `V4.62`，
直接提交会用旧文件盖掉别人新改的 5 个文件。

**提交前先按 git blob SHA-1 逐个比对**：

```powershell
function GitBlobSha([string]$path) {
    $b   = [System.IO.File]::ReadAllBytes($path)
    $hdr = [System.Text.Encoding]::ASCII.GetBytes("blob $($b.Length)`0")
    $sha = [System.Security.Cryptography.SHA1]::Create()
    $all = New-Object byte[] ($hdr.Length + $b.Length)
    [Array]::Copy($hdr, 0, $all, 0, $hdr.Length)
    [Array]::Copy($b,   0, $all, $hdr.Length, $b.Length)
    ([BitConverter]::ToString($sha.ComputeHash($all)) -replace '-', '').ToLower()
}
```

拿远端 tree（`GET /git/trees/<sha>?recursive=1`）里的 `sha` 和它比。
**只提交你确实改过/新增的文件**，其余交给 `base_tree` 原样继承。

> 顺带一提：`raw.githubusercontent.com` 偶尔会截断（`unexpected EOF`）或 reset，
> 失败时把下载替换成 `https://cdn.jsdelivr.net/gh/<owner>/<repo>@<ref>/<path>` 重试。

### ⚠️ 坑 6：中文 JSON body 必须转成 UTF-8 字节

PowerShell 5.1 的 `Invoke-RestMethod -Body "<string>"` 会按 Latin-1 发，中文全变问号：

```powershell
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
Invoke-RestMethod -Uri $url -Method POST -Headers $hdr -Body $bytes `
    -ContentType "application/json; charset=utf-8"
```

### 发版流程（对应 `AGENTS.md` 铁律 6）

```
① 建源码 commit
   GET    /repos/{o}/{r}/git/ref/heads/main                 → 基线 commit sha + tree sha
   POST   /repos/{o}/{r}/git/blobs                          → 每个文件一个 blob（base64）
   POST   /repos/{o}/{r}/git/trees    {base_tree, tree[]}   → 新 tree
   POST   /repos/{o}/{r}/git/commits  {message, tree, parents:[head]}
   PATCH  /repos/{o}/{r}/git/refs/heads/main {sha}          → 推进 main

② 归档 jar（文件名带①的短哈希）
   fo/build/libs/FO-<版本>.jar  →  releases/FO-<版本>-<短哈希>.jar
   同时在 releases/README.md 的版本清单表加一行

③ 建 Release（tag 由 GitHub 自动指向 target_commitish）
   POST   /repos/{o}/{r}/releases
          {tag_name:"FO-Vx.y", target_commitish:<②的commit>, name:"FO Vx.y",
           body:<更新介绍>, draft:false, prerelease:false}
   POST   https://uploads.github.com/repos/{o}/{r}/releases/{id}/assets?name=<jar名>
          --data-binary @<jar路径>       ← 用 curl.exe 传二进制
```

**版本号**改 `fo/gradle/libs.versions.toml` 的 `mod-version`，产物文件名自动跟随。

> **纯文档改动不要发版**：没有新 jar 就不升版本、不建 Release，否则会污染版本历史
> （会出现两个内容完全相同的 jar）。

现成脚本见 [`scripts/gh-deliver.ps1`](scripts/gh-deliver.ps1)。

### Token

需要一个能写 `fanjiale666666/FO` 的 token（细粒度给 **Contents: Read and write** 就够）。

- 别把它写进任何**要提交的文件**里；放到工作区下一个被忽略的临时文件（如 `.ghtoken`），**用完删掉**。
- 用完记得去 GitHub 设置里**吊销**。

---

## 7. 许可证：参考别的项目前先看 LICENSE

| 项目 | 许可证 |
|---|---|
| **FO**（`fo/LICENSE`） | **CC0-1.0** |
| IceHack（鞘翅套件来源，`com.fo.addon.elytra`） | MIT |
| [RustElytraClient](https://github.com/c4r0d/RustElytraClient)（参考实现） | **GPL-3.0** |

**GPL-3.0 有传染性**：把它的代码抄进 CC0 的 FO，会让整个 FO 变成 GPL 衍生作品。

> 参考它的**行为与思路**没问题（不受著作权保护），但**不要逐行复制它的代码**。
> V5.2 参考它的四点（走过去再自己挖 / 背包满腾位 / 槽位差检测 / 火球重置超时）都是按 FO 自己的结构重写的。

---

## 8. 前端汉化（`AGENTS.md` 铁律 3）

Meteor 的 `EnumSetting` 下拉框、模块列表、HUD、聊天输出**都走枚举的 `toString()`**。
所以会出现在界面上的枚举**必须覆写 `toString()` 返回中文**，否则玩家会看到 `BlocksPerSecond` 这种英文枚举名。

```java
public enum Unit {
    BlocksPerSecond("格/秒"),
    Kmh("公里/小时"),
    BlocksPerTick("格/刻");

    public final String label;

    Unit(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
```

已有守护测试 `ElytraUiTextTest` 会遍历所有界面枚举，**发现没有中文的枚举会直接让构建失败**。

同时注意：`.name()` 返回的是**枚举常量名（英文）**，界面上要用 `toString()`。

---

## 9. 一分钟速查

```powershell
$ws     = "$env:USERPROFILE\Documents\deepseek-harness\default-workspace"
$jdk    = "$env:APPDATA\.minecraft\runtime\java-runtime-delta"
$gradle = "$ws\tools\gradle-9.2.0\bin\gradle.bat"

# 1) JDK 在不在
& "$jdk\bin\javac.exe" -version

# 2) Gradle 在不在（不在就照第 2 节下）
Test-Path $gradle

# 3) 构建（约 6 秒，冷启动约 30 分钟）
$env:JAVA_HOME = $jdk; $env:GRADLE_USER_HOME = "$ws\.gradle-home"
& $gradle -p "$ws\FO\fo" build "-Dorg.gradle.java.home=$jdk" --console=plain

# 4) 产物
Get-ChildItem "$ws\FO\fo\build\libs\*.jar"
```
