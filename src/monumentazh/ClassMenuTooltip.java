package monumentazh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.*;

/** Remove server-side centering from class cards; Minecraft owns wrapping and tooltip rendering. */
public final class ClassMenuTooltip {
    private static final Set<String> CARD_TITLES=loadTitles();
    private static final Set<String> ALCHEMIST=Set.of("Alchemist","Alchemical Artillery","Alchemist Potions","Bezoar","Brutal Alchemy","Energizing Elixir","Esoteric Enhancements","Gruesome Alchemy","Iron Tincture","Panacea","Scorched Earth","Taboo","Transmutation Ring","Unstable Amalgam","Volatile Reaction","Warding Remedy");
    private static final java.util.regex.Pattern LEVEL=java.util.regex.Pattern.compile("\\s*\\[Lv\\. \\d+\\]$");
    private static final Map<class_2561,Integer> INDENTS=new IdentityHashMap<>();
    private static final ArrayDeque<class_2561> INDENT_ORDER=new ArrayDeque<>();
    private static synchronized Integer getIndent(class_2561 text){return INDENTS.get(text);}
    private static synchronized void putIndent(class_2561 text,int indent) {
        if(!INDENTS.containsKey(text)) {while(INDENTS.size()>=4096)INDENTS.remove(INDENT_ORDER.removeFirst());INDENT_ORDER.addLast(text);}
        INDENTS.put(text,indent);
    }
    static void rememberLayout(class_2561 source,class_2561 translated) {
        String raw=source.getString();
        int left=raw.length()-raw.stripLeading().length(),right=raw.length()-raw.stripTrailing().length();
        putIndent(translated,Math.max(0,left-Math.min(left,right)));
    }    static void copyLayout(class_2561 from,class_2561 to){Integer indent=getIndent(from);if(indent!=null)putIndent(to,indent);}
    public static boolean alchemist(List<class_2561> lines) {
        return !lines.isEmpty()&&ALCHEMIST.contains(LEVEL.matcher(TranslationEngine.normalize(lines.get(0).getString())).replaceFirst(""))&&lines.stream().anyMatch(ClassMenuTooltip::separator);
    }
    public static boolean matches(class_1799 stack,List<class_2561> lines) {
        if(ExtraInformationTooltip.matches(lines)||alchemist(lines)||knownCard(lines))return true;
        var nbt=stack.method_7969();
        if(nbt==null||!nbt.method_10545("Gui"))return false;
        String texture=nbt.method_10562("Gui").method_10558("texture");
        return texture.startsWith("class_select_")||texture.startsWith("skill_select_")
            ||lines.stream().anyMatch(t->Set.of("Use the items to the right to","dungeon to unlock this ability...","ᴇxᴛʀᴀ ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ","ᴇxᴛʀᴀ ᴛʀɪɢɢᴇʀѕ").contains(TranslationEngine.normalize(t.getString())));
    }
    /** Real class cards can omit Gui.texture; require an audited title and native divider structure. */
    static boolean knownCard(List<class_2561> lines) {
        return lines!=null&&!lines.isEmpty()&&CARD_TITLES.contains(LEVEL.matcher(TranslationEngine.normalize(lines.get(0).getString())).replaceFirst(""))
            &&(lines.stream().filter(ClassMenuTooltip::separator).count()>=2
                ||lines.size()==3&&lines.get(1).getString().equals("Click to choose cosmetics for"));
    }
    private static Set<String> loadTitles() {
        try(InputStream stream=ClassMenuTooltip.class.getResourceAsStream("/class-menu-titles.json")) {
            if(stream==null)return Set.of();
            try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                Set<String> titles=new HashSet<>();
                for(JsonElement item:JsonParser.parseReader(reader).getAsJsonArray())titles.add(item.getAsString());
                return Set.copyOf(titles);
            }
        }catch(Exception ex){return Set.of();}
    }
    public static void compact(List<class_2561> lines) { compact(null,lines); }
    public static void compact(List<class_2561> source,List<class_2561> lines) {
        var client=class_310.method_1551();var font=client.field_1772;
        int limit=Math.max(24,Math.min(260,client.method_22683().method_4486()-20));
        List<class_2561> result=new ArrayList<>();
        boolean extraPage=source!=null && source.size()==lines.size() && SkillExtraPage.matches(source);
        int sourceRow=0;
        for(class_2561 original:lines) {
            class_2561 line=trim(original);
            // Only pair source rows when translation kept the row count. Merged paragraphs need metadata.
            if(source!=null&&source.size()==lines.size()&&sourceRow>0) {
                String raw=source.get(sourceRow).getString();
                int left=raw.length()-raw.stripLeading().length(),right=raw.length()-raw.stripTrailing().length();
                int indent=Math.max(0,left-Math.min(left,right));
                if(extraPage && sourceRow>2 && left>0 && right>0 && !raw.isBlank())indent=Math.max(indent,2);
                if(indent>0&&!separator(line))line=class_2561.method_43470(" ".repeat(indent)).method_10852(line);
            }
            Integer remembered=getIndent(original);
            if(remembered!=null&&remembered>0&&(source==null||source.size()!=lines.size())&&!separator(line))
                line=class_2561.method_43470(" ".repeat(remembered)).method_10852(line);
            sourceRow++;
            if(separator(line)||font.method_27525(line)<=limit)result.add(line);
            else for(class_5348 row:font.method_27527().method_27495(line,limit,class_2583.field_24360)) {
                result.add(styled(row));
            }
        }
        int width=Math.min(limit,Math.max(Math.min(150,limit),result.stream().filter(t->!separator(t)).mapToInt(font::method_27525).max().orElse(0)));
        for(int i=0;i<result.size();i++) {
            class_2561 line=result.get(i);
            if(separator(line)) {
                String raw=line.getString();boolean diamond=raw.endsWith("◆");
                class_2561 end=diamond?slice(line,raw.length()-1,raw.length()):class_2561.method_43473();
                class_2561 body=diamond?slice(line,0,raw.length()-1):line;
                // Styled trimming preserves the resource pack's native glyph font and bold divider runs.
                result.set(i,styled(font.method_27527().method_27490(body,Math.max(0,width-font.method_27525(end)),class_2583.field_24360)).method_10852(end));
            }else if((i==0 || (extraPage && i==1))&&source!=null&&!source.isEmpty()&&sourceCentered(source.get(0))) {
                int space=Math.max(1,font.method_1727(" "));
                int pad=Math.max(0,(width-font.method_27525(line))/(2*space));
                result.set(i,class_2561.method_43470(" ".repeat(pad)).method_10862(class_2583.field_24360).method_10852(line));
            }
        }
        lines.clear();lines.addAll(result);
    }
    static boolean separator(class_2561 line) {
        return line.getString().strip().matches("[·–—─◆=\\- ]{8,}")&&!line.getString().isBlank();
    }
    private static boolean sourceCentered(class_2561 line) {
        String raw=line.getString();
        int left=raw.length()-raw.stripLeading().length(),right=raw.length()-raw.stripTrailing().length();
        return left>0&&right>0&&Math.abs(left-right)<=1;
    }    private static class_5250 styled(class_5348 text) {
        class_5250 result=class_2561.method_43473();
        text.method_27658((style,value)->{result.method_10852(class_2561.method_43470(value).method_10862(style));return Optional.empty();},class_2583.field_24360);
        return result;
    }
    private static class_2561 slice(class_2561 text,int start,int end) {
        class_5250 result=class_2561.method_43473();int[] offset={0};
        text.method_27658((style,value)->{int a=Math.max(0,start-offset[0]),b=Math.min(value.length(),end-offset[0]);
            if(a<b)result.method_10852(class_2561.method_43470(value.substring(a,b)).method_10862(style));offset[0]+=value.length();return Optional.empty();},class_2583.field_24360);
        return result;
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



