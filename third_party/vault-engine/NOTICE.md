# 不祥宝库战利品引擎 — 来源与许可

`fo/src/main/resources/fo/vault/vaulttool.exe` 是预编译的 Windows x64 可执行文件，
运行时代码会先校验 SHA-256 再释放到 `<游戏目录>/fo/tools/`。

- 参考二进制 SHA-256：`4b96414e647334d0c0a351b9e5177745ec88978814947cc9d3424f9770f7df64`
- 源码：本目录（`engine/vaulttool.c`、`cubiomes/`），构建脚本 `engine/build-msvc.bat`
- 上游：`xpple/cubiomes` 提交 `06393cf40f0d4aa30a6db28302ab053037ca785b`
- 算法参考：`xpple/SeedMapper` 提交 `a6918e91c1759169d3ec143eb136281c39e64541`（MIT）
- 相对上游的改动：`patches/cubiomes-loot-patches.diff`
- 复现构建：Windows + MSVC Build Tools，`cd engine && build-msvc.bat`

## 许可证

- `cubiomes/`：MIT，Copyright (c) 2020 Cubitect，全文见 `cubiomes/LICENSE`
- `cubiomes/loot/`：见 `cubiomes/loot/LICENSE_loot_library.h.txt`
- `engine/vaulttool.c`：由 FO 项目维护，按仓库 GPLv3 分发

`vaulttool.exe` 作为独立进程被调用，不与 GPLv3 的 Java 代码链接；
其源码与构建脚本一并提供，可自行重编译并核对哈希。

## 对拍数据

`golden/` 是用参考二进制生成的输出样本，用于将来纯 Java 移植时逐字节验收。
