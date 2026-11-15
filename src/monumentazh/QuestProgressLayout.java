package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Display-only layout for the audited Chinese Life Is of the Essence objective. */
public final class QuestProgressLayout {
    private static final String TITLE="生命的精华";
    private static final Pattern OBJECTIVE=Pattern.compile("^(生灵精华|生命之本|生命的精华)[ \\t]*(\\[[0-9]+/[0-9]+\\])?[ \\t]*[:：]从(.+?)身上收割(.+)$");
    private static final Pattern PROGRESS=Pattern.compile("^[ \\t]*([^\\[\\]\\r\\n§]+?)[ \\t]*[:：]?[ \\t]*(\\[[0-9]+/[0-9]+\\])[ \\t]*[:：](.+)$");
    private static final Pattern PROGRESS_TAIL=Pattern.compile("^[ \\t]*[:：]?[ \\t]*\\[[0-9]+/[0-9]+\\][ \\t]*[:：](.+)$");
    private static final Pattern TITLE_COUNTER=Pattern.compile("^([^\\[\\]]+?)[ \\t]*\\[[0-9]+/[0-9]+\\][ \\t]*$");
    private static final Pattern DESCRIPTION_TAIL=Pattern.compile("^[ \\t]*[:：](.+)$");
    private record Leaf(JsonObject object,JsonArray array,int index,String text,int start) {
        void put(String value) {
            if(object!=null)object.addProperty("text",value);
            else array.set(index,new JsonPrimitive(value));
        }
    }
    private record Edit(int start,int end,String replacement) {}
    private QuestProgressLayout() {}

    public static boolean rewrite(JsonElement translatedCopy,String originalVisible) {
        return rewrite(translatedCopy,originalVisible,false);
    }
    public static boolean isQuestProgress(JsonElement original) {
        if(original==null||!original.isJsonObject())return false;
        JsonObject root=original.getAsJsonObject();
        if(!plain(root)||!root.get("text").getAsString().isBlank()||!root.has("extra")||!root.get("extra").isJsonArray())return false;
        JsonArray extra=root.getAsJsonArray("extra");
        if(extra.size()!=2||!extra.get(0).isJsonObject()||!extra.get(1).isJsonObject())return false;
        JsonObject title=extra.get(0).getAsJsonObject(),tail=extra.get(1).getAsJsonObject();
        if(!plain(title)||!plain(tail)||title.has("extra")||tail.has("extra"))return false;
        String name=title.get("text").getAsString(),body=tail.get("text").getAsString();
        if(!eligible(name)||name.isBlank()||!eligible(body))return false;
        if(!title.has("bold")||!title.get("bold").isJsonPrimitive()
            ||!title.getAsJsonPrimitive("bold").isBoolean()||!title.get("bold").getAsBoolean())return false;
        if(!(color(title,"aqua","#55ffff")||color(title,"red","#ff5555"))||!color(tail,"aqua","#55ffff"))return false;
        Matcher match=PROGRESS_TAIL.matcher(body);
        if(name.indexOf('[')<0&&name.indexOf(']')<0)return match.matches()&&!match.group(1).isBlank();
        Matcher titleCounter=TITLE_COUNTER.matcher(name),description=DESCRIPTION_TAIL.matcher(body);
        return titleCounter.matches()&&!titleCounter.group(1).isBlank()
            &&!match.matches()&&description.matches()&&!description.group(1).isBlank();
    }
    private static boolean plain(JsonObject object) {
        if(!object.has("text")||!object.get("text").isJsonPrimitive()||!object.getAsJsonPrimitive("text").isString())return false;
        for(String key:List.of("translate","keybind","selector","score","nbt","with","separator"))if(object.has(key))return false;
        return true;
    }
    private static boolean color(JsonObject object,String named,String hex) {
        if(!object.has("color")||!object.get("color").isJsonPrimitive()||!object.getAsJsonPrimitive("color").isString())return false;
        String color=object.get("color").getAsString();return named.equalsIgnoreCase(color)||hex.equalsIgnoreCase(color);
    }
    public static boolean rewrite(JsonElement translatedCopy,String originalVisible,boolean recognizedProgress) {
        if(rewriteHarvest(translatedCopy,originalVisible))return true;
        if(!recognizedProgress||!eligible(originalVisible))return false;
        Matcher source=PROGRESS.matcher(originalVisible);
        if(!source.matches()||source.group(1).isBlank()||source.group(3).isBlank())return false;
        List<Leaf> leaves=new ArrayList<>();StringBuilder full=new StringBuilder();
        if(!collect(translatedCopy,null,-1,leaves,full)||!eligible(full.toString()))return false;
        Matcher target=PROGRESS.matcher(full);
        if(!target.matches()||target.group(1).isBlank()||target.group(3).isBlank()||!source.group(2).equals(target.group(2)))return false;
        List<Edit> edits=new ArrayList<>();
        if(sameLifeQuest(source.group(1).strip())&&sameLifeQuest(target.group(1).strip()))
            edits.add(new Edit(target.start(1),target.end(1),TITLE));
        edits.add(new Edit(target.end(1),target.start(2)," "));
        edits.add(new Edit(target.end(2),target.start(3),"\n"));
        return apply(leaves,edits);
    }
    /** Only this quest heading loses its NPC annotation; objective NPC references remain bilingual. */
    public static boolean chineseIgorTitle(JsonElement translatedCopy,String original) {
        if(!(original.startsWith("Lie of the Igor:")||original.startsWith("伊戈尔的谎言：")||original.startsWith("伊戈尔的谎言:")))return false;
        List<Leaf> leaves=new ArrayList<>();StringBuilder visible=new StringBuilder();
        if(!collect(translatedCopy,null,-1,leaves,visible))return false;
        for(String suffix:List.of("（Igor）","(Igor)")) {
            String prefix="伊戈尔"+suffix+"的谎言";
            if(visible.toString().startsWith(prefix))return apply(leaves,List.of(new Edit(3,3+suffix.length(),"")));
        }
        return false;
    }
    private static boolean sameLifeQuest(String title) {
        return Set.of("生灵精华","生命之本",TITLE,"Life is of the Essence").contains(title);
    }
    private static boolean rewriteHarvest(JsonElement translatedCopy,String originalVisible) {
        if(!eligible(originalVisible))return false;
        Matcher original=OBJECTIVE.matcher(originalVisible);
        if(!original.matches()||original.group(3).isBlank()||original.group(4).isBlank())return false;
        List<Leaf> leaves=new ArrayList<>();StringBuilder visible=new StringBuilder();
        if(!collect(translatedCopy,null,-1,leaves,visible))return false;
        String full=visible.toString();
        if(!eligible(full))return false;
        Matcher match=OBJECTIVE.matcher(full);
        if(!match.matches()||match.group(3).isBlank()||match.group(4).isBlank()
            ||!Objects.equals(original.group(2),match.group(2)))return false;
        List<Edit> edits=new ArrayList<>();
        int titleEnd=match.end(1);
        if(!match.group(1).equals(TITLE))edits.add(new Edit(match.start(1),titleEnd,TITLE));
        if(match.group(2)==null)edits.add(new Edit(titleEnd,match.start(3),"\n目标："));
        else {
            // Progress glyphs retain their exact leaves and effective styles, including split digits.
            edits.add(new Edit(titleEnd,match.start(2)," "));
            edits.add(new Edit(match.end(2),match.start(3),"\n目标："));
        }
        edits.add(new Edit(match.end(3),match.start(4),"\n收割："));
        return apply(leaves,edits);
    }
    private static boolean apply(List<Leaf> leaves,List<Edit> edits) {
        // Plan every leaf's new text before mutating anything. Nodes and all metadata survive.
        Map<Leaf,String> changes=new LinkedHashMap<>();
        for(Leaf leaf:leaves) {
            StringBuilder value=new StringBuilder(leaf.text());
            for(int i=edits.size()-1;i>=0;i--) {
                Edit edit=edits.get(i);int start=Math.max(edit.start(),leaf.start());
                int end=Math.min(edit.end(),leaf.start()+leaf.text().length());
                if(edit.start()==edit.end()) {
                    // At a boundary the right-hand nonempty leaf owns the insertion.
                    if(edit.start()>=leaf.start()&&edit.start()<leaf.start()+leaf.text().length())
                        value.insert(edit.start()-leaf.start(),edit.replacement());
                    continue;
                }
                if(start>=end)continue;
                String replacement=edit.start()>=leaf.start()?edit.replacement():"";
                value.replace(start-leaf.start(),end-leaf.start(),replacement);
            }
            if(!value.toString().equals(leaf.text()))changes.put(leaf,value.toString());
        }
        for(var change:changes.entrySet())change.getKey().put(change.getValue());
        return !changes.isEmpty();
    }
    private static boolean eligible(String text) {
        return text!=null&&text.indexOf('\n')<0&&text.indexOf('\r')<0&&text.indexOf('§')<0;
    }
    private static boolean collect(JsonElement node,JsonArray parent,int index,List<Leaf> leaves,StringBuilder full) {
        if(node==null||node.isJsonNull())return false;
        if(node.isJsonPrimitive()) {
            if(parent==null||!node.getAsJsonPrimitive().isString())return false;
            String text=node.getAsString();leaves.add(new Leaf(null,parent,index,text,full.length()));full.append(text);return true;
        }
        if(node.isJsonArray()) {
            JsonArray array=node.getAsJsonArray();
            for(int i=0;i<array.size();i++)if(!collect(array.get(i),array,i,leaves,full))return false;
            return true;
        }
        JsonObject object=node.getAsJsonObject();
        // Hover/click contents are metadata, never traversed. Unknown visible component kinds fail closed.
        for(String key:List.of("translate","keybind","selector","score","nbt","with","separator"))if(object.has(key))return false;
        if(object.has("text")) {
            JsonElement text=object.get("text");
            if(!text.isJsonPrimitive()||!text.getAsJsonPrimitive().isString())return false;
            String value=text.getAsString();leaves.add(new Leaf(object,null,-1,value,full.length()));full.append(value);
        }
        if(object.has("extra")) {
            if(!object.get("extra").isJsonArray())return false;
            if(!collect(object.get("extra"),null,-1,leaves,full))return false;
        }
        return object.has("text")||object.has("extra");
    }
}
