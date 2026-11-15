package monumentazh;

import com.google.gson.*;
import java.util.*;
import net.minecraft.class_2561;

/** Exact catalogued holograms only; source English and its dynamic colour spans remain intact. */
public final class HologramNameStyle {
    private record Glyph(int value,JsonObject style) {}
    private static final List<String> STYLES=List.of("color","bold","italic","underlined","strikethrough","obfuscated","font");
    public static class_2561 render(class_2561 original,String chinese) {
        JsonElement source=class_2561.class_2562.method_10868(original);
        List<Glyph> glyphs=new ArrayList<>();
        if(!collect(source,new JsonObject(),glyphs)||glyphs.isEmpty())return original;
        int[] chars=chinese.codePoints().toArray();JsonArray extra=new JsonArray();
        for(int i=0;i<chars.length;i++) {
            int at=chars.length<=1?0:Math.round(i*(glyphs.size()-1f)/(chars.length-1));
            JsonObject run=glyphs.get(at).style().deepCopy();
            run.addProperty("text",new String(Character.toChars(chars[i])));
            run.addProperty("font","minecraft:default");extra.add(run);
        }
        JsonObject open=glyphs.get(0).style().deepCopy();open.addProperty("text","（");extra.add(open);
        extra.add(source.deepCopy());
        JsonObject close=glyphs.get(glyphs.size()-1).style().deepCopy();close.addProperty("text","）");extra.add(close);
        JsonObject result=new JsonObject();result.addProperty("text","");result.add("extra",extra);
        // Preserve root interaction metadata; never rewrite its hidden contents.
        if(source.isJsonObject())for(String k:List.of("hoverEvent","clickEvent","insertion"))
            if(source.getAsJsonObject().has(k))result.add(k,source.getAsJsonObject().get(k).deepCopy());
        return class_2561.class_2562.method_10872(result);
    }
    private static boolean collect(JsonElement node,JsonObject inherited,List<Glyph> out) {
        if(node.isJsonPrimitive()){add(node.getAsString(),inherited,out);return true;}
        if(node.isJsonArray()){for(JsonElement v:node.getAsJsonArray())if(!collect(v,inherited,out))return false;return true;}
        if(!node.isJsonObject())return false;
        JsonObject o=node.getAsJsonObject();if(o.has("translate")||o.has("selector")||o.has("score")||o.has("keybind"))return false;
        JsonObject style=inherited.deepCopy();for(String k:STYLES)if(o.has(k))style.add(k,o.get(k).deepCopy());
        if(o.has("text")){String text=o.get("text").getAsString();if(text.indexOf('§')>=0)return false;add(text,style,out);}
        return !o.has("extra")||collect(o.get("extra"),style,out);
    }
    private static void add(String text,JsonObject style,List<Glyph> out){text.codePoints().forEach(c->out.add(new Glyph(c,style)));}
}
