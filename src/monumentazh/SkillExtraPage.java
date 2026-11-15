package monumentazh;
import java.util.*;
import com.google.gson.*;
import net.minecraft.class_2561;

/** Audited native extra-information text runs; unknown rows retain the existing translation. */
final class SkillExtraPage {
    public static boolean matches(List<class_2561> lines) {
        return lines.size()>=3 && lines.get(1).getString().strip().equals("ᴇxᴛʀᴀ ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ")
            && ClassMenuTooltip.separator(lines.get(2));
    }
    private static final Set<String> ROWS=Set.of(
        "(Alternate dash trigger)",
        "(Alternate unprime trigger)",
        "(Fire, Ice, Thunder, and Wind Aspect,",
        "(Reduced effectiveness on bosses)",
        "(Swap between healing/damage modes)",
        "|| Click to remove this ability.",
        "|| Press F to go back!",
        "A stronger form of Weakness.",
        "Additional triggers can be customized",
        "Afflicted mobs deal reduced melee (🗡),",
        "and 30% Weakness for 5s.",
        "and fire (🔥) damage.",
        "and projectile (🏹) damage.",
        "At 8 stacks, deals 25% max HP damage",
        "ᴀᴘᴘʟɪᴇѕ ᴀѕᴘᴇᴄᴛѕ",
        "Blinded mobs cannot attack, but",
        "ʙʟᴇᴇᴅ",
        "ʙʟɪɴᴅ",
        "can still cast spells.",
        "Cumbersome weapons contribute 33.3% less weapon damage.",
        "Damage dealt applies certain enchants.",
        "Damage values will change depending",
        "Decay, Bleeding, and Impact)",
        "ᴇɴᴄʜᴀɴᴛᴇᴅ ᴡᴇᴀᴋɴᴇѕѕ",
        "ᴇxᴛʀᴀ ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ",
        "ᴇxᴛʀᴀ ᴛʀɪɢɢᴇʀѕ",
        "in the ability trigger menu.",
        "Inflicts 3% Weakness per stack.",
        "No extra information to display.",
        "on the region you're currently in.",
        "projectile (🏹), magic (⭐), blast (☀)",
        "ʀᴇɢɪᴏɴ ѕᴄᴀʟᴇᴅ",
        "Staggered mobs cannot attack and",
        "their spells will be delayed.",
        "Vulnerability is half as effective on Bosses.",
        "Vulnerable mobs take increased damage.",
        "ᴠᴜʟɴᴇʀᴀʙʟᴇ",
        "Weakened mobs deal reduced melee (🗡)",
        "ᴡᴇᴀᴋɴᴇѕѕ",
        "ѕᴛᴀɢɢᴇʀ"
    );
    private static final String[][] PARTS={
        {"Extra Information","额外说明"},{"Change Ability Triggers","调整技能触发方式"},
        {"ᴇxᴛʀᴀ ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ","额外说明"},{"ʀᴇɢɪᴏɴ ѕᴄᴀʟᴇᴅ","区域缩放"},
        {"ᴇɴᴄʜᴀɴᴛᴇᴅ ᴡᴇᴀᴋɴᴇѕѕ","强化虚弱"},{"ᴡᴇᴀᴋɴᴇѕѕ","虚弱"},{"ᴠᴜʟɴᴇʀᴀʙʟᴇ","易伤"},
        {"ᴀᴘᴘʟɪᴇѕ ᴀѕᴘᴇᴄᴛѕ","触发附魔"},{"ᴇxᴛʀᴀ ᴛʀɪɢɢᴇʀѕ","额外触发方式"},{"ʙʟᴇᴇᴅ","流血"},{"ʙʟɪɴᴅ","致盲"},{"ѕᴛᴀɢɢᴇʀ","僵直"},
        {"No extra information to display.","暂无额外说明。"},
        {"Damage values will change depending","伤害数值会随"},{"on the region you're currently in.","你当前所在区域变化。"},
        {"A stronger form of ","更强的"},
        {"Weakened mobs deal reduced melee ","虚弱的怪物造成的近战"},
        {"Afflicted mobs deal reduced melee ","受影响的怪物造成的近战"},
        {"and projectile ","与弹射物"},{"projectile ","弹射物"},{"magic ","魔法"},{"blast ","爆炸"},{"and fire ","与火焰"},{" damage.","伤害降低。"},
        {"Vulnerable mobs take increased damage.","易伤的怪物受到的伤害提高。"},
        {"Vulnerability is half as effective on Bosses.","易伤对首领的效果减半。"},
        {"Staggered mobs cannot attack and","处于僵直状态的怪物无法攻击，"},{"their spells will be delayed.","其技能施放也会被推迟。"},
        {"Blinded mobs cannot attack, but","致盲的怪物无法攻击，"},{"can still cast spells.","但仍可施法。"},
        {"Damage dealt applies certain enchants.","造成的伤害会触发特定附魔。"},
        {"Fire, Ice, Thunder, and Wind Aspect,","火焰、冰霜、雷霆和疾风附加，以及"},{"Decay, Bleeding, and Impact","衰败附加、流血和撞击"},
        {"Additional triggers can be customized","可以在技能触发方式菜单中"},{"in the ability trigger menu.","自定义额外的触发方式。"},
        {"Alternate unprime trigger","取消准备的备用触发方式"},{"Alternate dash trigger","冲刺的备用触发方式"},{"Swap between healing/damage modes","切换治疗／伤害模式"},
        {"Inflicts ","每层施加"},{"Weakness","虚弱"},{" per stack.","。"},{"At ","达到"},{" stacks, deals ","层时，造成最大生命值"},{" max HP damage","的伤害"},{"and ","并施加"},{" for ", "，持续"},{"5s","5秒"},{", ","、"},{",","、"},{"(","（"},{")","）"},{"Reduced effectiveness on bosses","对首领效果降低"},
        {"Cumbersome weapons contribute 33.3% less weapon damage.","笨重武器提供的武器伤害降低 33.3%。"},
        {"View and change which keybinds","查看并修改施放技能时"},{"are used to cast abilities.","使用的按键。"},
        {"Press ","按下 "},{" to go back!"," 返回！"},{"Click to remove this ability.","点击移除此技能。"}
    };
    static { Arrays.sort(PARTS, Comparator.comparingInt((String[] pair)->pair[0].length()).reversed()); }
    public static void compact(List<class_2561> source,List<class_2561> translated) {
        if(source.size()!=translated.size())return;

        for(int i=0;i<source.size();i++) {
            if(i==0)continue; // retain the skill's established translated name
            if(!ROWS.contains(source.get(i).getString().strip())&&!(source.get(i).getString().strip().startsWith("|| Press ") && source.get(i).getString().strip().endsWith(" to go back!")))continue;
            JsonElement tree=class_2561.class_2562.method_10868(source.get(i)).deepCopy();
            List<JsonObject> leaves=new ArrayList<>(); collect(tree,leaves);
            boolean changed=false;
            for(String[] pair:PARTS)while(replace(leaves,pair[0],pair[1]))changed=true;
            if(source.get(i).getString().strip().equals("A stronger form of Weakness."))changed|=replace(leaves,".","效果。");

            if(source.get(i).getString().strip().equals("and 30% Weakness for 5s."))changed|=replace(leaves,".","。");
            if(changed) {
                class_2561 result=class_2561.class_2562.method_10872(tree);
                if(result!=null)translated.set(i,result);
            }
        }
    }
    private static void collect(JsonElement e,List<JsonObject> leaves) {
        if(e.isJsonObject()) {
            JsonObject o=e.getAsJsonObject();
            if(o.has("text"))leaves.add(o);
            if(o.has("extra"))collect(o.get("extra"),leaves);
        }else if(e.isJsonArray()) {
            JsonArray a=e.getAsJsonArray();
            for(int i=0;i<a.size();i++) {
                JsonElement child=a.get(i);
                if(child.isJsonPrimitive()&&child.getAsJsonPrimitive().isString()) {
                    JsonObject o=new JsonObject();o.addProperty("text",child.getAsString());a.set(i,o);child=o;
                }
                collect(child,leaves);
            }
        }
    }
    /** Match across per-word components, but attach translated text to its original semantic donor. */
    private static boolean replace(List<JsonObject> leaves,String from,String to) {
        StringBuilder all=new StringBuilder();for(JsonObject o:leaves)all.append(o.get("text").getAsString());
        int start=all.indexOf(from);if(start<0)return false;
        int end=start+from.length(),offset=0;boolean inserted=false;
        for(JsonObject o:leaves) {
            String s=o.get("text").getAsString();int next=offset+s.length();
            if(offset<end&&next>start) {
                int a=Math.max(0,start-offset),b=Math.min(s.length(),end-offset);
                o.addProperty("text",s.substring(0,a)+(inserted?"":to)+s.substring(b));inserted=true;
            }
            offset=next;
        }
        return true;
    }
}
