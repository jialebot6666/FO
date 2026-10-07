<#
  不用 git，直接走 GitHub REST API 交付一个新版本 FO。

  做三件事（对应 AGENTS.md 铁律 6）：
    ① 建源码 commit（只提交你列出的文件，其余靠 base_tree 原样继承）
    ② 归档 jar 到 releases/FO-<版本>-<短哈希>.jar，并更新 releases/README.md 的版本清单
    ③ 建 Release（tag 指向上面的归档 commit）+ 上传 jar 附件

  用法：
      # token 放临时文件，别写进脚本、别提交
      Set-Content .ghtoken -Value 'ghp_xxx' -Encoding ASCII -NoNewline
      powershell -NoProfile -ExecutionPolicy Bypass -File docs\scripts\gh-deliver.ps1 `
          -Version V5.3 `
          -Files @('fo/gradle/libs.versions.toml','fo/src/main/java/com/fo/addon/....java') `
          -CommitMessageFile rel\commit1.txt `
          -NotesFile rel\notes-V5.3.md
      Remove-Item .ghtoken      # 用完删掉，并去 GitHub 吊销 token

  参数：
      -Version            版本号，如 V5.3（须与 fo/gradle/libs.versions.toml 的 mod-version 一致）
      -Files              本次改动/新增的仓库相对路径（正斜杠）
      -CommitMessageFile  源码 commit 的完整说明（UTF-8 文件）
      -NotesFile          Release 更新介绍（UTF-8 文件；其中的 @@HASH@@ 会替换成源码 commit 短哈希）
      -ReadmeAnchor       版本清单里要插在它后面的那一行（正则），默认取上一个 FO-V* 行
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string[]]$Files,
    [Parameter(Mandatory = $true)][string]$CommitMessageFile,
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [string]$ReadmeAnchor = '',
    [string]$Owner = 'fanjiale666666',
    [string]$Repo  = 'FO'
)

$ErrorActionPreference = 'Stop'

$ws    = if ($env:FO_WS) { $env:FO_WS }
         else { Join-Path $env:USERPROFILE 'Documents\deepseek-harness\default-workspace' }
$tokFile = Join-Path $ws '.ghtoken'
if (-not (Test-Path $tokFile)) { throw "token file not found: $tokFile" }

$tok = (Get-Content $tokFile -Raw).Trim()
$Api = "https://api.github.com/repos/$Owner/$Repo"
$Hdr = @{ Authorization = "Bearer $tok"; Accept = "application/vnd.github+json"; "User-Agent" = "fo-deliver" }

function Call-Api {
    param([string]$Method, [string]$Url, $Json)
    if ($null -ne $Json) {
        # 必须转成 UTF-8 字节：PS 5.1 直接传字符串会按 Latin-1 发，中文变问号
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Json)
        return Invoke-RestMethod -Uri $Url -Method $Method -Headers $Hdr -Body $bytes `
            -ContentType "application/json; charset=utf-8" -TimeoutSec 600
    }
    return Invoke-RestMethod -Uri $Url -Method $Method -Headers $Hdr -TimeoutSec 600
}

function Get-Head {
    $r = Call-Api GET "$Api/git/ref/heads/main"
    $c = Call-Api GET "$Api/git/commits/$($r.object.sha)"
    return @{ sha = $r.object.sha; tree = $c.tree.sha; msg = $c.message }
}

function New-Blob([string]$localPath, [string]$repoPath, $treeList) {
    if (-not (Test-Path $localPath)) { throw "missing local file: $localPath" }
    $b64  = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($localPath))
    $blob = Call-Api POST "$Api/git/blobs" (@{ content = $b64; encoding = 'base64' } | ConvertTo-Json -Compress)
    Write-Host "  blob $repoPath -> $($blob.sha.Substring(0,8))"
    $treeList.Add(@{ path = $repoPath; mode = '100644'; type = 'blob'; sha = $blob.sha })
}

# ---------- 阶段一：源码 commit ----------
Write-Host "=== 阶段一：提交源码 ==="
$head  = Get-Head
Write-Host "base = $($head.sha)  ($($head.msg.Split("`n")[0]))"

$tree1 = New-Object System.Collections.Generic.List[object]
foreach ($f in $Files) {
    New-Blob (Join-Path $ws ("FO\" + ($f -replace '/', '\'))) $f $tree1
}
if ($tree1.Count -eq 0) { throw "no files to commit" }

$newTree1 = Call-Api POST "$Api/git/trees" (@{ base_tree = $head.tree; tree = $tree1 } | ConvertTo-Json -Depth 8 -Compress)
$msg1 = [System.IO.File]::ReadAllText((Join-Path $ws $CommitMessageFile), [System.Text.Encoding]::UTF8)
$c1 = Call-Api POST "$Api/git/commits" (@{ message = $msg1; tree = $newTree1.sha; parents = @($head.sha) } | ConvertTo-Json -Depth 5 -Compress)
$null = Call-Api PATCH "$Api/git/refs/heads/main" (@{ sha = $c1.sha } | ConvertTo-Json -Compress)
$short = $c1.sha.Substring(0, 7)
Write-Host "commit1 = $($c1.sha)  (short $short)"

# ---------- 阶段二：归档 jar ----------
Write-Host "=== 阶段二：归档 jar ==="
$jarName = "FO-$Version-$short.jar"
$srcJar  = Join-Path $ws "FO\fo\build\libs\FO-$Version.jar"
if (-not (Test-Path $srcJar)) { throw "jar not found: $srcJar (mod-version 对得上吗？)" }
$dstJar  = Join-Path $ws "FO\releases\$jarName"
Copy-Item $srcJar $dstJar -Force
Write-Host "archived -> releases/$jarName ($((Get-Item $dstJar).Length) bytes)"

# 更新 releases/README.md 的版本清单
$readme = Join-Path $ws 'FO\releases\README.md'
$t = [System.IO.File]::ReadAllText($readme, [System.Text.Encoding]::UTF8)
$pattern = if ($ReadmeAnchor) { $ReadmeAnchor } else { '\| FO-V[0-9][^\r\n]*' }
$ms = [regex]::Matches($t, $pattern)
if ($ms.Count -eq 0) { throw "README anchor not found (pattern: $pattern)" }
$last = $ms[$ms.Count - 1]
$row  = $last.Value + "`n| $jarName | $Version | （这里写这一版干了什么） |"
$t = $t.Substring(0, $last.Index) + $row + $t.Substring($last.Index + $last.Length)
[System.IO.File]::WriteAllText($readme, $t, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "README updated"

$head  = Get-Head
$tree2 = New-Object System.Collections.Generic.List[object]
New-Blob $dstJar "releases/$jarName" $tree2
New-Blob $readme "releases/README.md" $tree2
$newTree2 = Call-Api POST "$Api/git/trees" (@{ base_tree = $head.tree; tree = $tree2 } | ConvertTo-Json -Depth 8 -Compress)
$msg2 = "归档 $jarName"
$c2 = Call-Api POST "$Api/git/commits" (@{ message = $msg2; tree = $newTree2.sha; parents = @($head.sha) } | ConvertTo-Json -Depth 5 -Compress)
$null = Call-Api PATCH "$Api/git/refs/heads/main" (@{ sha = $c2.sha } | ConvertTo-Json -Compress)
Write-Host "commit2 = $($c2.sha)"

# ---------- 阶段三：Release + 附件 ----------
Write-Host "=== 阶段三：建 Release ==="
$body = [System.IO.File]::ReadAllText((Join-Path $ws $NotesFile), [System.Text.Encoding]::UTF8)
$body = $body.Replace('@@HASH@@', $short)

# tag 由 GitHub 自动创建，指向 target_commitish
$rel = Call-Api POST "$Api/releases" (@{
    tag_name         = "FO-$Version"
    target_commitish = $c2.sha
    name             = "FO $Version"
    body             = $body
    draft            = $false
    prerelease       = $false
} | ConvertTo-Json -Depth 5 -Compress)
Write-Host "release = $($rel.html_url)"

$uploadUrl = "https://uploads.github.com/repos/$Owner/$Repo/releases/$($rel.id)/assets?name=$jarName"
$resp = & curl.exe -sS -X POST -H "Authorization: Bearer $tok" `
    -H "Content-Type: application/java-archive" -H "User-Agent: fo-deliver" `
    --data-binary "@$dstJar" --max-time 900 $uploadUrl
$ok = $resp | ConvertFrom-Json
Write-Host "asset    = $($ok.name)  $($ok.size) bytes  state=$($ok.state)"
Write-Host "download = $($ok.browser_download_url)"
Write-Host "DONE $Version"
