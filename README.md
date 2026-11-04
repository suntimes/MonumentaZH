# Monumenta 中文补充 · MonumentaZH

Minecraft **1.20.4 / Fabric** 客户端汉化补充 MOD。离线读取词库，补充 Monumenta 的物品、任务、NPC 对话、菜单、聊天悬浮、书籍及告示牌译文。

当前版本为 **0.2.70-player-introduction**，附 **64,818 条**补充词库。职业专精任务与新导出内容已校对；本地原生回放检查通过，仍需实服视觉反馈。

直接下载：[最新 JAR](releases/0.2.70/Monumenta-ZH-Supplement-1.20.4-0.2.70-player-introduction.jar?raw=true) · [完整安装包](releases/0.2.70/Monumenta-ZH-1.20.4-0.2.70-full.zip?raw=true) · [校验值](releases/0.2.70/SHA256SUMS.txt)

首次三次提示分别介绍切换与快捷键、词库/JAR更新生效方式、漏译反馈；以普通玩家为对象。已看过的次数保留。译者采集说明在使用帮助中。

## 功能

- 已收录内容显示中文，未收录文字保留原文；不调用在线翻译服务。
- NPC 和地点可保留英文名称；NPC 对话正文采用中文。
- 任务悬浮保留英文任务名，并显示中英难度、条件和起点。
- 玩家聊天、私聊与玩家 ID 保护；书籍作者和明确的创作者署名保留原文。
- 告示牌显示和告示牌采集使用独立开关。
- 自动收集可选开启；书籍按页分批处理，相同内容共用缓存。
- 钱包、总数和物品组数采用固定格式规则，避免为每个数字保存一条译文。

初次没有配置时，所有功能默认关闭、快捷键默认未绑定。升级请保留原有 `settings.json` 与 Minecraft `options.txt`，沿用玩家现有设置。

## 构建与安装

需要 JDK 17 或更新版本，以及已运行过一次的 Minecraft 1.20.4 Fabric 客户端和 Fabric API。

在 PowerShell 中构建（替换示例路径）：

```powershell
./build.ps1 -GameDirectory 'X:/Minecraft/versions/1.20.4' -LibrariesDirectory 'X:/Minecraft/libraries'
```

1. 退出 Minecraft，将 `dist/Monumenta-ZH-Supplement-1.20.4-0.2.70-player-introduction.jar` 放入该实例的 `mods` 文件夹。
2. 同时移出旧版同名 MOD，避免重复加载。
3. 将本仓库 `config/monumenta_zh/` 中的 **overrides.json、names.json** 放入游戏实例相应配置目录。
4. 启动游戏，用 `/monumentazh on` 开启汉化；快捷键可在游戏控制设置中自行绑定。

**建议使用本次配套 JAR 与词库。词库文件更新后可执行 `/monumentazh reload`；JAR 更新必须退出并重启游戏，重载词库不会更新程序。**

构建直接使用本机合法安装的 Minecraft/Fabric 依赖，不下载、附带或重新分发 Minecraft 客户端。源码使用 1.20.4 的 intermediary 名称，当前构建脚本面向 Windows PowerShell。

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `/monumentazh` | 设置面板 |
| `/monumentazh on` / `off` | 中文显示 / 服务器原文 |
| `/monumentazh reload` | 重载词库 |
| `/monumentazh capture on` / `off` | 开启 / 暂停文本收集 |
| `/monumentazh signs on` / `off` | 告示牌汉化显示 |
| `/monumentazh signcapture on` / `off` | 告示牌文本采集 |
| `/monumentazh export` | 导出已收集内容 |

**非汉化人员无需开启文本收集。** 切换服务器原文不会自动关闭采集。导出前会完成排队中的书页，因此大量全新书籍可能需要等待。

采集文件、日志与个人设置由本机生成，不属于仓库内容。提交问题时，请只附必要的英文原文、中文显示及上下文，移除玩家私聊、账号凭据和本机路径。

## 测试与贡献

运行不依赖游戏窗口的核心测试：

```powershell
./test.ps1 -GsonJar 'X:/Minecraft/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'
```

欢迎通过 Issue 或 Pull Request 校对译文、报告误译和改进程序。词库为 UTF-8 JSON；请保留数字、占位符和原有英文键，不将玩家 ID 当作 NPC 名字翻译。涉及同名人物、技能层数和物品组数时，请附实际界面上下文。

更多内容见 [更新记录](docs/CHANGELOG.md)、[结构与验证](docs/DEVELOPMENT.md) 和 [来源说明](NOTICE.md)。

## 作者与致谢

作者：**suntime**  
附魔与技能汉化引用：**Moe_block**  
译者：**suntime、Armorer、vuken**  
感谢 **XM、HF、QL** 等玩家的翻译协助。

仅保证作者实际游玩部分的 80% 内容无误；其余区域以机翻为主。这里不代表全服务器覆盖率或整份词库准确率，遇到疑问请切换原文核对。

这是非官方个人中文补充，与 Monumenta 官方没有隶属关系。程序代码采用 [MIT](LICENSE)；游戏原文和引用译文的来源及适用范围见 [NOTICE](NOTICE.md)。

