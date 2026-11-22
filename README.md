# Monumenta 中文MOD

**近期更新会逐渐减缓，可能持续到明年1月份，精力、时间有限。**

Minecraft **1.20.4 / Fabric** 客户端汉化补充 MOD。

下载：[最新 Release](https://github.com/suntimes/MonumentaZH/releases/latest)。建议下载完整安装包，包含 JAR 与配套词库，也提供独立 JAR 和 SHA256 校验文件。

（一般来说是不需要校验的）

使用须知：/monumentazh welcome

## 功能
可以使用快捷键切换中/英文本
详情见游戏内
初次无配置时，所有功能默认关闭、快捷键默认未绑定。升级请保留原有 `settings.json` 与 Minecraft `options.txt`，沿用玩家现有设置。
————————
未来
（也许会添加中文wiki）：也就是说MOD未来可能自带一份简易指南，大致告诉你（怎么玩，怎么用，怎么去|一些规范大概是怎样）

## 构建与安装

需要 JDK 17 或更新版本，以及已运行过一次的 Minecraft 1.20.4 Fabric 客户端和 Fabric API。

在 PowerShell 中构建（替换示例路径）：

```powershell
./build.ps1 -GameDirectory 'X:/Minecraft/versions/1.20.4' -LibrariesDirectory 'X:/Minecraft/libraries'
```

1. 退出 Minecraft，将 `dist/Monumenta-ZH-Supplement-1.20.4-<version>.jar` 放入该实例的 `mods` 文件夹。
2. 同时移出旧版同名 MOD，避免重复加载。
3. 将本仓库 `config/monumenta_zh/` 中的 **overrides.json、names.json** 放入游戏实例相应配置目录。
4. 启动游戏，用 `/monumentazh on` 开启汉化；快捷键可在游戏控制设置中自行绑定。

**建议使用本次配套 JAR 与词库。词库文件更新后可执行 `/monumentazh reload`，JAR 更新必须退出并重启游戏，重载词库不会更新程序。**

构建直接使用本机合法安装的 Minecraft/Fabric 依赖，不下载、附带或重新分发 Minecraft 客户端。源码使用 1.20.4 的 intermediary 名称，当前构建脚本面向 Windows PowerShell。

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `/monumentazh` | 设置面板 |
| `/monumentazh on` / `off` | 中文显示 / 服务器原文 |
| `/monumentazh toggle` | 切换中/英文 |
| `/monumentazh welcome` | 查看使用提示 |
| `/monumentazh reload` | 重载词库 |
译者功能（普通玩家不要用）
尤其是告示牌收集！！！！！
| `/monumentazh capture on` / `off` | 开启 / 暂停文本收集 |
| `/monumentazh signs on` / `off` | 告示牌汉化显示 |
| `/monumentazh signcapture on` / `off` | 告示牌文本采集 |
| `/monumentazh export` | 导出已收集内容 |

**非汉化人员无需开启文本收集。** 
切换服务器原文不会自动关闭采集。导出前会完成排队中的书页，因此大量文本可能需要等待。
采集文件、日志与个人设置由本机生成，不属于仓库内容。提交问题时，请只附必要的英文原文、中文显示及上下文。
**注**：在采集过程中可能会因为‘完全搞不懂的问题’导致采集失败，这是没有办法的事情。可能需要手动截图/其他方式。

## 测试与贡献
运行不依赖游戏窗口的核心测试：

```powershell
./test.ps1 -GsonJar 'X:/Minecraft/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'
```

欢迎通过 Issue 或 Pull Request 校对译文、报告误译和改进程序。词库为 UTF-8 JSON；请保留数字、占位符和原有英文键，不将玩家 ID 当作 NPC 名字翻译。涉及同名人物、技能层数和物品组数时，请附实际界面上下文。

更多内容见 [更新记录](docs/CHANGELOG.md)、[结构与验证](docs/DEVELOPMENT.md) 和 [来源说明](NOTICE.md)。

****未来可能上传至 paratranz 网页 公开全译文用于校对****
如有大佬愿意协助，感激不尽，个人英语水平有限。

## 作者与致谢
作者：**suntime**  
附魔与技能汉化引用：**Moe_block**  
译者：**suntime、Armorer、vk**
感谢 **XM、HF、QL** 等玩家的翻译协助。

！！！！！非官方模组，与Monumenta制作组无关。**有机翻内容**！！！！！

程序代码采用 [MIT](LICENSE)；游戏原文和引用译文的来源及适用范围见 [NOTICE](NOTICE.md)。

***本人能力有限，有大佬愿意接受该项目/拿去优化都可以，我没意见。毕竟只是一拍脑门想的事情**

关于转发使用：我没意见，但希望官方不会管这方面的事情:)

