# 第三方声明（THIRD_PARTY_NOTICES）

FO 本身以 CC0 1.0 发布（见 `LICENSE`）。本文件列出 FO 里包含的第三方代码及其许可证。
凡是从下列项目移植 / 改写进来的源文件，文件头都保留了来源说明。

---

## 1. sniprrfire/Highway-Builder

- 仓库：https://github.com/sniprrfire/Highway-Builder
- 许可证：MIT
- 用途：`fo/src/main/java/com/fo/addon/vault/TunnelDriver.java` 的隧道机主循环
  （逐格扫描、先填液体再挖、只补地板洞、自动换最好的镐、工具快坏就停、卡住自停、自动前进）。
- 改动：截面从「宽 4 / 高 3」改成固定 2×2；去掉栏杆、对角、末影箱补料、铺黑曜石；
  地板从 `replace` 改成 `place-missing`；加宝库保护、段长上限、±16 台阶和失败原因上报。
- 原项目作者与原项目不为 FO 背书，也不对 FO 的行为负责。

```
MIT License

Copyright (c) 2026 sniprrfire

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. 只调 API、未复制源码的项目

- **Baritone**（cabaletta/baritone，LGPL-3.0）：FO 只通过反射调用它的寻路 / 停止 / 设置接口
  （`com.fo.addon.pathing`），没有把源码抄进仓库。
- **Meteor Client 自带 HighwayBuilder**（GPL-3.0）：只参考状态机思路，不复制代码、不改本体。
- **dynmie/HighwayTools**（仓库未声明许可证）：只看思路，不复制代码。
- **musheck/HWU-Highway-Builder**（MIT）：截至 7.0p1 未复制代码，若以后引用会在本节列出。
