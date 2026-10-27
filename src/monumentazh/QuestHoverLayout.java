package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Bilingual layout for complete server quest help cards; ordinary text and saved settings are untouched. */
public final class QuestHoverLayout {
    private static final Pattern CARD=Pattern.compile("^([^\\n]+)\\nDifficulty[ \\t]*:[ \\t]*([^\\n]+)\\nRequirements[ \\t]*:[ \\t]*(.+?)\\nStart[ \\t]*:[ \\t]*([^\\n]+)(?:\\n(.*))?$",Pattern.DOTALL);
    private static final Pattern COMPLETED=Pattern.compile("^Completed the (.+?) Dungeon(?: and speak to (.+?) afterwards\\.)?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LOBBY=Pattern.compile("^(.+?) in the (.+?) lobby$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,String> COLORS=Map.ofEntries(Map.entry("white","白色"),Map.entry("orange","橙色"),Map.entry("magenta","品红"),Map.entry("light blue","淡蓝色"),Map.entry("yellow","黄色"),Map.entry("lime","黄绿色"),Map.entry("pink","粉红色"),Map.entry("gray","灰色"),Map.entry("light gray","淡灰色"),Map.entry("cyan","青色"),Map.entry("purple","紫色"),Map.entry("blue","蓝色"),Map.entry("brown","棕色"),Map.entry("green","绿色"),Map.entry("red","红色"),Map.entry("black","黑色"));
    private static final Map<String,String> LEVELS=Map.ofEntries(Map.entry("beginner","入门"),Map.entry("easy","简单"),Map.entry("medium","中等"),Map.entry("medium-hard","中等偏难"),Map.entry("hard","困难"),Map.entry("very hard","极难"),Map.entry("challenging","挑战"),Map.entry("expert","专家"),Map.entry("insane","疯狂"));
    private static final Map<String,String> FOOTERS=Map.of("* Requirements not yet met *","* 前置条件尚未满足 *","* Requirements not yet met *'","* 前置条件尚未满足 *'","* Dungeon Quest *","* 地牢任务 *","* Mainline Quest *","* 主线任务 *");
    private static final Map<String,String> START_LOCATIONS=Map.ofEntries(
        Map.entry(" in the teleporter building.","传送楼内的 "),Map.entry(" near the Sierhaven Circle.","希尔海文城环街附近的 "),
        Map.entry(" in the Eastern Sierhaven housing district.","希尔海文城东部住宅区内的 "),Map.entry(" in the Apartments.","公寓内的 "),
        Map.entry(" in the Sierhaven Apartments.","希尔海文城公寓内的 "),Map.entry(" out in the Swamp","沼泽中的 "),
        Map.entry(" on the third floor of the Teleporter Hub.","传送枢纽三层的 "),Map.entry(", in the Arena of Terth","特尔斯竞技场内的 "),
        Map.entry(" in the Memory Vault","记忆密库中的 "),Map.entry(" in the Oceangate Lighthouse","海洋之门灯塔内的 "),
        Map.entry(" in the Northern Tower of Oceangate","海洋之门北塔内的 "),Map.entry(" in the cave behind Oceangate","海洋之门后方洞穴内的 "),
        Map.entry(" on The Black Mist","“黑雾”号上的 "),Map.entry(" in Mistport","雾港中的 "),
        Map.entry(" in her shop near the Sierhaven Plaza.","希尔海文城广场附近店铺内的 "),Map.entry(" near the Sierhaven apartments entrance.","希尔海文城公寓入口附近的 "),
        Map.entry(" in his Academy office.","学院办公室内的 "),Map.entry(" at the northern entrance of Sierhaven","希尔海文城北入口的 "),
        Map.entry(" in his house near Sierhaven Plaza","希尔海文城广场附近家中的 "));
    private static final List<String> STYLES=List.of("color","bold","italic","underlined","strikethrough","obfuscated","font");
    private QuestHoverLayout() {}

    public static boolean rewrite(JsonObject hover,TranslationEngine engine,Set<String> protectedNames) {
        if(!hover.has("contents"))return false;
        JsonElement original=hover.get("contents");
        if(unsupported(original))return false;
        List<JsonObject> runs=new ArrayList<>();collect(original,new JsonObject(),runs);
        StringBuilder joined=new StringBuilder();for(JsonObject run:runs)joined.append(run.get("text").getAsString());
        String full=joined.toString().replace("\r\n","\n");Matcher card=CARD.matcher(full.stripTrailing());
        if(!card.matches())return false;
        // Unknown extra paragraphs may carry instructions: leave their original component structure alone.
        String footer=card.group(5)==null?"":card.group(5).strip();
        if(!footer.isEmpty()&&!FOOTERS.containsKey(footer))return false;
        String title=card.group(1),level=card.group(2),requirements=card.group(3),start=card.group(4);
        // A card requires all three fields in order and a standalone title, never an incidental sentence.
        if(title.isBlank()||title.length()>180||level.isBlank()||start.isBlank())return false;
        JsonObject titleStyle=styleAt(runs,card.start(1)),levelStyle=styleAt(runs,card.start(2)),requirementStyle=styleAt(runs,card.start(3)),startStyle=styleAt(runs,card.start(4));
        JsonArray output=new JsonArray();
        String translated=local(title,engine,protectedNames);
        add(output,title+(translated.equals(title)?"":"（"+translated+"）"),titleStyle);newline(output,2);
        label(output,"难度 / Difficulty：");
        String chineseLevel=LEVELS.getOrDefault(level.toLowerCase(Locale.ROOT),local(level,engine,protectedNames));
        add(output,chineseLevel.equals(level)?level:chineseLevel+"（"+level+"）",levelStyle);newline(output,2);
        label(output,"前置要求 / Requirements：");newline(output,1);
        String all=local(requirements,engine,protectedNames);
        String[] entries=all.equals(requirements)?splitRequirements(requirements):new String[]{requirements};
        for(int i=0;i<entries.length;i++) {
            String rawEntry=entries[i].strip(),english=rawEntry.replaceAll("\\s+"," "),chinese=requirement(english,engine,protectedNames);
            JsonObject own=styleAt(runs,card.start(3)+Math.max(0,requirements.indexOf(rawEntry)));
            pair(output,chinese,english,own);
            if(i+1<entries.length)newline(output,1);
        }
        newline(output,2);label(output,"起始位置 / Start：");newline(output,1);
        Matcher lobby=LOBBY.matcher(start);String chineseStart=start(start,engine,protectedNames);
        if(lobby.matches()) {
            String color=COLORS.get(lobby.group(2).toLowerCase(Locale.ROOT));
            if(color!=null)chineseStart=color+"羊毛大厅内的 "+lobby.group(1);
        }
        pair(output,chineseStart,start,startStyle);
        if(!footer.isEmpty()) {
            newline(output,2);JsonObject footerStyle=styleAt(runs,card.start(5));
            pair(output,FOOTERS.get(footer),footer,footerStyle);
        }
        JsonObject result=new JsonObject();result.addProperty("text","");result.add("extra",output);
        hover.add("contents",result);return true;
    }
    private static String requirement(String english,TranslationEngine engine,Set<String> protectedNames) {
        String normalized=english.replaceFirst("(?i)^and ","").replaceFirst("\\.$","").strip();
        String direct=local(normalized,engine,protectedNames);
        if(!direct.equals(normalized))return direct;
        Matcher completed=COMPLETED.matcher(english);
        if(completed.matches()) {
            String color=COLORS.get(completed.group(1).toLowerCase(Locale.ROOT));
            if(color!=null)return "通关"+color+"羊毛地牢"+(completed.group(2)==null?"":"，并在之后与"+local(completed.group(2),engine,protectedNames)+"交谈。");
        }
        if(english.equalsIgnoreCase("None"))return "无";
        if(normalized.startsWith("Defeated "))return "击败"+local(normalized.substring(9),engine,protectedNames);
        if(normalized.equals("Completion of any difficulty of the Ephemeral Corridors dungeon"))return "通关任意难度的幻逝回廊";
        if(normalized.equals("Completion of Wave 10 of the Arena"))return "竞技场达到第10波";
        if(normalized.equals("Ticket Master II Advancement"))return local("Ticket Master II",engine,protectedNames)+"进度";
        if(normalized.matches("Completion of the (?:King's Valley|Kings Valley|Celsian Isles) Monument"))return "完成"+(normalized.contains("Celsian")?local("Celsian Isles",engine,protectedNames):local("King's Valley",engine,protectedNames))+"纪念碑";
        if(normalized.startsWith("Completion of the ")&&normalized.endsWith(" dungeons")) {
            String[] colors=normalized.substring(18,normalized.length()-9).replace(", and ",", ").replace(" and ",", ").split(",\\s*");
            List<String> translated=new ArrayList<>();for(String color:colors){String value=COLORS.get(color.toLowerCase(Locale.ROOT));if(value==null)return english;translated.add(value);}
            return "通关"+String.join("、",translated)+"羊毛地牢";
        }
        int conjunction=normalized.indexOf(" and ");
        if(conjunction>0){String left=normalized.substring(0,conjunction),right=normalized.substring(conjunction+5),a=local(left,engine,protectedNames),b=local(right,engine,protectedNames);if(!a.equals(left)&&!b.equals(right))return a+"和"+b;}
        return local(english,engine,protectedNames);
    }
    private static String[] splitRequirements(String original) {
        // Commas inside a list of dungeon colors belong to one condition.
        Matcher tail=Pattern.compile("(?:^|,\\s*)((?:and )?Completion of the .+ dungeons)").matcher(original.replaceAll("\\s+"," "));
        if(tail.find()){String prefix=original.replaceAll("\\s+"," ").substring(0,tail.start()).strip();List<String> entries=new ArrayList<>();if(!prefix.isEmpty())entries.addAll(Arrays.asList(prefix.split(",\\s*")));entries.add(tail.group(1));return entries.toArray(String[]::new);}
        return original.split(",\\s*");
    }
    private static String start(String english,TranslationEngine engine,Set<String> protectedNames) {
        String direct=local(english,engine,protectedNames);if(!direct.equals(english))return direct;
        if(english.equals("The Teacher"))return "教师";
        if(english.equals("Oink Oink"))return "哼哼，哼哼";
        for(var entry:START_LOCATIONS.entrySet())if(english.endsWith(entry.getKey()))return entry.getValue()+local(english.substring(0,english.length()-entry.getKey().length()),engine,protectedNames);
        return english;
    }
    private static String local(String english,TranslationEngine engine,Set<String> protectedNames) {
        String value=engine.translatePreserving(english,false,protectedNames).replaceAll("§[0-9A-FK-ORa-fk-or]","").strip();
        // Already bilingual dictionary values should not acquire a second copy of their English original.
        value=value.replace("（"+english+"）","").replace("("+english+")","").strip();
        return value.isEmpty()?english:value;
    }
    private static void pair(JsonArray output,String chinese,String english,JsonObject style) {
        add(output,"  "+chinese,style);
        if(!chinese.equals(english)) {newline(output,1);JsonObject secondary=style.deepCopy();secondary.addProperty("color","gray");add(output,"  "+english,secondary);}
    }
    private static void label(JsonArray output,String text) {JsonObject style=new JsonObject();style.addProperty("color","gray");add(output,text,style);}
    private static void newline(JsonArray output,int count) {output.add("\n".repeat(count));}
    private static void add(JsonArray output,String text,JsonObject style) {
        JsonObject node=style.deepCopy();TranslationEngine.replaceTranslatedText(node,text);output.add(node);
    }
    private static JsonObject styleAt(List<JsonObject> runs,int position) {
        int offset=0;for(JsonObject run:runs){int stop=offset+run.get("text").getAsString().length();if(position<stop)return run.getAsJsonObject("style").deepCopy();offset=stop;}return new JsonObject();
    }
    private static void collect(JsonElement value,JsonObject inherited,List<JsonObject> runs) {
        if(value==null||value.isJsonNull())return;
        if(value.isJsonPrimitive()) {JsonObject run=new JsonObject();run.addProperty("text",value.getAsString());run.add("style",inherited.deepCopy());runs.add(run);return;}
        if(value.isJsonArray()){for(JsonElement child:value.getAsJsonArray())collect(child,inherited,runs);return;}
        JsonObject object=value.getAsJsonObject(),style=inherited.deepCopy();for(String key:STYLES)if(object.has(key))style.add(key,object.get(key).deepCopy());
        if(object.has("text"))collect(object.get("text"),style,runs);if(object.has("extra"))collect(object.get("extra"),style,runs);
    }
    private static boolean unsupported(JsonElement value) {
        if(value==null||value.isJsonNull())return false;
        if(value.isJsonPrimitive())return !value.getAsJsonPrimitive().isString();
        if(value.isJsonArray()){for(JsonElement child:value.getAsJsonArray())if(unsupported(child))return true;return false;}
        JsonObject object=value.getAsJsonObject();for(String key:List.of("translate","keybind","selector","score","nbt","clickEvent","hoverEvent","insertion","with"))if(object.has(key))return true;
        return object.has("extra")&&unsupported(object.get("extra"));
    }
}
