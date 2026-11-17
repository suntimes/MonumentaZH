package monumentazh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.*;

/** Guarded complete skill-card paragraphs; ClassMenuTooltip owns native width wrapping afterwards. */
public final class ClassSkillTooltip {
    private record Rule(String id,List<String> source,List<String> target,String guard,String expected,List<String> rows) {}
    private record Replacement(int end,class_2561 text) {}
    private record Run(String text,class_2583 style) {}
    private record Highlight(int start,int end,class_2583 style) {}
    private static final Map<String,List<Rule>> RULES=load();
    private static final java.util.regex.Pattern POINT_BALANCE=java.util.regex.Pattern.compile("You have (\\d+) out of (\\d+) (Skill|Specialization|Enhancement) Points remaining\\.");
    private static final Set<String> GENERIC=Set.of("a","an","the","you","your","mobs","players","nearby","damage","seconds","blocks","and","or","while","with","on","at","for","in","of","per","to","this","that","it","them","each","all");
    private ClassSkillTooltip() {}
    public static boolean apply(List<class_2561> original,List<class_2561> displayed,TranslationEngine engine) {
        return applyRules(original,displayed,engine,RULES);
    }
    // Package-local seam uses the real Text/TranslationEngine implementations in native tests.
    static boolean applyForTest(List<class_2561> original,List<class_2561> displayed,TranslationEngine engine,JsonObject resource) {
        return applyRules(original,displayed,engine,parse(resource));
    }
    private static boolean applyRules(List<class_2561> original,List<class_2561> displayed,TranslationEngine engine,Map<String,List<Rule>> rules) {
        if(original==null||displayed==null||engine==null||original.isEmpty()||original.size()!=displayed.size()||original.get(0)==null)return false;
        // Hidden class cards must not reach paragraph, title, or style reconstruction.
        for(class_2561 line:original)if(line!=null&&TranslationEngine.hasHiddenText(class_2561.class_2562.method_10868(line)))return false;
        for (int row=1; row<original.size(); row++) {
            class_2561 revised=ClassTextColors.restore(original.get(row),displayed.get(row),engine);
            if (revised!=displayed.get(row)) {
                ClassMenuTooltip.copyLayout(displayed.get(row),revised);
                displayed.set(row,revised);
            }
        }
        if(SkillExtraPage.matches(original)) {
            displayed.set(0,repairTitle(original.get(0),displayed.get(0)));
            SkillExtraPage.compact(original,displayed);
            return false; // no paragraph merging on the server's extra-information page
        }
        for(int i=1;i<original.size();i++)if(original.get(i)!=null&&displayed.get(i)!=null)ClassMenuTooltip.rememberLayout(original.get(i),displayed.get(i));
        displayed.set(0,repairTitle(original.get(0),displayed.get(0)));
        repairCardStyles(original,displayed,engine);
        List<Rule> candidates=rules.get(key(original.get(0).getString()));
        String pointKind=switch(key(original.get(0).getString())) {
            case "Skill Points" -> "Skill";
            case "Specialization Points" -> "Specialization";
            case "Enhancement Points" -> "Enhancement";
            default -> null;
        };
        if(candidates==null&&pointKind==null)return false;
        if(candidates==null)candidates=List.of();
        Map<Integer,Replacement> planned=new LinkedHashMap<>();
        for(int at=1;at<original.size();at++) {
            Replacement balance=pointKind==null?null:pointBalance(original,at,pointKind,engine);
            if(balance!=null){planned.put(at,balance);at=balance.end()-1;continue;}
            for(Rule rule:candidates) {
            int end=at+rule.source().size();
            if(end>original.size()||!Objects.equals(engine.exactTranslation(rule.guard()),rule.expected()))continue;
            boolean matches=true;
            for(int i=0;i<rule.source().size();i++) {
                class_2561 line=original.get(at+i);
                if(line==null||!key(line.getString()).equals(key(rule.source().get(i)))
                    ||!Objects.equals(engine.exactTranslation(rule.source().get(i)),rule.rows().get(i))
                    ||unsupported(class_2561.class_2562.method_10868(line))) {matches=false;break;}
            }
            if(!matches)continue;
            class_5250 combined=class_2561.method_43473();
            for(int i=at;i<end;i++) {
                if(i>at)combined.method_27693(" ");
                combined.method_10852(ClassMenuTooltip.trim(original.get(i)));
            }
            List<Run> runs=runs(combined);class_2583 base=baseStyle(runs);
            combined.method_10862(base);
            boolean description=Set.of("Hunter","Ranger","Berserker","Guardian","Assassin","Swordsage","Elementalist","Arcanist","Reaper","Tenebrist","Paladin","Seraph","Harbinger","Apothecary","Hexbreaker","Soothsayer").contains(key(original.get(0).getString()));
            for(int prior=1;description&&prior<at;prior++)if(!original.get(prior).getString().isBlank()&&!ClassMenuTooltip.separator(original.get(prior)))description=false;
            String target=String.join(description?"\n":"",rule.target().stream().map(String::strip).toList());
            if(key(original.get(0).getString()).startsWith("Ethereal Ascension")&&target.endsWith("（1.5秒冷却）"))target=target.substring(0,target.lastIndexOf("（1.5秒冷却）"))+"\n（1.5秒冷却）";
            for(String note:List.of("（每次施放最多触发一次）","（每次跃起仅可施放一次）","(每次跃起仅可施放一次)"))if(target.endsWith(note)&&!target.equals(note))target=target.substring(0,target.length()-note.length())+"\n"+note;
            class_2561 translated=MonumentaZh.translatedTooltipLine(combined,target);
            translated=ClassTextColors.restore(combined,translated,engine);
            translated=highlights(translated,runs,target,base,engine,original.get(0).getString());
            ClassMenuTooltip.rememberLayout(original.get(at),translated);
            planned.put(at,new Replacement(end,translated));at=end-1;break;
            }
        }
        if(planned.isEmpty())return false;
        List<class_2561> result=new ArrayList<>();
        for(int i=0;i<displayed.size();) {
            Replacement replacement=planned.get(i);
            if(replacement==null)result.add(displayed.get(i++));
            else {appendRows(result,replacement.text());i=replacement.end();}
        }
        displayed.clear();displayed.addAll(result);repairCardStyles(original,displayed,engine);return true;
    }
    private static void appendRows(List<class_2561> result,class_2561 text) {
        if(!text.getString().contains("\n")){result.add(text);return;}
        class_5250[] row={class_2561.method_43473()};
        text.method_27658((style,value)->{
            String[] pieces=value.split("\n",-1);
            for(int i=0;i<pieces.length;i++) {
                if(i>0){ClassMenuTooltip.copyLayout(text,row[0]);result.add(row[0]);row[0]=class_2561.method_43473();}
                row[0].method_10852(class_2561.method_43470(pieces[i]).method_10862(style));
            }
            return Optional.empty();
        },class_2583.field_24360);
        ClassMenuTooltip.copyLayout(text,row[0]);result.add(row[0]);
    }
    /** Preserve the effective name and level styles separately, including inherited flags. */
    static class_2561 repairTitle(class_2561 source,class_2561 translated) {
        if(source==null||translated==null)return translated;
        String raw=source.getString(),target=translated.getString();
        if(key(raw).equals("Hunter")&&target.strip().equals("猎手"))target="猎人";
        var level=java.util.regex.Pattern.compile("\\s*\\[(?:Lv\\.|等级)\\s*\\d+\\]\\s*$").matcher(raw);
        int cut=level.find()?level.start():raw.length();
        class_2583[] nameStyle={null},levelStyle={null};int[] offset={0};
        final int boundary=cut;
        source.method_27658((style,value)->{
            for(int i=0;i<value.length();i++)if(!Character.isWhitespace(value.charAt(i))) {
                if(offset[0]+i<boundary&&nameStyle[0]==null)nameStyle[0]=style;
                if(offset[0]+i>=boundary&&levelStyle[0]==null)levelStyle[0]=style;
            }
            offset[0]+=value.length();return Optional.empty();
        },class_2583.field_24360);
        if(nameStyle[0]==null)return translated;
        var targetLevel=java.util.regex.Pattern.compile("\\s*\\[(?:Lv\\.|等级)\\s*\\d+\\]\\s*$").matcher(target);
        int split=targetLevel.find()?targetLevel.start():target.length();
        class_5250 out=MonumentaZh.translatedLine(class_2561.method_43470("").method_10862(nameStyle[0]),target.substring(0,split)).method_27661();
        if(split<target.length())out.method_10852(MonumentaZh.translatedLine(class_2561.method_43470("").method_10862(levelStyle[0]==null?nameStyle[0]:levelStyle[0]),target.substring(split)));
        return out;
    }    /** A balance is one sentence even when the server splits it before or after "Points". */
    private static Replacement pointBalance(List<class_2561> original,int at,String kind,TranslationEngine engine) {
        class_5250 combined=class_2561.method_43473();
        for(int end=at;end<Math.min(at+3,original.size());end++) {
            class_2561 line=original.get(end);
            if(line==null||!prose(line.getString())||unsupported(class_2561.class_2562.method_10868(line)))return null;
            if(end>at)combined.method_27693(" ");
            combined.method_10852(ClassMenuTooltip.trim(line));
            String raw=key(combined.getString());var match=POINT_BALANCE.matcher(raw);
            if(!match.matches())continue;
            if(!match.group(3).equals(kind))return null;
            // Honor an explicitly supplied whole-sentence translation, never a partial-row template.
            String target=engine.exactTranslation(raw);
            if(target==null) {
                String label=switch(kind){case "Skill"->"技能点";case "Specialization"->"专精点";default->"强化点";};
                target="剩余"+label+"："+match.group(1)+"／"+match.group(2)+"。";
            }
            combined.method_10862(baseStyle(runs(combined)));
            if((key(original.get(0).getString()).startsWith("Tactical Maneuver")||key(original.get(0).getString()).startsWith("Meteor Slam"))&&target.endsWith("）")&&target.contains("（")) {int note=target.lastIndexOf("（");target=target.substring(0,note)+"\n"+target.substring(note);}
            class_2561 translated=MonumentaZh.translatedTooltipLine(combined,target);
            String label=switch(kind){case "Skill"->"技能点";case "Specialization"->"专精点";default->"强化点";};
            int sourceAt=combined.getString().indexOf(kind),targetAt=target.indexOf(label),offset=0;
            class_2583 labelStyle=null;
            for(Run run:runs(combined)) {
                if(sourceAt>=offset&&sourceAt<offset+run.text().length()){labelStyle=run.style();break;}
                offset+=run.text().length();
            }
            if(labelStyle!=null&&targetAt>=0) {
                class_5250 out=class_2561.method_43473();int position=0;
                for(Run run:runs(translated)) {
                    for(int localIndex=0;localIndex<run.text().length();) {
                        int absolute=position+localIndex,bound=position+run.text().length();boolean in=absolute>=targetAt&&absolute<targetAt+label.length();
                        if(absolute<targetAt)bound=Math.min(bound,targetAt);else if(in)bound=Math.min(bound,targetAt+label.length());
                        out.method_10852(MonumentaZh.translatedLine(class_2561.method_43470("").method_10862(in?labelStyle:run.style()),run.text().substring(localIndex,bound-position)));
                        localIndex=bound-position;
                    }
                    position+=run.text().length();
                }
                translated=out;
            }
            ClassMenuTooltip.rememberLayout(original.get(at),translated);
            return new Replacement(end+1,translated);
        }
        return null;
    }
    private static List<Run> runs(class_2561 text) {
        List<Run> out=new ArrayList<>();text.method_27658((style,value)->{out.add(new Run(value,style));return Optional.empty();},class_2583.field_24360);return out;
    }
    private static class_2583 baseStyle(List<Run> runs) {
        Map<class_2583,Integer> weights=new LinkedHashMap<>();
        for(Run run:runs)weights.merge(run.style(),(int)run.text().codePoints().filter(c->Character.isLetter(c)).count(),Integer::sum);
        return weights.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(class_2583.field_24360);
    }
    private static class_2561 highlights(class_2561 translated,List<Run> source,String target,class_2583 base,TranslationEngine engine) {
        return highlights(translated,source,target,base,engine,"");
    }
    private static class_2561 highlights(class_2561 translated,List<Run> source,String target,class_2583 base,TranslationEngine engine,String title) {
        List<Highlight> spans=collectHighlights(source,target,base,engine,title);
        if(spans.isEmpty())return translated;
        spans.sort(Comparator.comparingInt(Highlight::start));class_5250 out=class_2561.method_43473();int offset=0;
        for(Run run:runs(translated)) {
            int local=0;
            while(local<run.text().length()) {
                int position=offset+local,next=offset+run.text().length();Highlight owner=null;
                for(Highlight span:spans) {
                    if(span.start()<=position&&position<span.end()){owner=span;next=Math.min(next,span.end());break;}
                    if(span.start()>position){next=Math.min(next,span.start());break;}
                }
                String piece=run.text().substring(local,next-offset);
                out.method_10852(owner==null?class_2561.method_43470(piece).method_10862(run.style())
                    :MonumentaZh.translatedLine(class_2561.method_43470("").method_10862(owner.style()),piece));
                local=next-offset;
            }
            offset+=run.text().length();
        }
        return out;
    }
    private static boolean underlined(class_2583 style){return class_2561.class_2562.method_10868(class_2561.method_43470("").method_10862(style)).toString().contains("\"underlined\":true");}
    /** Bridge only whitespace between identical, explicitly underlined reference runs. */
    private static List<Run> semanticRuns(List<Run> source) {
        List<Run> adjusted=new ArrayList<>(source);
        for(int i=1;i<adjusted.size()-1;i++)if(adjusted.get(i).text().isBlank()) {
            int left=i-1,right=i+1;while(left>=0&&adjusted.get(left).text().isBlank())left--;while(right<adjusted.size()&&adjusted.get(right).text().isBlank())right++;
            if(left>=0&&right<adjusted.size()&&underlined(adjusted.get(left).style())&&adjusted.get(left).style().equals(adjusted.get(right).style()))adjusted.set(i,new Run(adjusted.get(i).text(),adjusted.get(left).style()));
        }
        List<Run> merged=new ArrayList<>();for(Run run:adjusted){if(!merged.isEmpty()&&merged.get(merged.size()-1).style().equals(run.style())){Run previous=merged.remove(merged.size()-1);merged.add(new Run(previous.text()+run.text(),run.style()));}else merged.add(run);}return merged;
    }    private static List<Highlight> collectHighlights(List<Run> source,String target,class_2583 base,TranslationEngine engine,String title) {
        title=title.strip();source=semanticRuns(source);
        List<Highlight> spans=new ArrayList<>();String raw=String.join("",source.stream().map(Run::text).toList());
        Map<String,String> anchors=title.startsWith("Keeper Virtue")?Map.of("Shielding Flares?","庇护圣火","Harming Flares?","惩戒圣火","Virtue","守护天使")
            :title.startsWith("Elemental Spirits")?Map.of("Fire Spirit","烈焰精灵","Ice Spirit","冰雪精灵","Fire","火属性","Ice","冰属性"):Map.of();
        anchors=new LinkedHashMap<>(anchors);
        if(title.startsWith("Taboo")){anchors.put("Transform(?:ed)?","变形");}
        if(title.startsWith("Scorched Earth")){anchors.put("Scorch(?:ed)?","灼烧");}
        if(title.startsWith("Esoteric Enhancements")){anchors.put("Aberration","畸变体");}
        if(title.startsWith("Transmutation Ring")){anchors.put("Instability","迸裂");}
        if(title.startsWith("Ethereal Ascension")){anchors.put("Ascended","飞升");}
        if(title.startsWith("Astral Omen")){anchors.put("Omens?","征兆");}
        if(title.startsWith("Sage's Insight")){anchors.put("Insights?","洞察");}
        if(title.startsWith("Sanctified Armor")){anchors.put("Sanctif(?:y|ied)","圣光之铠");}
        if(title.startsWith("Arcane Strike")||title.startsWith("Mana Lance")){anchors.put("Arcane","奥术");}
        if(title.startsWith("Magma Shield")){anchors.put("Fire(?=\\s+damage)","火属性");}
        if(title.startsWith("Frost Nova")){anchors.put("Ice","冰属性");}
        if(title.startsWith("Hand of Light")){anchors.put("Heretics?","异端");}
        if(title.startsWith("Rending Razor")||title.startsWith("Sanguine Harvest")){anchors.put("Bleed","流血");}
        if(title.startsWith("Sanguine Harvest")){anchors.put("Mark(?:ing|ed)?","标记");}
        if(title.startsWith("Voodoo Bonds")){anchors.put("Curs(?:e|ed|es)","诅咒");anchors.put("Protect(?:s|ed)?","保护");}
        if(title.startsWith("Haunting Shades")){anchors.put("Shades?","暗影");}
        if(title.startsWith("Restless Souls")){anchors.put("Vex(?:es)?","恼鬼");}
        if(title.startsWith("Berserker")||title.startsWith("Glorious Battle")||title.startsWith("Meteor Slam")||title.startsWith("Rampage")){anchors.put("Bloodlust","血怒");}
        if(title.startsWith("Hunting Companion")){anchors.put("Fox(?:es)?","狐狸");}
        if(title.startsWith("Immolating Aura")||title.startsWith("Ritual Arts")||title.startsWith("Cyclone")||title.startsWith("Combustion Chain")){anchors.put("Immolated(?!\\s+Effect)","焚烧");}
        if(title.startsWith("Mending Remedy")||title.startsWith("Ritual Arts")||title.startsWith("Cyclone")||title.startsWith("Combustion Chain")){anchors.put("Mending","疗愈");}
        if(title.startsWith("Earthen Tremor")||title.startsWith("Ritual Arts")||title.startsWith("Cyclone")||title.startsWith("Combustion Chain")){anchors.put("Earthen","大地");}
        if(title.startsWith("Thunderstorm")||title.startsWith("Sculptor's Maul")){anchors.put("Thunder Orbs?","雷霆法球");}
        if(title.startsWith("Totemic Consecration")){anchors.put("Sacred Totems?","神圣图腾");}
        if(title.startsWith("Spiritcatcher Orbs")){anchors.put("Spiritflame","灵焰");}
        if(title.startsWith("Bodkin Blitz")||title.startsWith("Cloak and Dagger")){anchors.put("Stealth(?:ed)?","隐身");}
        if(title.startsWith("Dark Pact")){anchors.put("Pact","合约");}
        if(title.startsWith("Judgement Chain")){anchors.put("Chained","锁链连接");}
        if(title.startsWith("Spellshock")){anchors.put("Thunder","雷属性");}
        if(title.startsWith("Choleric Flames")){anchors.put("Inferno","炼狱");}
        if(title.startsWith("Unstable Amalgam")){anchors.put("Amalgam","合剂");anchors.put("Unstable","失衡");}
        if(title.startsWith("Sculptor's Maul")){anchors.put("Orb","法球");}
        if(title.startsWith("Gale Shot")){anchors.put("Gale Shots?","疾风劲射");}
        List<Map.Entry<String,String>> ordered=new ArrayList<>(anchors.entrySet());ordered.sort(Comparator.comparingInt((Map.Entry<String,String> e)->e.getKey().length()).reversed());
        for(var anchor:ordered) {
            var matcher=java.util.regex.Pattern.compile("\\b"+anchor.getKey().replace(" ","\\s+")+"\\b"+((anchor.getKey().equals("Fire")||anchor.getKey().equals("Ice"))?"(?!\\s+Spirit)":"")).matcher(raw);
            List<Integer> starts=new ArrayList<>();while(matcher.find())starts.add(matcher.start());
            if(starts.isEmpty()||starts.size()!=occurrences(target,anchor.getValue()))continue;
            int targetAt=-anchor.getValue().length();
            for(int start:starts) {
                targetAt=target.indexOf(anchor.getValue(),targetAt+anchor.getValue().length());int offset=0;class_2583 style=null;
                for(Run run:source){if(start>=offset&&start<offset+run.text().length()){style=run.style();break;}offset+=run.text().length();}
                int a=targetAt,b=a+anchor.getValue().length();
                if(style!=null&&spans.stream().noneMatch(h->a<h.end()&&b>h.start()))spans.add(new Highlight(a,b,style));
            }
        }
        int sourceOffset=0;
        for(Run run:source) {
            int runStart=sourceOffset;sourceOffset+=run.text().length();
            String word=run.text().strip();
            boolean sourceReference=underlined(run.style());
            if(run.style().equals(base)&&!sourceReference||(word.length()>160||!word.matches("(?s).*[A-Za-z].*"))||GENERIC.contains(word.toLowerCase(Locale.ROOT)))continue;
            String label=engine.translate(word,false);
            List<int[]> labels=labelMatches(target,label);
            if(label.isBlank()||labels.size()!=occurrences(raw,word))continue;
            int ordinal=occurrences(raw.substring(0,runStart),word);
            int start=labels.get(ordinal)[0],end=labels.get(ordinal)[1],a=start,b=end;
            if(start>=0) {
                boolean reference=class_2561.class_2562.method_10868(class_2561.method_43470("").method_10862(run.style())).toString().contains("\"underlined\":true");
                if(reference)spans.removeIf(h->a<h.end()&&b>h.start());
                if(spans.stream().noneMatch(h->a<h.end()&&b>h.start()))spans.add(new Highlight(start,end,run.style()));
            }
        }
        return spans;
    }

    /** Card-wide anchors preserve native keybind/translation nodes; only literal leaves are restyled. */
    private static void repairCardStyles(List<class_2561> source,List<class_2561> target,TranslationEngine engine) {
        if(source.get(0).getString().startsWith("Totemic Consecration"))for(int i=1;i<target.size();i++){
            String value=target.get(i).getString();if(value.contains("将图腾神化时，现在还会扩大其半径，并沉默范围内所有怪物。")){
                class_2561 old=target.get(i);class_2561 revised=MonumentaZh.translatedTooltipLine(old,value.replace("将图腾神化时，现在还会扩大其半径，并沉默范围内所有怪物。","将图腾神化为神圣图腾时，现在还会扩大其半径，并沉默范围内所有怪物。"));ClassMenuTooltip.copyLayout(old,revised);target.set(i,revised);
            }
        }
        if(source.get(0).getString().strip().startsWith("Challenge"))for(int i=1;i<target.size();i++){
            String value=target.get(i).getString();if(value.contains("每嘲讽一只怪物，还会")&&!value.contains("嘲讽挑战每嘲讽一只怪物")){
                class_2561 old=target.get(i);class_2561 revised=MonumentaZh.translatedTooltipLine(old,value.replace("每嘲讽一只怪物，还会","嘲讽挑战每嘲讽一只怪物，还会"));ClassMenuTooltip.copyLayout(old,revised);target.set(i,revised);
            }
        }
        class_5250 combined=class_2561.method_43473();StringBuilder translated=new StringBuilder();
        for(int i=1;i<source.size();i++){if(i>1)combined.method_27693(" ");combined.method_10852(source.get(i));}
        for(int i=1;i<target.size();i++){if(i>1)translated.append('\n');translated.append(target.get(i).getString());}
        List<Run> sourceRuns=runs(combined);List<Highlight> spans=collectHighlights(sourceRuns,translated.toString(),baseStyle(sourceRuns),engine,source.get(0).getString());
        // Exact UI labels have independently styled values on the original server card.
        String raw=combined.getString();
        for(String[] anchor:List.of(new String[]{"Ability already selected.","已选择此技能。"},new String[]{"Enhancement already selected.","已选择此强化。"},new String[]{"Click to remove this ability.","点击移除此技能。"},new String[]{"Click to reset your specialization.","点击重置你的专精。"},new String[]{"Cannot select enhancement!","无法选择此强化！"},new String[]{"Ability must be selected first!","必须先选择此技能！"},new String[]{"Disabled","未启用"},new String[]{"Bloodlust (Spec. Passive):","血怒激发\\(专精被动\\)："},new String[]{"Endurance (Spec. Passive):","持久防线\\(专精被动\\)："},new String[]{"Skill Points","技能点"},new String[]{"Specialization Points","专精点"},new String[]{"Enhancement Points","强化点"},new String[]{"Trigger:","触发方式："},new String[]{"Requirement:","(?:需求|条件)："})) {
            var sm=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(anchor[0]).replace(" ","\\E\\s+\\Q")).matcher(raw);
            var tm=java.util.regex.Pattern.compile(anchor[1]).matcher(translated);List<Integer> a=new ArrayList<>();List<int[]> z=new ArrayList<>();while(sm.find())a.add(sm.start());while(tm.find())z.add(new int[]{tm.start(),tm.end()});
            if(a.size()!=z.size())continue;
            for(int i=0;i<a.size();i++){class_2583 st=styleAt(sourceRuns,a.get(i));if(st!=null)spans.add(new Highlight(z.get(i)[0],z.get(i)[1],st));}
        }
        // Requirement and Trigger value phrases are whole semantic fields, never arbitrary italic runs.
        for(int i=1;i<source.size();i++) {
            String rawLine=source.get(i).getString().strip();boolean trigger=rawLine.startsWith("Trigger:"),requirement=rawLine.startsWith("Requirement:");
            if(!trigger&&!requirement)continue;
            int colon=source.get(i).getString().indexOf(':')+1;List<Run> rr=runs(source.get(i));
            String ss=source.get(i).getString();while(colon<ss.length()&&Character.isWhitespace(ss.charAt(colon)))colon++;
            class_2583 valueStyle=styleAt(rr,colon);
            for(int j=1;j<target.size();j++) {
                String line=target.get(j).getString();if(!(trigger?line.startsWith("触发方式："):line.startsWith("需求：")||line.startsWith("条件：")))continue;
                int offset=0;for(int k=1;k<j;k++)offset+=target.get(k).getString().length()+1;
                int begin=line.indexOf('：')+1;if(valueStyle!=null)spans.add(new Highlight(offset+begin,offset+line.length(),valueStyle));break;
            }
        }
        // These audited translations repeat an English pronoun as its named state. Repair prose only;
        // statistic labels and underlined skill references keep their independently sourced styles.
        String family=source.get(0).getString().strip();String donorPattern=null,targetPattern=null;
        if(family.startsWith("Scorched Earth")){donorPattern="Scorch(?:ed)?";targetPattern="灼烧(?!焦土|伤害)";}
        if(family.startsWith("Sage's Insight")){donorPattern="Insights?";targetPattern="(?<!贤者)洞察";}
        if(family.startsWith("Ethereal Ascension")){donorPattern="Ascend(?:ed|s|ing)?|Ascension";targetPattern="(?<!空灵)飞升";}
        if(family.startsWith("Hand of Light")){donorPattern="Heretics?";targetPattern="(?<=每个)异端";}
        if(family.startsWith("Attunement")||family.startsWith("Cyclone")||family.startsWith("Tempest's Call")||family.startsWith("Earthen Tremor")||family.startsWith("Sculptor's Maul")){donorPattern="Totem(?:'s)?";targetPattern="图腾";}
        if(family.startsWith("Devastation")){donorPattern="Totem";targetPattern="(?<=被摧毁的)图腾";}
        if(family.startsWith("Totemic Consecration")){donorPattern="Totem";targetPattern="(?<=将)图腾";}
        if(family.startsWith("Berserker")){donorPattern="Bloodlust";targetPattern="血怒(?!激发)";}
        if(family.startsWith("Immolating Aura")){donorPattern="Immolated";targetPattern="焚烧(?=效果)";}
        if(donorPattern!=null)addUniformAlias(sourceRuns,target,spans,donorPattern,targetPattern,family.startsWith("Hand of Light")||family.startsWith("Immolating Aura"));
        if(family.startsWith("Voodoo Bonds")){addUniformAlias(sourceRuns,target,spans,"Cursed","(?<=受)诅咒",false);addUniformAlias(sourceRuns,target,spans,"Curse","(?<=提高)诅咒",false);}
        if(family.startsWith("Sculptor's Maul")){addUniformAlias(sourceRuns,target,spans,"Orbs?","(?<!雷霆)法球",true);addUniformAlias(sourceRuns,target,spans,"Thunder Orbs?","雷霆法球",false);}
        if(family.startsWith("Cyclone"))addUniformAlias(sourceRuns,target,spans,"Cyclone","(?<=你的)旋风",false,true);
        if(Set.of("Mending Remedy","Immolating Aura","Earthen Tremor","Thunderstorm","Ritual Arts","Cyclone","Tempest's Call","Combustion Chain","Mountain's Soul").stream().anyMatch(family::startsWith)) {
            addUniformAlias(sourceRuns,target,spans,"Mending","疗愈",true,true);
            addUniformAlias(sourceRuns,target,spans,"Immolated","焚烧",true,true);
            addUniformAlias(sourceRuns,target,spans,"Earthen","大地",true,true);
        }
        if(spans.isEmpty())return;spans.sort(Comparator.comparingInt(Highlight::start));int offset=0;
        for(int i=1;i<target.size();i++) {
            class_2561 old=target.get(i);JsonElement json=class_2561.class_2562.method_10868(old).deepCopy();
            patchLiteralStyles(json,spans,new int[]{offset});class_2561 replacement=class_2561.class_2562.method_10872(json);
            if(replacement!=null){ClassMenuTooltip.copyLayout(old,replacement);target.set(i,replacement);}offset+=old.getString().length()+1;
        }
    }

    private static void addUniformAlias(List<Run> source,List<class_2561> target,List<Highlight> spans,String sourcePattern,String targetPattern,boolean statistics) {addUniformAlias(source,target,spans,sourcePattern,targetPattern,statistics,false); }
    private static void addUniformAlias(List<Run> source,List<class_2561> target,List<Highlight> spans,String sourcePattern,String targetPattern,boolean statistics,boolean references) {
        class_2583 donor=null;boolean uniform=true;
        for(Run run:source)if(run.text().strip().matches(sourcePattern)&&(references||!underlined(run.style()))) {
            if(donor==null)donor=run.style();else if(!donor.equals(run.style()))uniform=false;
        }
        if(donor==null||!uniform)return;int offset=0;
        for(int i=1;i<target.size();i++) {
            String line=target.get(i).getString();
            if(statistics||!line.stripLeading().startsWith("▶")){var m=java.util.regex.Pattern.compile(targetPattern).matcher(line);while(m.find()){
                int a=offset+m.start(),z=offset+m.end();spans.removeIf(h->a<h.end()&&z>h.start());spans.add(new Highlight(a,z,donor));
            }}
            offset+=line.length()+1;
        }
    }
    private static class_2583 styleAt(List<Run> runs,int position) {int offset=0;for(Run run:runs){if(position>=offset&&position<offset+run.text().length())return run.style();offset+=run.text().length();}return null;}
    private static void patchLiteralStyles(JsonElement node,List<Highlight> spans,int[] position) {
        if(node.isJsonArray()){JsonArray array=node.getAsJsonArray();for(int i=0;i<array.size();i++){JsonElement child=array.get(i);if(child.isJsonPrimitive()){JsonObject wrapped=new JsonObject();wrapped.add("text",child);array.set(i,wrapped);child=wrapped;}patchLiteralStyles(child,spans,position);}return;}
        if(!node.isJsonObject()){if(node.isJsonPrimitive())position[0]+=node.getAsString().length();return;}
        JsonObject object=node.getAsJsonObject();JsonArray oldExtra=object.has("extra")?object.getAsJsonArray("extra"):null;JsonArray pieces=null;
        if(object.has("text")&&object.get("text").isJsonPrimitive()) {
            String value=object.get("text").getAsString();int start=position[0];position[0]+=value.length();int local=0;
            if(spans.stream().anyMatch(h->start<h.end()&&start+value.length()>h.start())) {
                pieces=new JsonArray();while(local<value.length()) {
                    int absolute=start+local,end=start+value.length();Highlight owner=null;
                    for(Highlight h:spans){if(h.start()<=absolute&&absolute<h.end()){owner=h;end=Math.min(end,h.end());break;}if(h.start()>absolute){end=Math.min(end,h.start());break;}}
                    String piece=value.substring(local,end-start);JsonElement fragment;
                    if(owner==null){JsonObject plain=new JsonObject();plain.addProperty("text",piece);fragment=plain;}
                    else fragment=class_2561.class_2562.method_10868(MonumentaZh.translatedLine(class_2561.method_43470("").method_10862(owner.style()),piece));
                    pieces.add(fragment);local=end-start;
                }
                object.addProperty("text","");
            }
        } else {
            JsonObject own=object.deepCopy();own.remove("extra");class_2561 dynamic=class_2561.class_2562.method_10872(own);if(dynamic!=null){int length=dynamic.getString().length();for(Highlight h:spans)if(h.start()<=position[0]&&h.end()>=position[0]+length&&length>0){JsonElement encoded=class_2561.class_2562.method_10868(class_2561.method_43470("").method_10862(h.style()));if(encoded.isJsonObject())for(var entry:encoded.getAsJsonObject().entrySet())if(!entry.getKey().equals("text")&&!entry.getKey().equals("extra"))object.add(entry.getKey(),entry.getValue().deepCopy());break;}position[0]+=length;}
        }
        if(oldExtra!=null)patchLiteralStyles(oldExtra,spans,position);
        if(pieces!=null){if(oldExtra!=null)for(JsonElement child:oldExtra)pieces.add(child);object.add("extra",pieces);}
    }
    private static List<int[]> labelMatches(String text,String label) {
        List<int[]> matches=new ArrayList<>();if(label.isBlank())return matches;
        String pattern=java.util.regex.Pattern.quote(label);
        if(label.codePoints().allMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)){
            StringJoiner join=new StringJoiner("\\s*");label.codePoints().forEach(c->join.add(java.util.regex.Pattern.quote(new String(Character.toChars(c)))));pattern=join.toString();
        }
        var matcher=java.util.regex.Pattern.compile(pattern).matcher(text);while(matcher.find())matches.add(new int[]{matcher.start(),matcher.end()});return matches;
    }    private static int occurrences(String text,String needle) {int count=0;for(int at=0;(at=text.indexOf(needle,at))>=0;at+=needle.length())count++;return count;}
    static boolean unsupported(JsonElement node) {
        if(node==null||node.isJsonNull())return true;
        if(node.isJsonPrimitive())return !node.getAsJsonPrimitive().isString()||node.getAsString().contains("§");
        if(node.isJsonArray()){for(JsonElement child:node.getAsJsonArray())if(unsupported(child))return true;return false;}
        JsonObject object=node.getAsJsonObject();
        // Interactions cannot safely be reassigned across a reordered paragraph; leave that paragraph alone.
        for(String key:List.of("translate","keybind","selector","score","nbt","with","separator","clickEvent","hoverEvent","insertion"))if(object.has(key))return true;
        return object.has("text")&&unsupported(object.get("text"))||object.has("extra")&&unsupported(object.get("extra"));
    }
    private static String key(String text){return TranslationEngine.normalize(text);}
    private static boolean prose(String text) {
        String trimmed=text.strip();return !trimmed.isEmpty()&&trimmed.indexOf('\n')<0&&trimmed.indexOf('\r')<0&&trimmed.indexOf('§')<0
            &&!trimmed.startsWith("Trigger:")&&!trimmed.startsWith("Requirement:")&&!trimmed.startsWith("▶")
            &&!trimmed.matches("[·–—─◆=\\-]{8,}");
    }
    private static List<String> strings(JsonElement value) {
        if(value==null||!value.isJsonArray())throw new IllegalArgumentException();List<String> out=new ArrayList<>();
        for(JsonElement element:value.getAsJsonArray()){if(!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isString())throw new IllegalArgumentException();out.add(element.getAsString());}return List.copyOf(out);
    }
    private static Map<String,List<Rule>> parse(JsonObject root) {
        Map<String,List<Rule>> rules=new HashMap<>();Set<String> ids=new HashSet<>(),sequences=new HashSet<>();
        try {for(JsonElement value:root.getAsJsonArray("contexts"))try {
            JsonObject row=value.getAsJsonObject();String id=row.get("id").getAsString(),guard=row.get("guard_source").getAsString(),expected=row.get("guard_expected").getAsString();
            List<String> titles=strings(row.get("titles")),source=strings(row.get("source_lines")),target=strings(row.get("target_lines"));
            JsonArray guards=row.getAsJsonArray("guard_rows");List<String> rows=new ArrayList<>();
            for(JsonElement item:guards){if(!item.isJsonNull()&&(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString()))throw new IllegalArgumentException();rows.add(item.isJsonNull()?null:item.getAsString());}
            if(id.isBlank()||titles.isEmpty()||source.isEmpty()||source.size()!=target.size()||rows.size()!=source.size()
                ||source.stream().anyMatch(s->!prose(s))||target.stream().anyMatch(s->!prose(s))
                ||String.join(" ",source).strip().length()<12||!key(String.join(" ",source)).equals(key(guard))
                ||!String.join("",target.stream().map(String::strip).toList()).equals(expected)||ids.contains(id))continue;
            Set<String> titleKeys=new LinkedHashSet<>();for(String title:titles){if(key(title).isBlank()||!titleKeys.add(key(title)))throw new IllegalArgumentException();}
            List<String> signatures=titleKeys.stream().map(t->t+"\u0000"+String.join("\u0001",source.stream().map(ClassSkillTooltip::key).toList())).toList();
            if(signatures.stream().anyMatch(sequences::contains))continue;
            ids.add(id);sequences.addAll(signatures);Rule rule=new Rule(id,source,target,guard,expected,Collections.unmodifiableList(rows));
            for(String title:titleKeys)rules.computeIfAbsent(title,k->new ArrayList<>()).add(rule);
        } catch(RuntimeException ignored) { /* Invalid one-off rows do not break other cards. */ }}catch(RuntimeException ignored) {return Map.of();}
        Map<String,List<Rule>> frozen=new HashMap<>();for(var entry:rules.entrySet()){entry.getValue().sort(Comparator.comparingInt((Rule r)->r.source().size()).reversed());frozen.put(entry.getKey(),List.copyOf(entry.getValue()));}return Map.copyOf(frozen);
    }
    private static Map<String,List<Rule>> load() {
        try(InputStream stream=ClassSkillTooltip.class.getResourceAsStream("/class-skill-contexts.json")) {
            if(stream==null)return Map.of();try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return parse(JsonParser.parseReader(reader).getAsJsonObject());}
        } catch(Exception ignored){return Map.of();}
    }
}

























