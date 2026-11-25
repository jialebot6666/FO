# golden 测试数据

全部由 engine/vaulttool.exe（参考二进制）在固定种子生成；Java 版必须逐字节一致（换行统一 LF）。

| 文件 | 生成命令 |
|---|---|
| pred-ominous-A.txt | vaulttool.exe pred -7346913998703726680 1 0 10 |
| pred-normal-A.txt | vaulttool.exe pred -7346913998703726680 0 0 10 |
| pred-ominous-0.txt | vaulttool.exe pred 0 1 0 10 |
| pred-ominous-12345.txt | vaulttool.exe pred 12345 1 0 10 |
| matches-2M.txt | vaulttool.exe matches -7346913998703726680 1 2000000 <第 4 行 itemspec> |
| cands-200.txt | 取 matches-2M.txt 前 200 条 M <offset> |
| narrow.txt | vaulttool.exe narrow -7346913998703726680 1 4 100 30 <第 7 行 itemspec> cands-200.txt |
| detect.txt | vaulttool.exe detect -7346913998703726680 1 0 2000 <第 4 行 itemspec> |
| search.txt | vaulttool.exe search -7346913998703726680 1 0 100000 5 minecraft:enchanted_golden_apple;minecraft:heavy_core |

注意：matches / narrow / detect 会在当前目录生成 vault-state-*.bin 缓存；对比时删除缓存或忽略该文件。
