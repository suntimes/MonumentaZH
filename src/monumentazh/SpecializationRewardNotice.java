package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Complete, audited specialization reward sentences only; no inferred item or class translations. */
public final class SpecializationRewardNotice {
    private record Reward(String specialization,String item,String article,String oldSentence,Pattern source) {
        Reward(String specialization,String item,String article,String oldSentence) {
            this(specialization,item,article,oldSentence,Pattern.compile("^Awarded (?:the )?"
                +Pattern.quote(specialization)+(item==null?"":" and (?:(?:a|an|the) )?"+Pattern.quote(item))+"[.!]?\\z") );
        }
        String standard() {return "Awarded the "+specialization+(item==null?"":" and "+article+item)+"!";}
    }
    private static final List<Reward> REWARDS=List.of(
        new Reward("Arcanist Specialization","Erriot's Eradicator","","Awarded Arcanist Specialization and Erriot's Eradicator"),
        new Reward("Elementalist Specialization","Combustion Staff","a ","Awarded Elementalist Specialization and a Combustion Staff"),
        new Reward("Berserker Specialization","Steelscale Maul","a ","Awarded Berserker Specialization and a Steelscale Maul"),
        new Reward("Guardian specialization","Steelguard","a ","Awarded Guardian specialization and a Steelguard"),
        new Reward("Seraph specialization","Windwrought Shield","a ","Awarded Seraph specialization and a Windwrought Shield"),
        new Reward("Paladin Specialization",null,"","Awarded Paladin Specialization"),
        new Reward("Assassin Specialization","Gale Shortsword","a ","Awarded the Assassin Specialization and a Gale Shortsword!"),
        new Reward("Swordsage specialization","Frosted Sabre","a ","Awarded the Swordsage specialization and a Frosted Sabre!"),
        new Reward("Apothecary Specialization","Drowned Trident","a ","Awarded the Apothecary Specialization and a Drowned Trident!"),
        new Reward("Harbinger Specialization","Briskbronze Helm","a ","Awarded the Harbinger Specialization and a Briskbronze Helm!"),
        new Reward("Hunter Specialization","Viridian Hunter","a ","Awarded Hunter Specialization and a Viridian Hunter!"),
        new Reward("Ranger Specialization","Eagle's Talon","an ","Awarded Ranger Specialization and an Eagle's Talon"),
        new Reward("Reaper Specialization","Gravemaking Scythe","a ","Awarded Reaper Specialization and a Gravemaking Scythe"),
        new Reward("Tenebrist Specialization","Rusted Sickle","a ","Awarded Tenebrist Specialization and Rusted Sickle"),
        new Reward("Soothsayer Specialization","Veil of the Blizzard","a ","Awarded Soothsayer Specialization and a Veil of the Blizzard."),
        new Reward("Hexbreaker Specialization","Ishnir Catalyst","an ","Awarded Hexbreaker Specialization and an Ishnir Catalyst.")
    );
    private SpecializationRewardNotice() {}
    public static boolean rewrite(JsonElement json,String originalVisible,TranslationEngine engine) {
        if(json==null||!json.isJsonObject()||originalVisible==null||engine==null)return false;
        JsonObject root=json.getAsJsonObject();
        if(!root.has("text")||!root.get("text").isJsonPrimitive()||!root.getAsJsonPrimitive("text").isString()
            ||!root.get("text").getAsString().equals(originalVisible))return false;
        for(String key:List.of("extra","translate","keybind","selector","score","nbt","with","separator","signature","signed","sender","senderUuid"))if(root.has(key))return false;
        if(!root.has("color")||!root.get("color").isJsonPrimitive()||!root.getAsJsonPrimitive("color").isString())return false;
        String color=root.get("color").getAsString();
        if(!color.equals("red")&&!color.equalsIgnoreCase("#ff5555"))return false;
        // Even an explicit identity mapping is intentional and wins over this fallback.
        if(engine.exactTranslation(originalVisible)!=null)return false;
        for(Reward reward:REWARDS)if(reward.source().matcher(originalVisible).matches()) {
            String translated=engine.exactTranslation(reward.standard());
            if(translated==null)translated=engine.exactTranslation(reward.oldSentence());
            // An identity candidate is not an audited Chinese sentence; fail closed.
            if(translated==null||!translated.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))return false;
            TranslationEngine.replaceTranslatedText(root,translated);return true;
        }
        return false;
    }
}
