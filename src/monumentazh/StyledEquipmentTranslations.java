package monumentazh;

import com.google.gson.*;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import java.util.*;

/** Complete, audited labels whose source runs carry meaningful effects. */
final class StyledEquipmentTranslations {
    private record Rule(String expected, TranslationEngine local) {}
    private static final Map<String,Rule> RULES=Map.of(
        "The lxxxxxxx's presence was an utter enigma.", rule("lxxxxxxx的存在是个彻底的谜。",
            "The", "", "'s presence was an utter enigma.", "的存在是个彻底的谜。"),
        "Yes... this was the culmination of their sacrifices. Of her sacrifices.", rule(
            "是的……这是他们牺牲的结晶。也是她牺牲的结晶。",
            "Yes... this was the culmination of their sacrifices. Of", "是的……这是他们牺牲的结晶。也是",
            "her", "她", "sacrifices.", "牺牲的结晶。"),
        "Captain of Death", rule("死亡船长", "Captain", "船长", "of Death", "死亡"),
        "Transforms arrows to Ateoq Arrow",rule("将箭矢转化为阿特奥克之箭")
    );
    private StyledEquipmentTranslations() {}

    private static Rule rule(String expected,String... entries) {
        JsonObject exact=new JsonObject(),dictionary=new JsonObject();
        for(int i=0;i<entries.length;i+=2)exact.addProperty(entries[i],entries[i+1]);
        dictionary.add("exact",exact);
        return new Rule(expected,new TranslationEngine(dictionary));
    }

    static class_2561 apply(class_2561 source,TranslationEngine engine) {
        String raw=source.getString();
        class_2561 legacy=legacyCaption(source,engine);
        if(legacy!=null)return legacy;
        if(raw.equals(LATIN_SOURCE))return latin(source,engine);
        Rule rule=RULES.get(raw);
        if(rule==null&&!raw.equals("Apple Ring"))return null;
        JsonElement copy=class_2561.class_2562.method_10868(source).deepCopy();
        if(interactive(copy))return null;
        String expected=rule==null?"苹果戒指":rule.expected();
        // A user-edited complete translation always takes precedence.
        if(!engine.translate(raw,false).equals(expected))return null;
        if(raw.equals("Apple Ring"))return apple(source);
        if(raw.equals("Transforms arrows to Ateoq Arrow")) {
            List<class_2583> styles=styles(source);
            if(styles.size()!=raw.length())return null;
            return class_2561.method_43473().method_10852(MonumentaZh.translatedLine(
                class_2561.method_43470("").method_10862(styles.get(0)),"将箭矢转化为"))
                .method_10852(MonumentaZh.translatedLine(class_2561.method_43470("").method_10862(
                    styles.get(raw.indexOf("Ateoq Arrow"))),"阿特奥克之箭"));
        }
        if(raw.equals("Captain of Death")) {
            // Chinese reverses the two semantic runs; each keeps its own source style.
            List<class_2583> styles=styles(source);
            if(styles.size()!=raw.length())return null;
            return class_2561.method_43473().method_10852(MonumentaZh.translatedLine(class_2561.method_43470("").method_10862(styles.get(8)),"死亡"))
                .method_10852(MonumentaZh.translatedLine(
                    class_2561.method_43470("").method_10862(styles.get(0)),"船长"));
        }
        if(!rule.local().rewriteVisible(copy,false))return null;
        class_2561 result=class_2561.class_2562.method_10872(copy);
        return result!=null&&result.getString().equals(expected)?result:null;
    }

    private static final String LATIN_SOURCE="Quidquid in altum fortuna tulit, ruitura levat.";
    private static final Map<String,String> LEGACY_CAPTIONS=Map.of(
        "§a- Enable §6Buff Stations","§a- 启用§6增益站",
        "§a- Open §cCrimson Doors","§a- 打开§c猩红之门",
        "§b10% Speed §7until Death","§b10% 移动速度§7，持续至死亡",
        "§5-1s Revive Time §7until Death","§5复活时间-1秒§7，持续至死亡");
    /** Legacy codes reset at component boundaries; materialize the audited color runs. */
    private static class_2561 legacyCaption(class_2561 source,TranslationEngine engine) {
        String target=LEGACY_CAPTIONS.get(source.getString());
        if(target==null||!engine.translate(source.getString(),false).equals(target))return null;
        JsonElement tree=class_2561.class_2562.method_10868(source);
        if(!tree.isJsonObject()||tree.getAsJsonObject().has("extra")||tree.getAsJsonObject().has("with"))return null;
        JsonObject copy=tree.getAsJsonObject().deepCopy();JsonArray rows=new JsonArray();String color="white";int start=0;
        Map<Character,String> colors=Map.of('a',"green",'6',"gold",'c',"red",'b',"aqua",'7',"gray",'5',"dark_purple");
        for(int i=0;i<=target.length();i++) {
            if(i<target.length()&&(target.charAt(i)!='§'||i+1>=target.length()))continue;
            if(i>start) {
                JsonObject run=new JsonObject();TranslationEngine.replaceTranslatedText(run,target.substring(start,i));
                run.addProperty("color",color);
                for(String flag:List.of("bold","italic","underlined","strikethrough","obfuscated"))run.addProperty(flag,false);
                rows.add(run);
            }
            if(i<target.length()){color=colors.get(target.charAt(i+1));if(color==null)return null;i++;start=i+1;}
        }
        copy.addProperty("text","");copy.add("extra",rows);
        return class_2561.class_2562.method_10872(copy);
    }
    private static final String LATIN_TARGET="命运将一切举上高处，正是为了将其摔落。";
    private static final String[][] LATIN_RUNS={
        {"命","for"},{"运","tuna"},{"将","tulit"},{"一","Quid"},{"切","quid"},
        {"举","levat"},{"上","in "},{"高处","altum"},{"，正是为了将其摔落。","ruitura"}
    };
    private static class_2561 latin(class_2561 source,TranslationEngine engine) {
        if(!engine.translate(LATIN_SOURCE,false).equals(LATIN_TARGET))return null;
        if(interactive(class_2561.class_2562.method_10868(source)))return null;
        List<class_2583> styles=styles(source);
        if(styles.size()!=LATIN_SOURCE.length())return null;
        var out=class_2561.method_43473();
        // Reordered Chinese terms retain the visible/obfuscated styles of their
        // corresponding Latin words. The source component is never rewritten.
        for(String[] run:LATIN_RUNS)out.method_10852(MonumentaZh.translatedLine(
            class_2561.method_43470("").method_10862(styles.get(LATIN_SOURCE.indexOf(run[1]))),run[0]));
        return out;
    }

    private static class_2561 apple(class_2561 source) {
        List<class_2583> styles=styles(source);
        if(styles.size()!=10)return null;
        // Four Chinese glyphs sample the original green-to-white ramp at its endpoints
        // and two interior positions. No color, boldness or font is invented.
        int[] positions={0,3,6,9};String target="苹果戒指";
        var out=class_2561.method_43473();
        for(int i=0;i<positions.length;i++)out.method_10852(MonumentaZh.translatedLine(
            class_2561.method_43470("").method_10862(styles.get(positions[i])),target.substring(i,i+1)));
        return out;
    }

    private static List<class_2583> styles(class_2561 source) {
        List<class_2583> styles=new ArrayList<>();
        source.method_27658((style,text)->{for(int i=0;i<text.length();i++)styles.add(style);return Optional.empty();},class_2583.field_24360);
        return styles;
    }

    private static boolean interactive(JsonElement value) {
        if(value.isJsonArray()){for(JsonElement child:value.getAsJsonArray())if(interactive(child))return true;}
        if(!value.isJsonObject())return false;
        JsonObject object=value.getAsJsonObject();
        for(String key:List.of("hoverEvent","clickEvent","insertion","keybind","translate"))if(object.has(key))return true;
        return object.has("extra")&&interactive(object.get("extra"));
    }
}
