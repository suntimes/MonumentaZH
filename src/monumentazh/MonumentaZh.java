package monumentazh;

import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.class_1799;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_465;
import net.minecraft.class_642;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Intermediary names allow building directly against the user's exact 1.20.4 client. */
public final class MonumentaZh implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile TranslationEngine engine;
    private static volatile HudTranslator hudTranslator;
    private static volatile NameLocalizer nameLocalizer;
    private static boolean bilingualNames = false;
    private static boolean bilingualPlaces = false;
    private static boolean enabled = false;
    private static boolean captureEnabled = false;
    private static boolean signTranslationEnabled = false;
    private static boolean signCaptureEnabled = false;
    private static boolean force = false;
    private static final FirstJoinIntroduction introduction=new FirstJoinIntroduction();
    private static net.minecraft.class_8816 introductionPopup;
    private static boolean welcomeRequested;
    private static final net.minecraft.class_370.class_9037 statusToastType=new net.minecraft.class_370.class_9037(5000);
    private static Path configDir;
    private static final EncounteredTextStore captured = new EncounteredTextStore();
    private static final IdentityHashMap<class_2561, DisplayCache> displayCache = new IdentityHashMap<>();
    private static final ArrayDeque<class_2561> displayOrder = new ArrayDeque<>();
    // Cache only source metadata, never the changing online-player catalogue.
    private record SourceMetadata(int hash,class_2561 snapshot,boolean hidden,boolean player,boolean nonPlayer) {}
    private record SourceKey(int hash,class_2561 source) {
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){return value instanceof SourceKey other&&hash==other.hash&&source.equals(other.source);}
    }
    private static final IdentityHashMap<class_2561,SourceMetadata> sourceMetadata=new IdentityHashMap<>();
    private static final ArrayDeque<class_2561> metadataOrder=new ArrayDeque<>();
    private static final Map<SourceKey,SourceMetadata> sourceContents=new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<SourceKey,SourceMetadata> entry){return size()>4096;}
    };
    private static SourceMetadata sourceMetadata(class_2561 source) {
        int hash=source.hashCode();
        SourceMetadata cached=sourceMetadata.get(source);
        if(cached!=null&&cached.hash()==hash&&source.equals(cached.snapshot()))return cached;
        SourceMetadata result=sourceContents.get(new SourceKey(hash,source));
        if(result==null) {
            JsonElement json=class_2561.class_2562.method_10868(source);
            result=new SourceMetadata(hash,class_2561.class_2562.method_10872(json),TranslationEngine.hasHiddenText(json),
                CapturePrivacy.playerComponent(json),CapturePrivacy.nonPlayerComponent(json));
            sourceContents.put(new SourceKey(hash,result.snapshot()),result);
        }
        if(cached==null) {
            while(sourceMetadata.size()>=4096)sourceMetadata.remove(metadataOrder.removeFirst());
            metadataOrder.addLast(source);
        }
        sourceMetadata.put(source,result);
        return result;
    }
    private static final Map<DisplayKey,DisplayCache> contentCache = new LinkedHashMap<>(256,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<DisplayKey,DisplayCache> entry){return size()>4096;}
    };
    private record SignLayout(int width,net.minecraft.class_377 font,net.minecraft.class_8242 text) {}
    private static final Map<net.minecraft.class_8242, SignLayout> signCache = new WeakHashMap<>();
    private static final CaptureWorkQueue<class_1799> captureWork=new CaptureWorkQueue<>();
    private record ItemCaptureKey(String name,class_2487 display,class_2487 gui) {}
    private static final Map<ItemCaptureKey,Boolean> itemCaptureCache=new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ItemCaptureKey,Boolean> entry){return size()>512;}
    };
    private static long revision;
    private static int ticks = 0;
    private static net.minecraft.class_746 identityPlayer;
    private record DisplayCache(String kind,int hash,class_2561 translated,boolean changed,class_2561 upper) {}
    // Stored keys own a deserialized snapshot; mutable server components never become map keys.
    private record DisplayKey(String kind,int hash,class_2561 source) {
        @Override public int hashCode(){return 31*kind.hashCode()+hash;}
        @Override public boolean equals(Object value){return value instanceof DisplayKey other&&hash==other.hash&&kind.equals(other.kind)&&source.equals(other.source);}
    }

    @Override public void onInitializeClient() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("monumenta_zh");
        ClientControls.register();
        loadSettings();
        boolean privacyReady=true;
        try {CapturePrivacy.migrateConfig(configDir);}
        catch(Exception ex){privacyReady=false;captureEnabled=false;System.err.println("[MonumentaZH] Identity migration failed; collection is paused. Original files are preserved.");}
        reload();
        try { if(privacyReady)captured.loadArchives(configDir); }
        catch (Exception ex) { System.err.println("[MonumentaZH] Capture archive index failed: " + ex.getMessage()); }
        // The checkpoint is the current page; an older manual export may belong to an archived page.
        for (String filename : privacyReady?List.of("encountered_text.json", "captured_text.json"):List.<String>of()) {
            if (!Files.isRegularFile(configDir.resolve(filename))) continue;
            try { captured.load(configDir.resolve(filename)); break; }
            catch (Exception ex) { System.err.println("[MonumentaZH] Capture load failed: " + filename + ": " + ex.getMessage()); }
        }
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (!eligible()) return;
            List<class_2561> originalLines=enabled&&ContextTranslations.hasTooltipCandidate(lines)?List.copyOf(lines):null;
            if (captureEnabled) capture(stack, "物品悬浮说明");
            List<class_2561> paddedInfo=enabled&&ClassMenuTooltip.matches(stack,lines)?List.copyOf(lines):null;
            boolean alchemist=ClassMenuTooltip.alchemist(lines);
            boolean shaman=ShamanTooltip.matches(lines);
            boolean bountyPlace=bountyPlaceTitle(lines);
            List<class_2561> keyOriginals=enabled&&isKeyTooltip(lines)?List.copyOf(lines):null;
            for (int i = 0; i < lines.size(); i++) {
                lines.set(i, display(lines.get(i), alchemist ? i==0?"alchemist_title":"alchemist_tooltip" : shaman ? i==0?"shaman_title":"shaman_tooltip" : i==0?(bountyPlace?"bounty_place_title":"item_name"):paddedInfo!=null?"class_tooltip":"tooltip"));
            }
            if(originalLines!=null)ContextTranslations.tooltip(originalLines,lines,engine);
            if(keyOriginals!=null)chineseKeyTooltip(keyOriginals,lines);
            if(paddedInfo!=null) {
                if(enabled&&ExtraInformationTooltip.matches(paddedInfo))ExtraInformationTooltip.compact(paddedInfo,lines);
                if(enabled)ClassSkillTooltip.apply(paddedInfo,lines,engine);
                ClassMenuTooltip.compact(paddedInfo,lines);
            }
        });
        // Native player chat is untouched. Forwarded player chat is filtered before any dictionary lookup.
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : display(message, "server_message"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.method_1558() == null && force) { force = false; invalidateDisplays(); }
            ClientControls.tick(client);
            if(settingsRequested) {
                settingsRequested=false;
                if(client.field_1724!=null) openSettings(client);
            }
            if(client.field_1724==null){CapturePrivacy.clearSession();identityPlayer=null;HologramLayout.clear();}
            else if(identityPlayer!=client.field_1724){identityPlayer=client.field_1724;if(identityPlayer.method_7334()!=null)CapturePrivacy.observePlayer(identityPlayer.method_7334().getName());}
            if(welcomeRequested&&client.field_1724!=null) {
                welcomeRequested=false;introduction.shownCount(Math.max(1,introduction.shownCount()));
                introductionPopup=createIntroductionPopup(client);client.method_1507(introductionPopup);saveSettings();
            }
            tickIntroduction(client);
            if(introductionPopup==null&&introduction.statusTick(client.field_1724!=null?client.method_1562():null)) {
                showStatusToast(client);
            }
            ++ticks;
            if (eligible() && ticks % 100 == 0 && client.method_1562()!=null) {
                for (var entry:client.method_1562().method_2880()) nameLocalizer.observePlayer(entry.method_2966().getName());
            }
            if (eligible() && captureEnabled && ticks % 20 == 0 && client.field_1755 instanceof class_465<?> screen) {
                for (var slot : screen.method_17577().field_7761) capture(slot.method_7677(), screen.method_25440().getString());
            }
            if(eligible()&&captureEnabled)captureWork.tick(MonumentaZh::captureNow,MonumentaZh::captureBookPage);
            if (ticks % 600 == 0) captured.requestCheckpoint(configDir.resolve("encountered_text.json"));
            try { captured.tickCheckpoint(); }
            catch (IOException ex) { System.err.println("[MonumentaZH] Capture save failed; records retained for retry: " + ex.getMessage()); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            captureWork.drain(MonumentaZh::captureNow,MonumentaZh::captureBookPage);
            try { captured.close(configDir.resolve("encountered_text.json")); }
            catch (IOException ex) { System.err.println("[MonumentaZH] Capture shutdown save failed: " + ex.getMessage()); }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            var root=ClientCommandManager.literal("monumentazh")
                .executes(ctx -> requestSettings())
                .then(ClientCommandManager.literal("on").executes(ctx -> runCommand(ctx.getSource(), ClientControls.Action.ON)))
                .then(ClientCommandManager.literal("off").executes(ctx -> runCommand(ctx.getSource(), ClientControls.Action.OFF)))
                .then(ClientCommandManager.literal("toggle").executes(ctx -> runCommand(ctx.getSource(), ClientControls.Action.TOGGLE)))
                .then(ClientCommandManager.literal("capture")
                    .then(ClientCommandManager.literal("on").executes(ctx -> { force = true; setCaptureEnabled(true); ctx.getSource().sendFeedback(class_2561.method_43470("已开启英文自动收集。")); return 1; }))
                    .then(ClientCommandManager.literal("off").executes(ctx -> { setCaptureEnabled(false); checkpoint(); ctx.getSource().sendFeedback(class_2561.method_43470("已暂停英文自动收集，已有记录保留。")); return 1; })))
                .then(ClientCommandManager.literal("signs")
                    .then(ClientCommandManager.literal("on").executes(ctx -> { setSignTranslationEnabled(true); ctx.getSource().sendFeedback(notice("已开启告示牌汉化；牌文采集由独立开关控制。",false)); return 1; }))
                    .then(ClientCommandManager.literal("off").executes(ctx -> { setSignTranslationEnabled(false); ctx.getSource().sendFeedback(notice("已关闭告示牌汉化显示；采集设置独立保留，右键聊天仍可汉化。",false)); return 1; })))
                .then(ClientCommandManager.literal("signcapture")
                    .then(ClientCommandManager.literal("on").executes(ctx -> { setSignCaptureEnabled(true); ctx.getSource().sendFeedback(notice("已允许告示牌采集，需同时开启自动收集总开关；牌面显示设置保持。",false)); return 1; }))
                    .then(ClientCommandManager.literal("off").executes(ctx -> { setSignCaptureEnabled(false); ctx.getSource().sendFeedback(notice("已暂停告示牌采集，已有牌面译文照常显示。",false)); return 1; })))
                .then(ClientCommandManager.literal("reload").executes(ctx -> { reload(); ctx.getSource().sendFeedback(class_2561.method_43470("中文词库已重载。")); return 1; }))
                .then(ClientCommandManager.literal("welcome").executes(ctx -> { welcomeRequested=true;return 1; }))
                .then(ClientCommandManager.literal("export").executes(ctx -> runCommand(ctx.getSource(), ClientControls.Action.EXPORT)));
            var menu=ClientCommandManager.literal("menu");
            for(String action:List.of("on","off","capture_on","capture_off","names_on","names_off","places_on","places_off","signs_on","signs_off","sign_capture_on","sign_capture_off","reload","export","about","keys"))
                menu.then(ClientCommandManager.literal(action).executes(ctx -> runMenu(ctx.getSource(),action)));
            root.then(menu.executes(ctx -> requestSettings()));
            dispatcher.register(root);
        });
        System.out.println("[MonumentaZH] Offline Chinese supplement initialized for Minecraft 1.20.4.");
    }

    record ActionResult(boolean success,String message) {}

    private static int runCommand(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,ClientControls.Action action) {
        ActionResult result=executeAction(action,source.getClient());
        if(result.success())source.sendFeedback(notice(result.message(),false));
        else source.sendError(notice(result.message(),true));
        return result.success()?1:0;
    }

    static ActionResult executeAction(ClientControls.Action action,class_310 client) {
        switch(action) {
            case ON -> { force=true;setEnabled(true);return new ActionResult(true,"已开启中文补充（当前连接允许任意地址）。"); }
            case OFF -> { setEnabled(false);return new ActionResult(true,"已切换为服务器原文；自动收集设置保持不变。"); }
            case TOGGLE -> { force=true;setEnabled(!enabled);return new ActionResult(true,enabled?"已切换为中文。":"已切换为服务器原文。"); }
            case EXPORT -> {
                try {
                    captureWork.drain(MonumentaZh::captureNow,MonumentaZh::captureBookPage);
                    if(client!=null && client.field_1724!=null) {
                        capture(client.field_1724.method_6047(),"主手物品");
                        capture(client.field_1724.method_6079(),"副手物品");
                        if(client.field_1755 instanceof class_465<?> screen)
                            for(var slot:screen.method_17577().field_7761)capture(slot.method_7677(),screen.method_25440().getString());
                    }
                    captureWork.drain(MonumentaZh::captureNow,MonumentaZh::captureBookPage);
                    Path target=configDir.resolve("captured_text.json");
                    captured.write(target);captured.write(configDir.resolve("encountered_text.json"));
                    return new ActionResult(true,"已导出本批 "+captured.itemCount()+" 个物品、"+captured.textCount()+" 条界面英文："+target+
                        (captured.archivedBatchCount()>0?"；另有 "+captured.archivedBatchCount()+" 个历史批次位于 capture_archive 文件夹。":""));
                } catch(IOException ex) { return new ActionResult(false,"导出失败："+ex.getMessage()); }
            }
        }
        throw new IllegalArgumentException("Unknown local action");
    }

    private static JsonObject messagePart(String text,String color,boolean bold) {
        JsonObject part=new JsonObject();part.addProperty("text",text);part.addProperty("color",color);part.addProperty("bold",bold);return part;
    }
    static class_2561 notice(String text,boolean error) {
        JsonObject root=messagePart("[Monumenta 中文] ","aqua",true);root.addProperty("font","minecraft:default");
        JsonArray extra=new JsonArray();extra.add(messagePart(text,error?"red":"white",false));root.add("extra",extra);
        return class_2561.class_2562.method_10872(root);
    }
    private static boolean settingsRequested;
    private static int requestSettings() {
        // Chat closes after submitting a command. Open on the following client tick.
        settingsRequested=true;
        return 1;
    }
    public static void openSettings(class_310 client) {
        var parent=client.field_1755 instanceof net.minecraft.class_408 ? null : client.field_1755;
        client.method_1507(new SettingsScreen(parent,()->new SettingsScreen.State(enabled,bilingualNames,
            bilingualPlaces,signTranslationEnabled,captureEnabled,signCaptureEnabled,
            "本批："+captured.itemCount()+" 件物品 · "+captured.textCount()+" 条文本　历史："+captured.archivedBatchCount()+" 批"),
            action->applySettings(client,action)));
    }
    private static int runMenu(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,String action) {
        // Retain commands behind existing clickable chat history.
        var client=source.getClient();
        if(action.equals("keys") || action.equals("about")) {source.sendFeedback(menuHelp(action.equals("keys")));return 1;}
        applySettings(client,action);
        return requestSettings();
    }
    private static void applySettings(class_310 client,String action) {
        switch(action) {
            case "on" -> executeAction(ClientControls.Action.ON,client);
            case "off" -> executeAction(ClientControls.Action.OFF,client);
            case "capture_on" -> {force=true;setCaptureEnabled(true);}
            case "capture_off" -> {setCaptureEnabled(false);checkpoint();}
            case "names_on","names_off" -> {bilingualNames=action.equals("names_on");invalidateDisplays();saveSettings();}
            case "places_on","places_off" -> {bilingualPlaces=action.equals("places_on");invalidateDisplays();saveSettings();}
            case "signs_on","signs_off" -> setSignTranslationEnabled(action.equals("signs_on"));
            case "sign_capture_on","sign_capture_off" -> setSignCaptureEnabled(action.equals("sign_capture_on"));
            case "reload" -> {
                reload();
                client.method_1507(new SettingsInfoScreen(client.field_1755,"重载词库",class_2561.method_43470("中文词库已重载。")));
            }
            case "export" -> {
                ActionResult result=executeAction(ClientControls.Action.EXPORT,client);
                client.method_1507(new SettingsInfoScreen(client.field_1755,"导出文本",notice(result.message(),!result.success())));
            }
            case "about","keys" -> client.method_1507(new SettingsInfoScreen(client.field_1755,action.equals("keys")?"按键说明":"使用说明",menuHelp(action.equals("keys"))));
            default -> throw new IllegalArgumentException(action);
        }

    }

    private static class_2561 menuHelp(boolean keys) {
        JsonObject root=messagePart("","white",false);root.addProperty("font","minecraft:default");
        JsonArray lines=new JsonArray();
        if(keys) {
            lines.add(messagePart("控制 → 按键绑定 → Monumenta 中文补充\n","aqua",false));
            lines.add(messagePart("默认未设置，可自定义开启、关闭、切换与导出键。\n","white",false));
            lines.add(messagePart("背包、容器、交易面板和聊天框内均可使用。\n建议选择功能键或鼠标侧键，避免影响打字。\n搜索和重命名时不触发。\n","gray",false));
        } else {
            appendCredits(lines);
            lines.add(messagePart("\n日常使用\n","gold",true));
            lines.add(messagePart("/monumentazh 打开设置，可开关汉化。\n\n自定义快捷键：\n选项 → 控制 → 按键绑定\n普通玩家无需开启自动收集。\n","white",false));
            lines.add(messagePart("词库文件更新后，点击“重载词库”；\n模组 JAR 更新后，需重启游戏。\n发现漏译，先核对词库和模组版本。\n","aqua",false));
            appendTranslationNotice(lines);lines.add(messagePart("\n","white",false));
        }
        root.add("extra",lines);
        return class_2561.class_2562.method_10872(root);
    }

    private static void tickIntroduction(class_310 client) {
        if(introductionPopup!=null&&client.field_1755!=introductionPopup)introductionPopup=null;
        if(!introduction.tick(client.field_1724!=null?client.method_1562():null,
            client.field_1724!=null&&client.field_1755==null&&client.method_18506()==null))return;
        introductionPopup=createIntroductionPopup(client);
        client.method_1507(introductionPopup);saveSettings();
    }

    public static boolean closeIntroductionPopup(net.minecraft.class_8816 popup) {
        if(introductionPopup!=popup)return false;
        introductionPopup=null;class_310.method_1551().method_1507(null);return true;
    }

    public static boolean isIntroductionPopup(net.minecraft.class_8816 popup) {return introductionPopup==popup;}

    private static net.minecraft.class_8816 createIntroductionPopup(class_310 client) {
        var background=new net.minecraft.class_424(class_2561.method_43470(""));
        background.method_25423(client,client.method_22683().method_4486(),client.method_22683().method_4502());
        return new net.minecraft.class_8816.class_8817(background,class_2561.method_43470("Monumenta汉化"))
            .method_54126(Math.min(460,client.method_22683().method_4486()-24))
            .method_54129(introductionMessage())
            .method_54130(class_2561.method_43470("关闭"),popup -> popup.method_25419())
            .method_54125();
    }

    private static class_2561 statusMessage() {
        JsonObject root=messagePart("Monumenta汉化：","gold",true);root.addProperty("font","minecraft:default");
        JsonArray extra=new JsonArray();extra.add(messagePart(enabled?"已启用":"已关闭",enabled?"green":"red",true));root.add("extra",extra);
        return class_2561.class_2562.method_10872(root);
    }

    private static void showStatusToast(class_310 client) {
        JsonObject title=messagePart("Monumenta汉化","gold",true);title.addProperty("font","minecraft:default");
        JsonObject state=messagePart(enabled?"已启用":"已关闭",enabled?"green":"red",true);state.addProperty("font","minecraft:default");
        JsonArray extra=new JsonArray();
        extra.add(messagePart("\n译者进出市场后记得打开提取文本","gray",false));state.add("extra",extra);
        // Use the native wrapped toast so the reminder fits without expanding a single line.
        var manager=client.method_1566();
        net.minecraft.class_370.method_55507(manager,statusToastType);
        manager.method_1999(net.minecraft.class_370.method_29047(client,statusToastType,
            class_2561.class_2562.method_10872(title),class_2561.class_2562.method_10872(state)));
    }

    private static class_2561 introductionMessage() {
        JsonObject root=messagePart("","white",false);root.addProperty("font","minecraft:default");
        JsonArray lines=new JsonArray();appendCredits(lines);
        lines.add(messagePart("\n使用提示\n","gold",true));
        lines.add(popupCommand("", "打开设置"));
        lines.add(popupCommand("toggle", "切换中/英文"));
        lines.add(messagePart("\n自定义快捷键：\n选项 → 控制 → 按键绑定\n","white",false));
        appendTranslationNotice(lines);
        root.add("extra",lines);return class_2561.class_2562.method_10872(root);
    }

    private static JsonObject popupCommand(String action,String description) {
        JsonObject line=commandLine(action,description,"aqua");
        JsonArray parts=line.getAsJsonArray("extra");
        JsonObject button=parts.get(0).getAsJsonObject();
        String command=action.isEmpty()?"/monumentazh":"/monumentazh "+action;
        button.addProperty("text",command);
        button.getAsJsonObject("clickEvent").addProperty("value",command);
        parts.set(1,messagePart("\n"+description+"\n","white",false));
        return line;
    }

    private static JsonObject messageHeader() {
        String version=FabricLoader.getInstance().getModContainer("monumenta_zh_supplement")
            .map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("未知版本");
        JsonObject root=messagePart("━━ Monumenta 汉化 · "+version+" ━━\n","gold",true);
        root.addProperty("font","minecraft:default");return root;
    }
    private static void appendCredits(JsonArray lines) {
        lines.add(messagePart("作者：suntime\n","white",false));
        lines.add(messagePart("附魔与技能汉化引用：Moe_block\n","light_purple",false));
        lines.add(messagePart("译者：suntime、Armorer、vk\n","white",false));
        lines.add(messagePart("感谢XM、HF、QL等玩家的翻译协助。\n","gray",false));
    }
    private static void appendCommonCommands(JsonArray lines) {
        lines.add(messagePart("\n常用指令\n","gold",true));
        lines.add(commandLine("on","开启汉化","green"));
        lines.add(commandLine("off","显示服务器原文","yellow"));
        lines.add(commandLine("toggle","切换汉化与原文","aqua"));
        lines.add(commandLine("reload","重新加载词库","aqua"));
    }
    private static JsonObject commandLine(String action,String description,String color) {
        String command="/monumentazh "+action;
        JsonObject line=messagePart("","white",false),button=messagePart(command,color,false);
        JsonObject click=new JsonObject();click.addProperty("action","suggest_command");click.addProperty("value",command);button.add("clickEvent",click);
        JsonObject hover=new JsonObject();hover.addProperty("action","show_text");hover.add("contents",messagePart("点击填入指令，按回车执行","gray",false));button.add("hoverEvent",hover);
        // Only the command is interactive; the explanation and subsequent lines do not inherit it.
        JsonArray extra=new JsonArray();extra.add(button);extra.add(messagePart("  —  "+description+"\n","white",false));line.add("extra",extra);return line;
    }
    private static void appendTranslationNotice(JsonArray lines) {
        lines.add(messagePart("\n使用声明与须知\n","gold",true));
        lines.add(messagePart("非官方模组，与Monumenta制作组无关。\n有机翻内容。","white",false));
    }

    public static boolean signTranslationActive() { return signTranslationEnabled && active(); }

    public static boolean active() {
        return enabled && eligible();
    }

    private static boolean eligible() {
        if (engine == null) return false;
        class_310 client = class_310.method_1551();
        if (client == null) return false;
        class_642 server = client.method_1558();
        return server != null && (force || server.field_3761.toLowerCase(Locale.ROOT).contains("monumenta"));
    }

    private static boolean isKeyTooltip(List<class_2561> lines) {
        if(lines.isEmpty())return false;
        String first=TranslationEngine.normalize(lines.get(0).getString());
        if(first.endsWith(" Key")||first.endsWith("钥匙"))return true;
        for(class_2561 line:lines)if(TranslationEngine.normalize(line.getString()).matches(".*[:：]\\s*(?:Key|钥匙)"))return true;
        return false;
    }
    public static boolean chineseKeyTooltip(List<class_2561> original,List<class_2561> shown) {
        if(!active()||!isKeyTooltip(original))return false;
        JsonArray array=new JsonArray();
        for(class_2561 line:shown)array.add(class_2561.class_2562.method_10868(line));
        if(!nameLocalizer.chineseKeyReferences(array))return false;
        for(int i=0;i<shown.size();i++)shown.set(i,class_2561.class_2562.method_10872(array.get(i)));
        return true;
    }
    public static class_2561 translateTitle(class_2561 title) {
        return display(title, "menu_title");
    }

    public static class_2561 translateHud(class_2561 message) {
        return display(message, "bossbar");
    }

    /** Called only at specific server-text display surfaces, never on player chat or player names. */
    /** Bounty menu icons have only a place title and a numeric tier, plus vanilla debug rows. */
    private static boolean bountyPlaceTitle(List<class_2561> lines) {
        if(lines==null||lines.size()<2||nameLocalizer==null||bountyPlaceName(lines.get(0).getString())==null)return false;
        if(!TranslationEngine.normalize(lines.get(1).getString()).matches("Tier\\s+[1-9][0-9]*"))return false;
        for(int i=2;i<lines.size();i++) {
            String row=TranslationEngine.normalize(lines.get(i).getString());
            if(!row.isEmpty()&&!row.matches("minecraft:[a-z0-9_./-]+")&&!row.startsWith("NBT:")&&!row.startsWith("NBT："))return false;
        }
        return true;
    }

    private static NameLocalizer.Name bountyPlaceName(String raw) {
        NameLocalizer.Name direct=nameLocalizer.exactPlace(raw);
        if(direct!=null)return direct;
        String text=TranslationEngine.normalize(raw);
        int start=text.lastIndexOf('（');
        if(start>0&&text.endsWith("）")&&text.substring(0,start).codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)) {
            String english=text.substring(start+1,text.length()-1);
            NameLocalizer.Name name=nameLocalizer.exactPlace(english);
            if(name!=null&&name.english().equals(english))return name;
        }
        return null;
    }

    public static synchronized class_2561 display(class_2561 original, String kind) {
        if (original == null || !enabled && !captureEnabled || EncounteredTextStore.isChatKind(kind) || !eligible()) return original;
        if (sourceMetadata(original).hidden()) return original;
        if ((kind.equals("entity_label") || kind.equals("hologram_label") || kind.equals("text_display")) && playerLabel(original)&&!knownPlayerTitle(original,kind)) return original;
        boolean serverMessage = kind.equals("server_message") || kind.equals("npc_dialogue") || kind.equals("server_notice");
        if (serverMessage) {
            if(ChatMessageFilter.isLocalModMessage(original.getString()))return original;
            ChatMessageFilter.Kind classification = ChatMessageFilter.classify(class_2561.class_2562.method_10868(original), original.getString());
            if (classification == ChatMessageFilter.Kind.PLAYER) return original;
            kind = classification == ChatMessageFilter.Kind.NPC ? "npc_dialogue" : "server_notice";
        }
        DisplayCache cached = displayCache.get(original);
        int hash = original.hashCode();
        if (cached == null || cached.hash() != hash || !cached.kind().equals(kind)) {
            DisplayKey lookup=new DisplayKey(kind,hash,original);
            cached=contentCache.get(lookup);
            if(cached==null) {
            boolean centralPrefix=kind.equals("subtitle")&&incompleteCentralSubtitle(original.getString());
            class_2561 translated = centralPrefix?original:serverMessage ? translateServerComponent(original)
                : kind.equals("alchemist_tooltip") || kind.equals("alchemist_title") ? translateAlchemistComponent(original)
                : kind.equals("shaman_title") || kind.equals("shaman_tooltip") ? translateShamanComponent(original)
                : kind.equals("book_page") ? translateBookComponent(original)
                : kind.equals("bossbar") || kind.equals("actionbar") ? translateHudComponent(original) : translateTooltipLine(original);
            if (kind.equals("bossbar") || kind.equals("actionbar")) translated = translateTooltipLine(translated);
            if(!serverMessage&&nameLocalizer.knownLabel(original.getString())) {
                JsonElement json=class_2561.class_2562.method_10868(translated);
                if(json.isJsonPrimitive()){JsonObject wrapper=new JsonObject();wrapper.add("text",json);json=wrapper;}
                if(NameLocalizer.cleanLegacyCodes(json)) {
                    class_2561 clean=class_2561.class_2562.method_10872(json);
                    if(clean!=null)translated=clean;
                }
            }
            NameLocalizer.Name bountyName=kind.equals("bounty_place_title")?bountyPlaceName(original.getString()):null;
            boolean bountyAlreadyBilingual=bilingualPlaces&&bountyName!=null&&TranslationEngine.normalize(original.getString()).endsWith("（"+bountyName.english()+"）");
            if(bountyAlreadyBilingual)translated=original;
            // Monster health nameplates must not annotate ordinary words as NPCs/places.
            // Player identity protection has already returned before this point.
            boolean monsterPlate=(kind.equals("entity_label")||kind.equals("hologram_label")||kind.equals("text_display"))
                &&original.getString().matches("(?s)[^\n]+\n[«<]?[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*/\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?[»>]?");
            boolean instanceLabel=(kind.equals("entity_label")||kind.equals("hologram_label")||kind.equals("text_display"))&&(HologramLayout.chineseOnly(original.getString())||knownPlayerTitle(original,kind));
            if (!monsterPlate && !((kind.equals("bossbar")||kind.equals("actionbar"))&&original.getString().contains("Kaul")) && !bountyAlreadyBilingual && !centralPrefix && !serverMessage && !kind.equals("sign") && !kind.equals("item_name") && !kind.equals("class_tooltip") && !kind.equals("alchemist_tooltip") && !kind.equals("alchemist_title") && !kind.equals("shaman_title") && !kind.equals("shaman_tooltip") && !instanceLabel && !nameLocalizer.guildIdentity(original.getString())) {
                // Only a standalone ordinary NPC gets its English on a separate row.
                // Fixed holograms and phrases such as "Cinevra's Office" retain English inline.
                translated = formatNames(original,translated,!kind.equals("bounty_place_title")&&(!kind.equals("entity_label")||nameLocalizer.npc(original.getString())==null),kind.equals("tooltip")?(TranslationEngine.normalize(original.getString()).equals("King's Valley items into circus tickets.")?Set.of("White","King's Valley"):original.getString().contains("in the depths of the Jaguartooth Mountains")?Set.of("White","Jaguartooth Mountains"):Set.of("White")):Set.of());
            }
            if(bilingualPlaces&&(kind.equals("text_display")||kind.equals("hologram_label")||kind.equals("entity_label"))) {
                NameLocalizer.Name exact=nameLocalizer.exactPlace(original.getString());
                if(exact!=null&&TranslationEngine.normalize(original.getString()).equals(exact.english()))
                    translated=HologramNameStyle.render(original,exact.chinese());
            }
            // Apply exact item exceptions across display surfaces, including container summaries and hover text.
            JsonElement itemReferences=class_2561.class_2562.method_10868(translated);
            if(itemReferences.isJsonPrimitive()){JsonObject wrapper=new JsonObject();wrapper.add("text",itemReferences);itemReferences=wrapper;}
            if(NameLocalizer.cleanChineseOnlyItemReferences(itemReferences)) {
                class_2561 clean=class_2561.class_2562.method_10872(itemReferences);
                if(clean!=null)translated=clean;
            }
            if (captureEnabled && (!kind.equals("sign") || signCaptureEnabled)) captured.addText(kind.equals("hologram_label")?"entity_label":(kind.equals("alchemist_tooltip")||kind.equals("shaman_tooltip")||kind.equals("class_tooltip"))?"tooltip":(kind.equals("alchemist_title")||kind.equals("shaman_title")||kind.equals("bounty_place_title"))?"item_name":kind, original.getString(), class_2561.class_2562.method_10868(original), translated.getString());
            class_2561 upper=kind.equals("entity_label")&&bilingualNames?createNpcUpper(original):null;
            cached = new DisplayCache(kind,hash,translated,translated!=original,upper);
            class_2561 snapshot=sourceMetadata(original).snapshot();
            if(snapshot!=null)contentCache.put(new DisplayKey(kind,hash,snapshot),cached);
            }
            if(!displayCache.containsKey(original)) {
                while(displayCache.size()>=4096)displayCache.remove(displayOrder.removeFirst());
                displayOrder.addLast(original);
            }
            displayCache.put(original, cached);
        }
        if(enabled&&cached.changed()&&!knownPlayerTitle(original,kind)&&(kind.equals("hologram_label")||kind.equals("text_display"))&&nameLocalizer.npc(original.getString())!=null&&readableNpcUpper(original)==null)
            return chineseSignLine(original,cached.translated());
        return enabled&&cached.changed() ? cached.translated() : original;
    }

    private static class_2561 translateServerComponent(class_2561 original) {
        JsonElement json = class_2561.class_2562.method_10868(original);
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            JsonObject wrapper = new JsonObject(); wrapper.add("text", json); json = wrapper;
        }
        Set<String> protectedNames=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);protectedNames.addAll(ChatMessageFilter.protectedNames(original.getString()));protectedNames.addAll(CapturePrivacy.roleNames(original.getString()));
        boolean questProgress=QuestProgressLayout.isQuestProgress(json);
        boolean changed=rewriteUndergrowthBounty(json,original.getString());
        changed|=BountyDialogue.rewrite(json,original.getString(),engine);
        JsonElement contextual=ContextTranslations.serverCopy(json,original.getString(),engine);
        if(contextual!=null){json=contextual;changed=true;}
        Set<String> translationProtected=protectedNames;
        if(NameLocalizer.hasKimAnnotation(original.getString())) {
            translationProtected=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            translationProtected.addAll(protectedNames);translationProtected.add("Kim");translationProtected.add("Sergeant Kim");
        }
        changed|=SpecializationRewardNotice.rewrite(json,original.getString(),engine);
        changed|=ChatMessageFilter.rewriteVisible(json,engine,translationProtected);
        if(TranslationEngine.normalize(original.getString()).equals("The Treasures of Viridia have washed in with the flow of the ocean. Where in the Isles might they be?")) {
            protectedNames.add("Treasures of Viridia");protectedNames.add("Viridia");
        }
        // Screenshot-audited collapsing-tower bounty only; standalone POIs retain bilingual names.
        if(original.getString().contains("falling in on itself. Today's Bounty asks") || (original.getString().contains("豹牙山脉") && original.getString().contains("早已废弃") && original.getString().contains("悬赏"))) {
            protectedNames.add("Jaguar Tooth Mountains");protectedNames.add("Jaguartooth Mountains");
        }
        changed|=nameLocalizer.rewriteChat(json,original.getString(),bilingualNames,bilingualPlaces,protectedNames);
        changed|=QuestProgressLayout.chineseIgorTitle(json,original.getString());
        changed|=nameLocalizer.chineseDeathMonster(json);
        changed|=QuestProgressLayout.rewrite(json,original.getString(),questProgress);
        if(original.getString().replaceAll("§.", "").matches("^(?:Pyromania|纵火狂) \\[\\d/4\\].*")) changed|=NameLocalizer.cleanPyromaniaReferences(json);
        if (!changed) return original;
        class_2561 translated = class_2561.class_2562.method_10872(json);
        return translated == null ? original : translated;
    }

    public static long displayRevision() { return revision; }
    /** Record original server title updates even when no HUD frame renders that value. */
    public static synchronized void captureCentralTitle(class_2561 original,String kind) {
        if(original==null||!captureEnabled||!eligible()||!(kind.equals("title")||kind.equals("subtitle")))return;
        captured.addText(kind,original.getString(),class_2561.class_2562.method_10868(original),original.getString());
    }
    /** This one server bounty splits its proper name into differently styled Chinese runs. */
    private static boolean rewriteUndergrowthBounty(JsonElement json,String source) {
        if(!source.equals("国王峡谷悬赏任务:我们从一些Axtan僧侣的口中得知，一个位于被称为“林地”的地区内的潮湿地林有着皇室寻求已久的远古苦艾来源。你能找到那片树林吗？"))return false;
        String name=engine.translate("Undergrowth",false);
        if(!name.equals("地下林地"))return false;
        // Never add a global rule for this short fragment: only this complete bounty qualifies.
        JsonObject exact=new JsonObject();exact.addProperty("地林",name.substring(2));
        JsonObject local=new JsonObject();local.add("exact",exact);
        return new TranslationEngine(local).rewriteVisible(json,false);
    }
    /** Four display copies for the approved area-discovery layout; no dictionary or source edits. */
    public static DiscoveryTitle.Content discoveryTitle(class_2561 title,class_2561 subtitle) {
        if(title==null||subtitle==null||!active()||!bilingualPlaces)return null;
        String announcement=TranslationEngine.normalize(title.getString());
        if(!Set.of("Area Discovered","☼ Area Discovered ☼","发现新区域","☼ 发现新区域 ☼","☼发现新区域☼").contains(announcement))return null;
        NameLocalizer.Name place=nameLocalizer.exactPlace(subtitle.getString());
        if(place==null)return null;
        // Keep normal capture bookkeeping; these display copies never replace the packet components.
        display(title,"title");display(subtitle,"subtitle");
        return new DiscoveryTitle.Content(discoveryLine(title,"Area Discovered","gray"),
            discoveryLine(title,"☼ 发现新区域 ☼","white"),
            discoveryLine(subtitle,place.english(),"gold"),discoveryLine(subtitle,place.chinese(),"gold"));
    }
    private static class_2561 discoveryLine(class_2561 original,String label,String color) {
        JsonElement serialized=class_2561.class_2562.method_10868(translatedLine(original,label));
        JsonObject json;
        if(serialized.isJsonObject())json=serialized.getAsJsonObject();
        else {json=new JsonObject();json.add("text",serialized);}
        json.addProperty("color",color);json.addProperty("bold",false);json.addProperty("italic",false);
        json.addProperty("underlined",false);json.addProperty("strikethrough",false);json.addProperty("obfuscated",false);
        return class_2561.class_2562.method_10872(json);
    }
    public static ArrivalTitle.Content arrivalTitle(class_2561 title,class_2561 subtitle) {
        if(title==null||!active()||!bilingualPlaces)return null;
        if(subtitle!=null&&CinematicTitle.valleyChapter(title.getString(),subtitle.getString()))return null;
        String heading=TranslationEngine.normalize(title.getString());
        // An exact character name is not an unfinished place such as "Kaul's Judgment".
        // Real exact place names keep their arrival path even if a catalog also lists an NPC.
        if(nameLocalizer.npc(heading)!=null&&nameLocalizer.exactPlace(heading)==null)return null;
        NameLocalizer.Name place=nameLocalizer.arrivalPlace(title.getString());
        if(place==null) {
            // An ambiguous early prefix is still English, never a guessed Chinese city.
            if(!ArrivalAnimation.isArrivalPrefix(heading))return null;
            display(title,"title");
            return new ArrivalTitle.Content(title,null,null,null);
        }
        display(title,"title");
        if(subtitle!=null)display(subtitle,"subtitle");
        String raw=subtitle==null?"":TranslationEngine.normalize(subtitle.getString());
        boolean stale=ArrivalAnimation.isStaleSubtitle(place.english(),raw);
        if(stale)raw="";
        String full=ArrivalAnimation.completeSubtitle(place.english(),raw);
        class_2561 description=null,englishDescription=null;
        int descriptionVisible=0;
        if(full!=null&&!full.isBlank()) {
            class_2561 styled=subtitle==null||stale?class_2561.class_2562.method_10872(messagePart(full,"gray",false)):subtitle;
            String translated=nameLocalizer.withoutKnownReferences(translateTooltipLine(translatedLine(styled,full)).getString());
            if(translated.equals(full)&&!full.equals(raw)) {full=raw;translated=raw;}
            description=titleWeight(translatedLine(styled,translated));
            if(!full.isBlank()&&!full.equals(translated))englishDescription=discoveryLine(styled,full,"gray");
            descriptionVisible=ArrivalAnimation.visible(full,raw);
        }
        return new ArrivalTitle.Content(translatedLine(title,place.english()),titleWeight(translatedLine(title,place.chinese())),
            description,englishDescription,ArrivalAnimation.visible(place.english(),heading),descriptionVisible);
    }
    /** Complete boss/chapter/result pairs use four rows; unknown or animated fragments keep the native path. */
    public static CinematicTitle.Content cinematicTitle(class_2561 title,class_2561 subtitle) {
        if(title==null||!active())return null;
        String heading=TranslationEngine.normalize(title.getString()),detail=subtitle==null?"":TranslationEngine.normalize(subtitle.getString());
        boolean conquestPair=heading.equals("Congratulations!")&&detail.matches("You have conquered .+[!.]");
        boolean completion=heading.endsWith(" Complete")&&detail.equals("Congratulations!")
            &&nameLocalizer.exactPlace(heading.substring(0,heading.length()-" Complete".length()))!=null;
        if(!bilingualPlaces&&!conquestPair&&!completion)return null;
        // Recognised Masked Man introduction keeps its final layout while the server types its subtitle.
        if(bilingualNames&&heading.equals("The Masked Man")&&"Shard Bearer".startsWith(detail)) {
            NameLocalizer.Name boss=nameLocalizer.npc(heading);
            if(boss!=null) {
                class_2561 cn=titleWeight(translatedLine(title,boss.chinese()));
                class_2561 partial=detail.isBlank()?null:discoveryLine(subtitle,detail,"gray");
                class_2561 description=detail.equals("Shard Bearer")?titleWeight(translateTooltipLine(subtitle)):null;
                return new CinematicTitle.Content(title,cn,description,partial);
            }
        }
        if(subtitle==null)return null;
        // Congratulations stays primary; complete conquest subtitles get separate translations.
        if(conquestPair) {
            class_2561 translatedTitle=translateTooltipLine(title);
            class_2561 translatedSubtitle=translateTooltipLine(subtitle);
            String chinese=nameLocalizer.withoutKnownReferences(translatedTitle.getString());
            String description=nameLocalizer.withoutKnownReferences(translatedSubtitle.getString());
            if(!completeChinese(chinese)||!completeChinese(description.replace("P.O.R.T.A.L.","")))return null;
            display(title,"title");display(subtitle,"subtitle");
            return new CinematicTitle.Content(null,translatedLine(translatedTitle,chinese),
                translatedLine(translatedSubtitle,description),null);
        }
        // A known place followed by this complete result pair is a location notice,
        // independent of the NPC-name switch. Partial or unpaired packets still fall back.

        if(!bilingualNames&&!completion)return null;
        if(incompleteCentralSubtitle(detail))return null;
        boolean chapter=heading.matches("Tale [0-9]+ Complete")||heading.equals("The Celsian Isles")||CinematicTitle.valleyChapter(heading,detail);
        boolean event=Set.of("VICTORY","Victory!","DEFEAT","Defeat!","XENOTOPSIS").contains(heading);
        if(!chapter&&!event&&!completion&&nameLocalizer.npc(heading)==null)return null;
        if(detail.isBlank()||!heading.matches(".*[A-Za-z].*")||!detail.matches(".*[A-Za-z].*"))return null;
        class_2561 translatedTitle=translateTooltipLine(title),translatedSubtitle=translateTooltipLine(subtitle);
        String chinese=nameLocalizer.withoutKnownReferences(translatedTitle.getString());
        String description=nameLocalizer.withoutKnownReferences(translatedSubtitle.getString());
        if(!completeChinese(chinese)||!completeChinese(description))return null;
        display(title,"title");display(subtitle,"subtitle");
        if(completion)return new CinematicTitle.Content(null,translatedLine(translatedTitle,chinese),translatedLine(translatedSubtitle,description),null);
        return new CinematicTitle.Content(title,titleWeight(chinese.equals(translatedTitle.getString())?translatedTitle:translatedLine(translatedTitle,chinese)),
            titleWeight(description.equals(translatedSubtitle.getString())?translatedSubtitle:translatedLine(translatedSubtitle,description)),discoveryLine(subtitle,detail,"gray"));
    }
    /** Only known typewriter subtitles: short menu words must not translate unfinished sentences. */
    private static boolean incompleteCentralSubtitle(String text) {
        String raw=TranslationEngine.normalize(text);
        if(raw.isEmpty())return false;
        for(String full:List.of("Captain of Death","Good.","Now. A more active task.","Shard Bearer","Keeper of the Pass"))
            if(full.length()>raw.length()&&full.startsWith(raw))return true;
        return false;
    }
    /** Weight applies to rendered title copies only, preserving packet text and every original style. */
    private static class_2561 titleWeight(class_2561 source) {
        JsonElement copy=class_2561.class_2562.method_10868(source).deepCopy();
        if(copy.isJsonPrimitive()){JsonObject root=new JsonObject();root.add("text",copy);copy=root;}
        titleWeightJson(copy);
        return class_2561.class_2562.method_10872(copy);
    }
    private static void titleWeightJson(JsonElement value) {
        if(value.isJsonArray()){for(JsonElement child:value.getAsJsonArray())titleWeightJson(child);return;}
        if(!value.isJsonObject())return;
        JsonObject object=value.getAsJsonObject();object.addProperty("bold",true);
        if(object.has("extra"))titleWeightJson(object.get("extra"));
    }
    private static boolean completeChinese(String value) {
        return value.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)
            &&!value.matches(".*[A-Za-z].*");
    }
    private static synchronized void invalidateDisplays() { displayCache.clear(); displayOrder.clear(); contentCache.clear(); signCache.clear(); sourceMetadata.clear(); metadataOrder.clear(); sourceContents.clear(); ++revision; }

    /** Book readers and lecterns render copies; editing, signing and original page data are untouched. */
    public static net.minecraft.class_5348 displayBookPage(net.minecraft.class_5348 original) {
        if(original==null||!eligible())return original;
        class_2561 text=original instanceof class_2561 component?component:class_2561.method_43470(original.getString());
        class_2561 translated=display(text,"book_page");
        return translated==text?original:translated;
    }

    /** Snapshot the native filtered source strings; page parsing runs later, one page per tick. */
    private static boolean captureBookPages(class_1799 stack) {
        if(!(stack.method_7909() instanceof net.minecraft.class_1843))return true;
        class_2487 nbt=stack.method_7969();
        if(nbt==null||!nbt.method_10573("pages",9))return true;
        List<String> pages=new ArrayList<>();
        if(net.minecraft.class_1843.method_8053(nbt))net.minecraft.class_3872.method_33888(nbt,pages::add);
        else {
            var page=new net.minecraft.class_3872.class_3933(stack).method_17563(0);
            class_2561 text=page instanceof class_2561 component?component:class_2561.method_43470(page.getString());
            pages.add(class_2561.class_2562.method_10867(text));
        }
        return captureWork.book(pages);
    }

    private static void captureBookPage(String raw) {
        if(captured.hasBookComponent(raw))return;
        class_2561 original=null;
        try{original=class_2561.class_2562.method_10877(raw);}catch(Exception ignored){}
        if(original==null)original=class_2561.method_43470(raw);
        if(captured.hasBookPage(original.getString()))return;
        class_2561 translated=formatNames(original,translateBookComponent(original),true);
        captured.addText("book_page",original.getString(),class_2561.class_2562.method_10868(original),translated.getString());
    }

    static class_2561 formatNames(class_2561 original,class_2561 translated,boolean includeNpcs) {
        return formatNames(original,translated,includeNpcs,Set.of());
    }
    private static class_2561 formatNames(class_2561 original,class_2561 translated,boolean includeNpcs,Set<String> protectedNames) {
        String source = TranslationEngine.normalize(original.getString());
        // A complete bilingual title already identifies its names; keep one full original annotation.
        if (!source.isBlank() && translated.getString().endsWith("（" + source + "）")) return translated;
        JsonElement json=class_2561.class_2562.method_10868(translated);
        if(json.isJsonPrimitive()){JsonObject wrapper=new JsonObject();wrapper.add("text",json);json=wrapper;}
        if(!nameLocalizer.rewrite(json,original.getString(),includeNpcs&&bilingualNames,bilingualPlaces,protectedNames))return translated;
        class_2561 result=class_2561.class_2562.method_10872(json);
        return result==null?translated:result;
    }

    /** Ordinary NPCs can get an upper row; the renderer checks space before drawing it. */
    public static synchronized class_2561 npcNameAbove(class_2561 original) {
        if(original==null||!active()||!bilingualNames||playerLabel(original))return null;
        DisplayCache cached=displayCache.get(original);
        // The matching display cache already passed the metadata guard. Online identity changes still win.
        if(cached!=null&&cached.kind().equals("entity_label")&&cached.hash()==original.hashCode()&&!nameLocalizer.playerIdentity(original.getString()))return readableNpcUpper(cached.upper());
        if(cached==null||!cached.kind().equals("entity_label")||cached.hash()!=original.hashCode()) {
            display(original,"entity_label");cached=displayCache.get(original);
        }
        return cached==null?null:readableNpcUpper(cached.upper());
    }
    /** Read the glyph geometry already selected by Minecraft; a flattened font gets no extra row. */
    private static class_2561 readableNpcUpper(class_2561 text) {
        if(text==null)return null;
        var font=(monumentazh.mixin.TextRendererFontAccessor)class_310.method_1551().field_1772;
        boolean[] readable={true};
        text.method_27658((style,value)->{
            var storage=font.monumentazh$font(style.method_27708());
            value.codePoints().filter(Character::isLetter).forEach(c->{
                var glyph=(monumentazh.mixin.BakedGlyphBoundsAccessor)storage.method_2014(c);
                if(glyph.monumentazh$maxY()-glyph.monumentazh$minY()<=1)readable[0]=false;
            });return Optional.empty();
        },net.minecraft.class_2583.field_24360);
        return readable[0]?text:null;
    }
    /** Known player-title displays do not use NPC annotations; entity identity remains protected. */
    private static boolean knownPlayerTitle(class_2561 original,String kind) {
        if(!TranslationEngine.normalize(original.getString()).equals("Tourist")&&!nameLocalizer.playerTitleLabel(original.getString())&&!nameLocalizer.cosmeticTitle(original.getString()))return false;
        if(kind.equals("text_display"))return true;
        if(!kind.equals("entity_label")&&!kind.equals("hologram_label"))return false;
        JsonElement value=class_2561.class_2562.method_10868(original);
        if(!value.isJsonObject())return false;
        JsonObject root=value.getAsJsonObject();
        if(!root.has("hoverEvent")||!root.get("hoverEvent").isJsonObject())return false;
        JsonObject hover=root.getAsJsonObject("hoverEvent");
        if(!hover.has("action")||!hover.get("action").getAsString().equals("show_entity")||!hover.has("contents")||!hover.get("contents").isJsonObject())return false;
        JsonObject content=hover.getAsJsonObject("contents");
        return content.has("type")&&content.get("type").getAsString().equals("minecraft:text_display");
    }
    private static boolean playerLabel(class_2561 original) {
        if(nameLocalizer.playerTitleLabel(original.getString()))return true;
        SourceMetadata metadata=sourceMetadata(original);
        return metadata.player()||nameLocalizer.playerIdentity(original.getString())&&!metadata.nonPlayer();
    }
    private static class_2561 createNpcUpper(class_2561 original) {
        if(HologramLayout.chineseOnly(original.getString())||knownPlayerTitle(original,"entity_label"))return null;
        NameLocalizer.Name npc=nameLocalizer.npc(original.getString());
        if(npc==null)return null;
        class_2561 upper=translatedLine(original,"（"+npc.english()+"）");
        return upper.method_27661().method_10862(upper.method_10866().method_10977(net.minecraft.class_124.field_1080).method_10982(false));
    }

    /** Immutable copies cover both sides and filtered text, without changing sign contents or commands. */
    public static net.minecraft.class_8242 displaySign(net.minecraft.class_8242 original) {
        return displaySign(original,90);
    }
    public static net.minecraft.class_8242 displaySign(net.minecraft.class_8242 original,int maxWidth) {
        if(original==null||!(enabled&&signTranslationEnabled||captureEnabled&&signCaptureEnabled)||!eligible())return original;
        var font=((monumentazh.mixin.TextRendererFontAccessor)class_310.method_1551().field_1772).monumentazh$font(net.minecraft.class_2583.field_24360.method_27708());
        SignLayout cached=signCache.get(original);
        if(cached==null||cached.width()!=maxWidth||cached.font()!=font) {
            net.minecraft.class_8242 result=original;
            boolean creditNames=buildCreditSign(original,false),filteredCreditNames=buildCreditSign(original,true);
            for(int i=0;i<4;i++) {
                class_2561 normal=original.method_49859(i,false),filtered=original.method_49859(i,true);
                // A contributor account is local to this sign, even when a real NPC shares its name.
                class_2561 translated=buildCreditAccount(creditNames,i,normal)?normal:display(normal,"sign");
                class_2561 translatedFiltered=filtered==normal&&creditNames==filteredCreditNames?translated:
                    buildCreditAccount(filteredCreditNames,i,filtered)?filtered:display(filtered,"sign");
                translated=chineseSignLine(normal,translated);
                translatedFiltered=filtered==normal&&creditNames==filteredCreditNames?translated:chineseSignLine(filtered,translatedFiltered);
                if(translated!=normal||translatedFiltered!=filtered)result=result.method_49858(i,translated,translatedFiltered);
            }
            result=lobbyReturnSign(original,result);
            if(enabled&&signTranslationEnabled)result=SignPhraseContext.apply(original,result,maxWidth,engine,class_310.method_1551().field_1772);
            result=compactSign(original,result,maxWidth);
            if(enabled&&signTranslationEnabled)result=ReverieWallLayout.apply(original,result);
            // A weak key must not be retained by its unchanged cached value.
            cached=new SignLayout(maxWidth,font,result==original?null:result);signCache.put(original,cached);
        }
        return enabled&&signTranslationEnabled&&cached.text()!=null?cached.text():original;
    }
    private static final java.util.regex.Pattern CREDIT_ACCOUNT=java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static boolean buildCreditSign(net.minecraft.class_8242 sign,boolean filtered) {
        String heading=TranslationEngine.normalize(sign.method_49859(0,filtered).getString()).toLowerCase(Locale.ROOT)
            .replaceAll("^[^a-z]+|[^a-z]+$","");
        return Set.of("build team","building team","builders","built by","build credits","builder credits","mob design","loot + lore","loot and lore").contains(heading);
    }
    private static boolean buildCreditAccount(boolean credits,int row,class_2561 original) {
        return credits&&row>0&&CREDIT_ACCOUNT.matcher(TranslationEngine.normalize(original.getString())).matches();
    }
    /** Keep the native heading/divider; English needs one quiet row with side margins. */
    private static net.minecraft.class_8242 compactSign(net.minecraft.class_8242 original,net.minecraft.class_8242 translated,int maxWidth) {
        var font=class_310.method_1551().field_1772;
        int margin=font.method_1727("中"),comfortableWidth=maxWidth-2*margin;
        for(boolean filtered:List.of(false,true)) {
            net.minecraft.class_8242 teleport=singleTeleportSign(original,translated,filtered);
            if(teleport!=null){translated=teleport;continue;}
            String header=TranslationEngine.normalize(original.method_49859(0,filtered).getString()).toLowerCase(Locale.ROOT);
            boolean portal=Set.of("related quest:","required quest:","teleport to:","accessed by:").contains(header);
            boolean separator=original.method_49859(1,filtered).getString().strip().matches("[-=]{3,}");
            int start=portal?(separator?2:1):0;
            List<class_2561> chinese=new ArrayList<>(),english=new ArrayList<>();
            for(int i=start;i<4;i++) {
                var raw=ClassMenuTooltip.trim(original.method_49859(i,filtered));
                if(raw.getString().isBlank())continue;
                var zh=ClassMenuTooltip.trim(translated.method_49859(i,filtered));
                if(!raw.getString().matches(".*[A-Za-z].*")||raw.getString().codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)
                    ||zh.getString().matches(".*[A-Za-z].*")||zh.getString().codePoints().noneMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)){english.clear();break;}
                chinese.add(zh);english.add(raw);
            }
            if(english.isEmpty()||!portal&&english.size()!=1)continue;
            var joined=class_2561.method_43473();for(var line:chinese)joined.method_10852(line);
            if(portal&&font.method_27525(joined)<=comfortableWidth)chinese=List.of(joined);
            var originalJoined=class_2561.method_43473();for(var line:english){if(!originalJoined.getString().isEmpty())originalJoined.method_27693(" ");originalJoined.method_10852(line);}
            if(portal) {
                String complete=nameLocalizer.withoutKnownReferences(engine.translate(originalJoined.getString(),false));
                if(!complete.equals(originalJoined.getString())&&complete.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)&&!complete.matches(".*[A-Za-z].*")) {
                    var full=translatedLine(chinese.get(0),complete);
                    if(font.method_27525(full)<=comfortableWidth)chinese=List.of(full);
                }
            }
            if(font.method_27525(originalJoined)<=comfortableWidth)english=List.of(originalJoined);
            boolean bilingual=(portal?bilingualPlaces:bilingualNames||bilingualPlaces)&&chinese.size()==1&&english.size()==1
                &&font.method_27525(chinese.get(0))<=comfortableWidth&&font.method_27525(english.get(0))<=comfortableWidth
                &&font.method_27525(english.get(0))<=font.method_27525(chinese.get(0))+2*margin;
            if(!portal&&!bilingual)continue;
            List<class_2561> rows=new ArrayList<>();
            if(portal) {
                rows.add(translated.method_49859(0,filtered));
                if(separator)rows.add(translated.method_49859(1,filtered));
                else if(chinese.size()<=2)rows.add(translatedLine(original.method_49859(1,filtered),""));
            } else rows.add(translatedLine(original.method_49859(0,filtered),""));
            rows.addAll(chinese);if(bilingual)rows.addAll(english);
            for(int i=0;i<4;i++) {
                var value=i<rows.size()?rows.get(i):translatedLine(original.method_49859(i,filtered),"");
                translated=translated.method_49858(i,filtered?translated.method_49859(i,false):value,filtered?value:translated.method_49859(i,true));
            }
        }
        return translated;
    }
    /** Single destination/prefix boards share the same two row slots, irrespective of text length. */
    public static boolean teleportSignLabel(class_2561 line) {
        if(line==null||nameLocalizer==null)return false;
        String raw=TranslationEngine.normalize(line.getString());
        if(!raw.matches(".*[A-Za-z].*")||raw.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))return false;
        if(raw.equals("Teleport:")||raw.equals("Teleport :"))return true;
        if(raw.startsWith("Teleport:"))raw=raw.substring(9).strip();
        else if(raw.startsWith("Teleport :"))raw=raw.substring(10).strip();
        else if(raw.startsWith("To "))raw=raw.substring(3).strip();
        return nameLocalizer.arrivalPlace(raw)!=null;
    }
    private static net.minecraft.class_8242 singleTeleportSign(net.minecraft.class_8242 original,net.minecraft.class_8242 translated,boolean filtered) {
        if(!enabled||!signTranslationEnabled)return null;
        List<Integer> occupiedRows=new ArrayList<>();
        for(int i=0;i<4;i++)if(!original.method_49859(i,filtered).getString().isBlank())occupiedRows.add(i);
        if(occupiedRows.isEmpty()||occupiedRows.size()>2)return null;
        int occupied=occupiedRows.get(0);
        class_2561 first=original.method_49859(occupied,filtered),raw=first;
        class_2561 chinese=ClassMenuTooltip.trim(translated.method_49859(occupied,filtered));
        if(occupiedRows.size()==2) {
            if(!TranslationEngine.normalize(first.getString()).startsWith("To "))return null;
            var combined=class_2561.method_43473();combined.method_10852(ClassMenuTooltip.trim(first));combined.method_27693(" ");
            combined.method_10852(ClassMenuTooltip.trim(original.method_49859(occupiedRows.get(1),filtered)));raw=combined;
        }
        String joined=TranslationEngine.normalize(raw.getString());
        if(joined.startsWith("To ")) {
            var destination=nameLocalizer.arrivalPlace(joined.substring(3).strip());
            if(destination==null)return null;
            // 'To' is directional only in this known-place sign context. Mail rules stay intact.
            String whole=nameLocalizer.withoutKnownReferences(engine.translate(joined,false));
            if(whole.equals(joined)||whole.startsWith("收件人"))whole="前往"+destination.chinese();
            chinese=translatedLine(first,whole);
        }
        if(!teleportSignLabel(raw)||CapturePrivacy.playerComponent(class_2561.class_2562.method_10868(raw))
            ||chinese.getString().codePoints().noneMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))return null;
        net.minecraft.class_8242 result=translated;
        for(int i=0;i<4;i++) {
            class_2561 value=translatedLine(original.method_49859(i,filtered),"");
            if(bilingualPlaces) {if(i==1)value=chinese;else if(i==2)value=ClassMenuTooltip.trim(raw);}
            else if(i==occupied)value=chinese;
            result=result.method_49858(i,filtered?result.method_49859(i,false):value,filtered?value:result.method_49859(i,true));
        }
        return result;
    }
    private static class_2561 chineseSignLine(class_2561 original,class_2561 translated) {
        String plain=nameLocalizer.withoutKnownReferences(translated.getString());
        return plain.equals(translated.getString())?translated:translatedLine(original,plain);
    }
    /** This sentence spans three server rows; translating "to the" alone loses its context. */
    private static net.minecraft.class_8242 lobbyReturnSign(net.minecraft.class_8242 original,net.minecraft.class_8242 translated) {
        for(boolean filtered:List.of(false,true)) {
            String sentence=TranslationEngine.normalize(original.method_49859(0,filtered).getString()+" "+original.method_49859(1,filtered).getString()+" "+original.method_49859(2,filtered).getString());
            if(!sentence.equals("Teleport back to the Dungeon Lobby."))continue;
            class_2561[] rows={translatedLine(original.method_49859(0,filtered),""),translatedLine(original.method_49859(0,filtered),"传送并返回"),translatedLine(original.method_49859(2,filtered),"地牢大厅")};
            for(int i=0;i<3;i++)translated=translated.method_49858(i,filtered?translated.method_49859(i,false):rows[i],filtered?rows[i]:translated.method_49859(i,true));
        }
        return translated;
    }
    private static void setEnabled(boolean value) { enabled = value; invalidateDisplays(); saveSettings(); }
    private static void setCaptureEnabled(boolean value) { captureEnabled = value; invalidateDisplays(); saveSettings(); }
    private static void setSignTranslationEnabled(boolean value) { signTranslationEnabled=value; invalidateDisplays(); saveSettings(); }
    private static void setSignCaptureEnabled(boolean value) { signCaptureEnabled=value; invalidateDisplays(); saveSettings(); }

    private static void loadSettings() {
        Path path = configDir.resolve("settings.json");
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject settings = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if(settings.has("introduction_display_count"))introduction.shownCount(settings.get("introduction_display_count").getAsInt());
            if (settings.has("enabled")) enabled = settings.get("enabled").getAsBoolean();
            if (settings.has("capture_enabled")) captureEnabled = settings.get("capture_enabled").getAsBoolean();
            if (settings.has("sign_translation_enabled")) signTranslationEnabled = settings.get("sign_translation_enabled").getAsBoolean();
            if (settings.has("sign_capture_enabled")) signCaptureEnabled = settings.get("sign_capture_enabled").getAsBoolean();
            if (settings.has("bilingual_names")) bilingualNames = settings.get("bilingual_names").getAsBoolean();
            if (settings.has("bilingual_places")) bilingualPlaces = settings.get("bilingual_places").getAsBoolean();
        } catch (Exception ex) { System.err.println("[MonumentaZH] Settings load failed: " + ex.getMessage()); }
    }

    private static void saveSettings() {
        try {
            JsonObject settings = new JsonObject();
            settings.addProperty("introduction_display_count",introduction.shownCount());
            settings.addProperty("enabled", enabled);
            settings.addProperty("capture_enabled", captureEnabled);
            settings.addProperty("sign_translation_enabled", signTranslationEnabled);
            settings.addProperty("sign_capture_enabled", signCaptureEnabled);
            settings.addProperty("bilingual_names", bilingualNames);
            settings.addProperty("bilingual_places", bilingualPlaces);
            EncounteredTextStore.atomicWrite(configDir.resolve("settings.json"), GSON.toJson(settings));
        } catch (IOException ex) { System.err.println("[MonumentaZH] Settings save failed: " + ex.getMessage()); }
    }

    private static void checkpoint() {
        try { captured.checkpoint(configDir.resolve("encountered_text.json")); }
        catch (IOException ex) { System.err.println("[MonumentaZH] Capture save failed: " + ex.getMessage()); }
    }

    private static class_2561 translateHudComponent(class_2561 message) {
        JsonElement json = class_2561.class_2562.method_10868(message);
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            JsonObject wrapped = new JsonObject();
            wrapped.add("text", json);
            json = wrapped;
        }
        if (!hudTranslator.rewrite(json, message.getString())) return message;
        class_2561 translated = class_2561.class_2562.method_10872(json);
        return translated == null ? message : translated;
    }

    private static class_2561 translateComponent(class_2561 text) {
        return translateComponent(text, true);
    }

    private static final Map<String,TranslationEngine> alchemistLines=new HashMap<>();
    private static class_2561 translateAlchemistComponent(class_2561 original) {
        TranslationEngine local=alchemistLines.get(TranslationEngine.normalize(original.getString()));
        if(local==null)return translateTooltipLine(original);
        JsonElement json=class_2561.class_2562.method_10868(original);
        local.rewriteVisible(json,false);engine.rewriteVisible(json,true);
        return class_2561.class_2562.method_10872(json);
    }

    private static class_2561 translateShamanComponent(class_2561 original) {
        class_2561 result=ShamanTooltip.translate(original,engine);
        return result==null?translateTooltipLine(original):result;
    }

    private static class_2561 translateBookComponent(class_2561 original) {
        JsonElement json=class_2561.class_2562.method_10868(original);
        if(json.isJsonPrimitive()){JsonObject wrapper=new JsonObject();wrapper.add("text",json);json=wrapper;}
        boolean scoped=ContextTranslations.bookRewrite(json,original.getString(),engine);
        boolean global=engine.rewriteBookVisible(json);
        if(!scoped&&!global)return original;
        class_2561 result=class_2561.class_2562.method_10872(json);
        return result==null?original:result;
    }

    private static class_2561 translateTooltipLine(class_2561 original) {
        if (original != null && TranslationEngine.hasHiddenText(class_2561.class_2562.method_10868(original))) return original;
        class_2561 styledExact=StyledEquipmentTranslations.apply(original,engine);
        if(styledExact!=null)return styledExact;
        class_2561 questTitle=ContextTranslations.questTitle(original,engine);
        if(questTitle!=null)return questTitle;
        // A tagged guild label is one identity; its colored leaves can collide with skills or enchantments.
        if (nameLocalizer.guildIdentity(original.getString())||original.getString().startsWith("by ")&&(nameLocalizer.npc(original.getString().substring(3))!=null||CapturePrivacy.playerName(original.getString().substring(3)))) return original;
        Set<String> protectedNames=CapturePrivacy.roleNames(original.getString());
        class_2561 structured;
        if(protectedNames.isEmpty())structured=translateComponent(original);
        else {
            JsonElement json=class_2561.class_2562.method_10868(original);
            if(json.isJsonPrimitive()){JsonObject wrapper=new JsonObject();wrapper.add("text",json);json=wrapper;}
            structured=ChatMessageFilter.rewriteVisible(json,engine,protectedNames)?class_2561.class_2562.method_10872(json):original;
        }
        String result = engine.translatePreserving(original.getString(), false, protectedNames);
        // Keep colors, keybinds and numeric formatting when component-wise translation is complete.
        if (result.equals(original.getString()) || sameVisibleWords(structured.getString(), result)) {
            return structured;
        }
        if (hasKeybind(class_2561.class_2562.method_10868(original))) return structured;
        // Count rules may recognize the rendered name of a native item. Keep its translation key.
        if(QuantityText.captureKey(TranslationEngine.normalize(original.getString()))!=null
            &&hasNativeTranslation(class_2561.class_2562.method_10868(original)))return structured;
        return translatedTooltipLine(original, result);
    }

    private static final java.util.regex.Pattern VALUE_RUN=java.util.regex.Pattern.compile("[-+]?\\d+(?:\\.\\d+)?%?|[⭐🗡🏹▶✓✗]");
    private static final java.util.regex.Pattern COUNTED_NAME=java.util.regex.Pattern.compile("^(\\d+\\s+)(.+?)(\\s+\\(\\d+ (?:total|in wallet)\\))?$");
    private static final java.util.regex.Pattern COUNTED_PREFIX=java.util.regex.Pattern.compile("^\\d+\\s*");
    /** Whole-sentence corrections keep the original styles of numbers and native damage icons. */
    static class_2561 translatedTooltipLine(class_2561 original,String result) {
        List<net.minecraft.class_2583> styles=new ArrayList<>();
        original.method_27658((style,value)->{for(int i=0;i<value.length();i++)styles.add(style);return Optional.empty();},net.minecraft.class_2583.field_24360);
        String raw=original.getString();
        // This market instruction has one highlighted key label between two connective runs.
        if(raw.equals("of your Hotbar keys to")) {
            String label=engine.translate("Hotbar keys",false);int at=result.indexOf(label),start=raw.indexOf("Hotbar keys");
            var keyStyle=uniformStyle(styles,raw,start,start+"Hotbar keys".length());
            if(at>=0&&keyStyle!=null)return styledTranslation(styles.get(0),result.substring(0,at))
                .method_10852(styledTranslation(keyStyle,label))
                .method_10852(styledTranslation(styles.get(styles.size()-1),result.substring(at+label.length())));
        }
        // Region and rarity are separate native runs, even when the exact phrase includes an English name.
        int divider=raw.indexOf(" : "),translatedDivider=result.lastIndexOf('：');
        if(divider>0&&translatedDivider>0&&raw.indexOf(" : ",divider+3)<0) {
            var labelStyle=uniformStyle(styles,raw,0,divider+1);
            var valueStyle=uniformStyle(styles,raw,divider+3,raw.length());
            if(labelStyle!=null&&valueStyle!=null)return styledTranslation(labelStyle,result.substring(0,translatedDivider+1))
                .method_10852(styledTranslation(valueStyle,result.substring(translatedDivider+1)));
        }
        // Wallet headings retain the item's own style between the native count and total.
        var count=COUNTED_NAME.matcher(raw);var translatedCount=COUNTED_PREFIX.matcher(result);
        if(count.matches()&&translatedCount.find()) {
            int tail=result.lastIndexOf('（');if(tail<0)tail=result.lastIndexOf('(');
            var nameStyle=uniformStyle(styles,raw,count.start(2),count.end(2));
            var tailStyle=count.group(3)==null?null:uniformStyle(styles,raw,count.start(3),raw.length());
            if(nameStyle!=null&&(count.group(3)==null&&tail<0||tailStyle!=null&&tail>=translatedCount.end())) {
                var title=styledTranslation(styles.get(0),result.substring(0,translatedCount.end()));
                title.method_10852(styledTranslation(nameStyle,result.substring(translatedCount.end(),tail<0?result.length():tail)));
                if(tail>=0)title.method_10852(styledTranslation(tailStyle,result.substring(tail)));
                return title;
            }
        }
        Map<String,ArrayDeque<net.minecraft.class_2583>> values=new HashMap<>();
        var source=VALUE_RUN.matcher(raw);var valuePositions=new BitSet(raw.length());
        while(source.find()){values.computeIfAbsent(source.group().replaceFirst("^[+-]",""),k->new ArrayDeque<>()).add(styles.get(source.start()));valuePositions.set(source.start(),source.end());}
        net.minecraft.class_2583 proseStyle=null;boolean uniformProse=true;
        for(int i=0;i<raw.length();i++)if(!valuePositions.get(i)&&!Character.isWhitespace(raw.charAt(i))) {
            if(proseStyle==null)proseStyle=styles.get(i);else if(!proseStyle.equals(styles.get(i))){uniformProse=false;break;}
        }
        class_2561 prose=uniformProse&&proseStyle!=null?class_2561.method_43470("").method_10862(proseStyle):original;
        var output=class_2561.method_43473();var target=VALUE_RUN.matcher(result);int position=0;
        while(target.find()) {
            output.method_10852(translatedLine(prose,result.substring(position,target.start())));
            var donors=values.get(target.group().replaceFirst("^[+-]",""));
            output.method_10852(class_2561.method_43470(target.group()).method_10862(donors==null||donors.isEmpty()?original.method_10866():donors.removeFirst()));
            position=target.end();
        }
        return output.method_10852(translatedLine(prose,result.substring(position)));
    }

    private static net.minecraft.class_2583 uniformStyle(List<net.minecraft.class_2583> styles,String text,int start,int end) {
        net.minecraft.class_2583 found=null;
        for(int i=start;i<end;i++)if(!Character.isWhitespace(text.charAt(i))) {
            if(found==null)found=styles.get(i);else if(!found.equals(styles.get(i)))return null;
        }
        return found;
    }
    private static net.minecraft.class_5250 styledTranslation(net.minecraft.class_2583 style,String text) {
        return translatedLine(class_2561.method_43470("").method_10862(style),text).method_27661();
    }

    private static boolean sameVisibleWords(String left, String right) {
        return comparableText(left).equals(comparableText(right));
    }
    private static String comparableText(String text) {
        return TranslationEngine.normalize(text).replace('（', '(').replace('）', ')').replace('：', ':').replaceAll("\\s+", "");
    }
    private static boolean hasKeybind(JsonElement json) {
        if (json.isJsonArray()) { for (JsonElement child : json.getAsJsonArray()) if (hasKeybind(child)) return true; }
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("keybind")||object.has("hoverEvent")||object.has("clickEvent")) return true;
            if (object.has("extra")) return hasKeybind(object.get("extra"));
        }
        return false;
    }
    private static boolean hasNativeTranslation(JsonElement json) {
        if(json.isJsonArray()){for(JsonElement child:json.getAsJsonArray())if(hasNativeTranslation(child))return true;}
        if(json.isJsonObject()){
            JsonObject object=json.getAsJsonObject();if(object.has("translate"))return true;
            for(String key:List.of("extra","with"))if(object.has(key)&&hasNativeTranslation(object.get(key)))return true;
        }
        return false;
    }

    static class_2561 translatedLine(class_2561 original, String translated) {
        JsonElement serialized = class_2561.class_2562.method_10868(
            class_2561.method_43470("").method_10862(original.method_10866()));
        JsonObject json = serialized.isJsonObject() ? serialized.getAsJsonObject() : new JsonObject();
        TranslationEngine.replaceTranslatedText(json, translated);
        class_2561 result = class_2561.class_2562.method_10872(json);
        return result == null ? original : result;
    }

    private static class_2561 translateComponent(class_2561 text, boolean partial) {
        if (text != null && TranslationEngine.hasHiddenText(class_2561.class_2562.method_10868(text))) return text;
        JsonElement json = class_2561.class_2562.method_10868(text);
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            JsonObject wrapped = new JsonObject();
            wrapped.add("text", json);
            json = wrapped;
        }
        if (!engine.rewriteVisible(json, partial)) return text;
        class_2561 result = class_2561.class_2562.method_10872(json);
        return result == null ? text : result;
    }

    private static void reload() {
        try (InputStream stream = MonumentaZh.class.getResourceAsStream("/monumenta_zh.json")) {
            if (stream == null) throw new IOException("内置词库缺失");
            JsonObject dictionary = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            alchemistLines.clear();
            if(dictionary.has("alchemist_lines"))for(var entry:dictionary.getAsJsonObject("alchemist_lines").entrySet()) {
                JsonObject local=new JsonObject();local.add("exact",entry.getValue());
                alchemistLines.put(TranslationEngine.normalize(entry.getKey()),new TranslationEngine(local));
            }
            TranslationEngine replacement = new TranslationEngine(dictionary);
            Path overrides = configDir.resolve("overrides.json");
            if (Files.isRegularFile(overrides)) {
                try (Reader reader = Files.newBufferedReader(overrides, StandardCharsets.UTF_8)) {
                    JsonObject custom = JsonParser.parseReader(reader).getAsJsonObject();
                    replacement.add(CapturePrivacy.runtimeDictionary(custom));
                }
            }
            engine = replacement;
            hudTranslator = new HudTranslator(replacement, dictionary);
            try(InputStream names=MonumentaZh.class.getResourceAsStream("/monumenta_names.json")) {
                nameLocalizer=new NameLocalizer(names==null?new JsonObject():JsonParser.parseReader(new InputStreamReader(names,StandardCharsets.UTF_8)).getAsJsonObject());
            }
            Path customNames=configDir.resolve("names.json");
            if(Files.isRegularFile(customNames))nameLocalizer.add(JsonParser.parseString(Files.readString(customNames,StandardCharsets.UTF_8)).getAsJsonObject());
            nameLocalizer.fillMissingTranslations(replacement);
            invalidateDisplays();
        } catch (Exception ex) { System.err.println("[MonumentaZH] Dictionary load failed: " + ex.getMessage()); }
    }

    private static void capture(class_1799 stack, String screenTitle) {
        if (stack.method_7960()) return;
        captureWork.request(stack,screenTitle);
    }

    private static boolean captureNow(class_1799 stack,String screenTitle) {
        if(stack.method_7960())return true;
        if(!captureBookPages(stack))return false;
        class_2487 nbt = stack.method_7969();
        if (nbt == null || !nbt.method_10545("display")) return true;
        String name=stack.method_7964().getString();
        ItemCaptureKey key=new ItemCaptureKey(name,nbt.method_10562("display"),nbt.method_10562("Gui"));
        if(itemCaptureCache.get(key)!=null)return true;
        JsonObject item = new JsonObject();
        item.addProperty("screen", screenTitle);
        item.addProperty("name", stack.method_7964().getString());
        item.add("name_component", class_2561.class_2562.method_10868(stack.method_7964()));
        if (nbt.method_10545("Gui")) item.addProperty("gui_texture", nbt.method_10562("Gui").method_10558("texture"));
        JsonArray lore = new JsonArray();
        JsonArray loreComponents = new JsonArray();
        class_2499 list = nbt.method_10562("display").method_10554("Lore", 8);
        for (int i = 0; i < list.size(); i++) {
            class_2561 line = class_2561.class_2562.method_10877(list.method_10608(i));
            if (line != null) {
                lore.add(line.getString());
                loreComponents.add(class_2561.class_2562.method_10868(line));
            }
        }
        item.add("lore", lore);
        item.add("lore_components", loreComponents);
        captured.addItem(item);
        itemCaptureCache.put(new ItemCaptureKey(name,key.display().method_10553(),key.gui().method_10553()),true);
        return true;
    }
}
