package monumentazh;

import com.google.gson.*;
import java.util.*;
import net.minecraft.*;

/** Remove server-side centering from class cards; Minecraft owns wrapping and tooltip rendering. */
public final class ClassMenuTooltip {
    private static final Set<String> ALCHEMIST=Set.of("Alchemist","Alchemical Artillery","Alchemist Potions","Bezoar","Brutal Alchemy","Energizing Elixir","Esoteric Enhancements","Gruesome Alchemy","Iron Tincture","Panacea","Scorched Earth","Taboo","Transmutation Ring","Unstable Amalgam","Volatile Reaction","Warding Remedy");
    private static final java.util.regex.Pattern LEVEL=java.util.regex.Pattern.compile("\\s*\\[Lv\\. \\d+\\]$");
    public static boolean alchemist(List<class_2561> lines) {
        return !lines.isEmpty()&&ALCHEMIST.contains(LEVEL.matcher(TranslationEngine.normalize(lines.get(0).getString())).replaceFirst(""))&&lines.stream().anyMatch(ClassMenuTooltip::separator);
    }
    public static boolean matches(class_1799 stack,List<class_2561> lines) {
        if(ExtraInformationTooltip.matches(lines)||alchemist(lines))return true;
        var nbt=stack.method_7969();
        if(nbt==null||!nbt.method_10545("Gui"))return false;
        String texture=nbt.method_10562("Gui").method_10558("texture");
        return texture.startsWith("class_select_")||texture.startsWith("skill_select_")
            ||lines.stream().anyMatch(t->Set.of("Use the items to the right to","dungeon to unlock this ability...","ᴇxᴛʀᴀ ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ","ᴇxᴛʀᴀ ᴛʀɪɢɢᴇʀѕ").contains(TranslationEngine.normalize(t.getString())));
    }
    public static void compact(List<class_2561> lines) {
        var client=class_310.method_1551();var font=client.field_1772;
        int limit=Math.min(260,client.method_22683().method_4486()-20);
        List<class_2561> result=new ArrayList<>();
        for(class_2561 original:lines) {
            class_2561 line=trim(original);
            if(separator(line)||font.method_27525(line)<=limit)result.add(line);
            else for(class_5348 row:font.method_27527().method_27495(line,limit,class_2583.field_24360)) {
                class_5250 text=class_2561.method_43473();
                row.method_27658((style,value)->{text.method_10852(class_2561.method_43470(value).method_10862(style));return Optional.empty();},class_2583.field_24360);
                result.add(text);
            }
        }
        int width=result.stream().filter(t->!separator(t)).mapToInt(font::method_27525).max().orElse(0);
        for(int i=0;i<result.size();i++) {
            class_2561 line=result.get(i);
            if(!separator(line))continue;
            String raw=line.getString(),end=raw.endsWith("◆")?"◆":"";
            String body=end.isEmpty()?raw:raw.substring(0,raw.length()-1);
            body=font.method_27527().method_27494(body,Math.max(0,width-font.method_1727(end)),line.method_10866());
            result.set(i,MonumentaZh.translatedLine(line,body+end));
        }
        lines.clear();lines.addAll(result);
    }
    private static boolean separator(class_2561 line) {
        return line.getString().matches("[·–—─◆=\\-]{8,}");
    }
    static class_2561 trim(class_2561 line) {
        String value=line.getString();int leading=value.length()-value.stripLeading().length(),trailing=value.length()-value.stripTrailing().length();
        if(leading==0&&trailing==0)return line;
        JsonElement json=class_2561.class_2562.method_10868(line).deepCopy();
        if(json.isJsonPrimitive()){JsonObject object=new JsonObject();object.add("text",json);json=object;}
        edge(json,new int[]{leading},false);edge(json,new int[]{value.isBlank()?0:trailing},true);
        return class_2561.class_2562.method_10872(json);
    }
    /** Consume only the visible outer whitespace, never spaces between differently styled words. */
    private static void edge(JsonElement json,int[] left,boolean reverse) {
        if(left[0]==0)return;
        if(json.isJsonArray()) {
            JsonArray array=json.getAsJsonArray();
            for(int i=0;i<array.size();i++) {
                int index=reverse?array.size()-1-i:i;JsonElement child=array.get(index);
                if(child.isJsonPrimitive()){JsonObject object=new JsonObject();object.add("text",child);array.set(index,object);child=object;}
                edge(child,left,reverse);
            }
        } else if(json.isJsonObject()) {
            JsonObject object=json.getAsJsonObject();
            if(reverse&&object.has("extra"))edge(object.get("extra"),left,true);
            if(object.has("text")) {
                String text=object.get("text").getAsString();int length=Math.min(left[0],reverse?text.length()-text.stripTrailing().length():text.length()-text.stripLeading().length());
                object.addProperty("text",reverse?text.substring(0,text.length()-length):text.substring(length));left[0]-=length;
            }
            if(!reverse&&object.has("extra"))edge(object.get("extra"),left,false);
        }
    }
}
