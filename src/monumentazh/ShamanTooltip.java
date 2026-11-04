package monumentazh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.*;

/** A dictionary available only to identified Shaman class/ability cards. */
public final class ShamanTooltip {
    private static final Set<String> TITLES=Set.of("Shaman","Flame Totem","Lightning Totem","Earthen Tremor","Spiritualism","Interconnected Havoc","Cleansing Totem","Chain Lightning","Ignition Drive");
    private static final Pattern LEVEL=Pattern.compile("\\s*\\[Lv\\. \\d+\\]$");
    private static final TranslationEngine LOCAL=load();
    private ShamanTooltip() {}
    public static boolean matches(List<class_2561> lines) {
        return !lines.isEmpty()&&TITLES.contains(LEVEL.matcher(TranslationEngine.normalize(lines.get(0).getString())).replaceFirst(""))
            &&lines.stream().anyMatch(t->t.getString().matches("[·–—─◆=\\-]{8,}"));
    }
    private static TranslationEngine load() {
        try(InputStream input=ShamanTooltip.class.getResourceAsStream("/monumenta_shaman.json")) {
            if(input!=null)return new TranslationEngine(JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject());
        } catch(Exception ex) {System.err.println("[MonumentaZH] Shaman dictionary unavailable: "+ex.getMessage());}
        return new TranslationEngine(new JsonObject());
    }
    public static class_2561 translate(class_2561 original,TranslationEngine global) {
        JsonElement json=class_2561.class_2562.method_10868(original).deepCopy();
        if(CapturePrivacy.playerComponent(json))return original;
        if(json.isJsonPrimitive()){JsonObject object=new JsonObject();object.add("text",json);json=object;}
        boolean changed=LOCAL.rewriteVisible(json,false);
        class_2561 structured=changed?class_2561.class_2562.method_10872(json):original;
        String translated=LOCAL.translate(original.getString(),false);
        if(translated.equals(original.getString()))return changed?structured:null;
        if(structured!=null&&words(structured.getString()).equals(words(translated)))return structured;
        // Never flatten native keybinds or interactive text. Its visible label is client-controlled.
        if(interactive(class_2561.class_2562.method_10868(original)))return changed?structured:null;
        class_2561 output=MonumentaZh.translatedTooltipLine(original,translated);
        return highlightedNames(original,output,global);
    }
    private static String words(String text) {return TranslationEngine.normalize(text).replaceAll("\\s+","").replace('（','(').replace('）',')');}
    private static boolean interactive(JsonElement json) {
        if(json.isJsonArray()){for(JsonElement child:json.getAsJsonArray())if(interactive(child))return true;}
        if(json.isJsonObject()) {
            JsonObject object=json.getAsJsonObject();
            for(String key:List.of("keybind","hoverEvent","clickEvent","translate","selector","score","nbt"))if(object.has(key))return true;
            if(object.has("extra"))return interactive(object.get("extra"));
        }
        return false;
    }
    /** Recover isolated skill/totem highlight runs after full-line sentence reordering. */
    private static class_2561 highlightedNames(class_2561 source,class_2561 output,TranslationEngine global) {
        Map<String,class_2583> names=new LinkedHashMap<>();
        source.method_27658((style,value)->{
            String key=value.trim();
            if(TITLES.contains(key)||key.equals("Totem")||key.equals("Totems")) {
                String target=key.equals("Totem")||key.equals("Totems")?"图腾":LOCAL.translate(key,false);
                if(target.equals(key))target=global.translate(key,false);
                if(!target.equals(key)&&!target.isBlank())names.putIfAbsent(target,style);
            }
            return Optional.empty();
        },class_2583.field_24360);
        if(names.isEmpty())return output;
        List<class_2583> styles=new ArrayList<>();
        output.method_27658((style,value)->{for(int i=0;i<value.length();i++)styles.add(style);return Optional.empty();},class_2583.field_24360);
        String text=output.getString();
        for(var entry:names.entrySet())for(int at=text.indexOf(entry.getKey());at>=0;at=text.indexOf(entry.getKey(),at+entry.getKey().length()))
            for(int i=at;i<at+entry.getKey().length();i++)styles.set(i,entry.getValue());
        class_5250 result=class_2561.method_43473();
        for(int at=0;at<text.length();) {
            int end=at+1;while(end<text.length()&&styles.get(end).equals(styles.get(at)))end++;
            result.method_10852(class_2561.method_43470(text.substring(at,end)).method_10862(styles.get(at)));at=end;
        }
        return result;
    }
}
