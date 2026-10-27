package monumentazh;

import java.util.*;
import com.google.gson.*;
import net.minecraft.class_2561;

/** Compact the two confirmed padded class-selection cards; vanilla renders the resulting rows. */
public final class ExtraInformationTooltip {
    public static boolean matches(List<class_2561> lines) {
        return !lines.isEmpty()&&Set.of("Extra Information","Change Ability Triggers").contains(TranslationEngine.normalize(lines.get(0).getString()));
    }
    public static void compact(List<class_2561> source,List<class_2561> translated) {
        class_2561 points=null;
        for(class_2561 line:source)if((points=findPoints(line))!=null)break;
        for(int i=0;i<source.size();i++) {
            class_2561 line=source.get(i),donor=firstContent(line);
            String raw=TranslationEngine.normalize(line.getString());
            String value=switch(raw) {
                case "Extra Information" -> "额外信息";
                case "Change Ability Triggers" -> "调整技能触发方式";
                case "View and change which keybinds" -> "查看并修改施放技能时";
                case "are used to cast abilities." -> "使用的按键。";
                case "You can always change your class and" -> "你可随时免费更换";
                case "abilities for no cost at any time!" -> "职业与技能。";
                case "You will gain more Skill Points and unlock" -> "通关地牢、推进游戏进度，";
                case "more abilities by completing dungeons" -> "即可获得更多技能点，";
                case "and progressing through the game!" -> "并解锁更多技能。";
                case "" -> "";
                default -> raw.matches("·{30,}")?"························":null;
            };
            if(value==null)continue;
            if(raw.equals("more abilities by completing dungeons")&&points!=null) {
                translated.set(i,styled(donor,"即可获得更多").method_10852(styled(points,"技能点")).method_27693("，"));
            } else translated.set(i,styled(donor,value));
        }
    }
    private static net.minecraft.class_5250 styled(class_2561 donor,String text) {
        return MonumentaZh.translatedLine(donor,text).method_27661();
    }
    private static class_2561 firstContent(class_2561 line) {
        JsonElement json=class_2561.class_2562.method_10868(line);
        if(json.isJsonPrimitive()&&!json.getAsString().isBlank())return line;
        if(json.isJsonObject()&&json.getAsJsonObject().has("text")&&!json.getAsJsonObject().get("text").getAsString().isBlank())return line;
        for(class_2561 child:line.method_10855())if(!child.getString().isBlank())return firstContent(child);
        return line;
    }
    private static class_2561 findPoints(class_2561 line) {
        if(line.getString().equals("Skill Points"))return line;
        for(class_2561 child:line.method_10855()){class_2561 found=findPoints(child);if(found!=null)return found;}
        return null;
    }
}
