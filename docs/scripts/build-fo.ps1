<#
  在 Windows 上构建 FO（不需要装 Java / Gradle / git）。

  用法：
      powershell -NoProfile -ExecutionPolicy Bypass -File docs\scripts\build-fo.ps1
      powershell ... -File docs\scripts\build-fo.ps1 -Task compileJava

  可用环境变量覆盖默认路径：
      FO_WS      工作区根目录（默认 $env:USERPROFILE\Documents\deepseek-harness\default-workspace）
      FO_JDK     JDK 21 目录（默认 Minecraft 启动器自带的 java-runtime-delta）
      FO_GRADLE  gradle.bat 路径（默认 <工作区>\tools\gradle-9.2.0\bin\gradle.bat）

  背景与坑详见 docs/windows-build.md。
#>
param(
    [string]$Task = 'build',
    [switch]$NoDaemon
)

$ws = if ($env:FO_WS) { $env:FO_WS }
      else { Join-Path $env:USERPROFILE 'Documents\deepseek-harness\default-workspace' }

$jdk = if ($env:FO_JDK) { $env:FO_JDK }
       else { Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta' }

$gradle = if ($env:FO_GRADLE) { $env:FO_GRADLE }
          else { Join-Path $ws 'tools\gradle-9.2.0\bin\gradle.bat' }

$proj = Join-Path $ws 'FO\fo'

if (-not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
    throw "JDK not found (need one WITH javac.exe): $jdk`nSet FO_JDK or see docs/windows-build.md section 1."
}
if (-not (Test-Path $gradle)) {
    throw "Gradle not found: $gradle`nSet FO_GRADLE or see docs/windows-build.md section 2."
}
if (-not (Test-Path (Join-Path $proj 'build.gradle.kts'))) {
    throw "FO project not found at: $proj`nSet FO_WS."
}

$env:JAVA_HOME = $jdk
# 依赖缓存放工作区里，避免污染用户目录，也方便整个目录一起搬走
$env:GRADLE_USER_HOME = Join-Path $ws '.gradle-home'

New-Item -ItemType Directory -Force (Join-Path $ws 'logs') | Out-Null

# 注意：fo/gradle.properties 里写死了 Linux 的 org.gradle.java.home，这里用命令行覆盖，不改那个文件
$gradleArgs = @(
    '-p', $proj,
    $Task,
    "-Dorg.gradle.java.home=$jdk",
    '--console=plain'
)
if ($NoDaemon) { $gradleArgs += '--no-daemon' }

$log    = Join-Path $ws "logs\fo-$Task.log"
$errLog = Join-Path $ws "logs\fo-$Task.err.log"
Remove-Item $log, $errLog -ErrorAction SilentlyContinue

Write-Host "JDK    : $jdk"
Write-Host "Gradle : $gradle"
Write-Host "Task   : $Task"
Write-Host ""

# 关键：用 Start-Process 重定向。
# 用 `2>&1 | Tee-Object` 会让 Windows PowerShell 5.1 把 Gradle 的 stderr 当成终止错误直接中断脚本。
$p = Start-Process -FilePath $gradle -ArgumentList $gradleArgs -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $log -RedirectStandardError $errLog

Write-Host "EXIT = $($p.ExitCode)"
Write-Host "---- stdout tail ----"
if (Test-Path $log) { Get-Content $log -Tail 40 }
Write-Host "---- stderr tail ----"
if (Test-Path $errLog) { Get-Content $errLog -Tail 40 }

if ($p.ExitCode -eq 0) {
    Get-ChildItem (Join-Path $proj 'build\libs') -Filter *.jar -ErrorAction SilentlyContinue |
        ForEach-Object { "  ARTIFACT: $($_.Name)  $($_.Length) bytes" }
}
exit $p.ExitCode
