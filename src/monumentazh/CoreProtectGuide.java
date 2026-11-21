package monumentazh;

import net.minecraft.class_2561;
import net.minecraft.class_5250;

final class CoreProtectGuide {
    private static final int BLUE = 0x31B0E8, GRAY = 0xAAAAAA, WHITE = 0xFFFFFF;
    private CoreProtectGuide() {}

    private static class_5250 text(String s, int color) {
        return class_2561.method_43470(s).method_27694(style -> style.method_36139(color));
    }
    private static void command(class_5250 page, String verb, String args, String description) {
        if (verb.equals("help")) {
            page.method_10852(text("/co help", BLUE));
        } else {
            page.method_10852(text("/co ", BLUE));
            page.method_10852(text(verb, GRAY));
        }
        if (!args.isEmpty()) page.method_10852(text(" " + args, verb.equals("help") ? GRAY : BLUE));
        page.method_10852(text("　" + description + "\n", WHITE));
    }
    static class_2561 body() {
        var page = text("查看市场交易\n", BLUE);
        command(page, "inspect", "", "开启或关闭检查模式。");
        command(page, "i", "", "同上，是检查指令的简写。");
        page.method_10852(text("开启后，右键点击箱子，查看谁存入或取走了物品。\n", WHITE));
        page.method_10852(text("\n按条件查询\n", BLUE));
        command(page, "l", "t:1h r:5 a:container", "查询容器存取记录。");
        page.method_10852(text("上例查询附近 5 格内最近一小时的记录。\n", WHITE));
        page.method_10852(text("a:+container", BLUE));
        page.method_10852(text(" 只查存入，", WHITE));
        page.method_10852(text("a:-container", BLUE));
        page.method_10852(text(" 只查取出。\n", WHITE));
        command(page, "l", "2", "查看查询结果的第 2 页。");
        page.method_10852(text("\n查询帮助\n", BLUE));
        command(page, "help", "", "列出指令。");
        command(page, "help", "lookup", "查看查询指令的完整用法。");
        page.method_10852(text("\n其他指令\n", BLUE));
        command(page, "rollback", "t:1h r:5", "回滚附近 5 格内最近一小时的改动。");
        command(page, "restore", "t:1h r:5", "恢复上述范围内已回滚的改动。");
        command(page, "purge", "t:90d", "删除超过 90 天的日志。");
        command(page, "reload", "", "重载插件配置。");
        command(page, "status", "", "查看插件运行状态。");
        return page;
    }
}
