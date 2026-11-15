package monumentazh;

import java.util.*;
import java.util.regex.*;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;

/** Recover visible source colors on class cards whose translated text inherited white padding. */
final class ClassTextColors {
    private static final int WHITE = 0xffffff;
    private static final int UNKNOWN = -1;
    private static final Pattern UNIT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(s|blocks?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZH_UNIT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([秒格])");
    private static final Pattern WORDS = Pattern.compile("[A-Za-z][A-Za-z '-]*[A-Za-z]|[A-Za-z]");
    private static final Map<String,String> TERMS = Map.ofEntries(
        Map.entry("Slowness","减速"), Map.entry("Weakness","虚弱"), Map.entry("Stun","眩晕"),
        Map.entry("Stagger","僵直"), Map.entry("Resistance","抗性"), Map.entry("Speed","速度"),
        Map.entry("blocks","格"), Map.entry("stack","层"), Map.entry("stacks","层"),
        Map.entry("Cooldown","冷却时间"), Map.entry("Stagger Duration","僵直持续时间"), Map.entry("Blocks","格"), Map.entry("Bleed","流血"), Map.entry("Silence","沉默"),
        Map.entry("Regeneration","生命恢复"), Map.entry("Root","定身"));
    private static final Pattern ICON_BRACKETS = Pattern.compile("[（(]\\s*([^\\p{L}\\p{N}\\s()（）])\\s*[)）]");
    private record Span(int start,int end,class_2583 style) {}
    private record Line(String text,List<Span> spans,int[] colors) {
        static Line from(class_2561 component) {
            StringBuilder text=new StringBuilder();
            List<Span> spans=new ArrayList<>();
            component.method_27658((style,value)->{
                int start=text.length(); text.append(value);
                if (text.length()>start) spans.add(new Span(start,text.length(),style));
                return Optional.empty();
            },class_2583.field_24360);
            int[] colors=new int[text.length()];
            for (Span span:spans) {
                var color=span.style.method_10973();
                Arrays.fill(colors,span.start,span.end,color==null ? WHITE : color.method_27716());
            }
            return new Line(text.toString(),spans,colors);
        }
    }
    private static boolean gray(int color) {
        return (color>>16)==((color>>8)&255) && (color>>16)==(color&255) && color<0xe0e0e0;
    }
    private static int normalized(int c) {
        return switch(c) {
            case '（' -> '('; case '）' -> ')'; case '：' -> ':'; case '。' -> '.';
            case '，','、' -> ','; case '！' -> '!'; case '？' -> '?'; default -> c;
        };
    }
    private static <K> void observe(Map<K,Integer> colors,K key,int color) {
        colors.merge(key,color,(a,b)->a.equals(b)?a:UNKNOWN);
    }
    private static void mark(String text,int[] donors,String phrase,int color) {
        if (phrase==null || phrase.isBlank() || !phrase.codePoints().anyMatch(ClassTextColors::han)) return;
        for (int at=text.indexOf(phrase);at>=0;at=text.indexOf(phrase,at+phrase.length()))
            Arrays.fill(donors,at,at+phrase.length(),color);
    }
    private static boolean han(int c) {return Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN;}
    static class_2561 restore(class_2561 original,class_2561 translated,TranslationEngine engine) {
        if (original==null || translated==null || original==translated) return translated;
        Line target=Line.from(translated);
        boolean hasWhite=false;
        for (int i=0;i<target.colors.length;i++)
            if(target.colors[i]==WHITE && !Character.isWhitespace(target.text.charAt(i))) {hasWhite=true; break;}
        if (ClassSkillTooltip.unsupported(class_2561.class_2562.method_10868(translated))
            || ClassSkillTooltip.unsupported(class_2561.class_2562.method_10868(original))) return translated;
        Line source=Line.from(original);
        Map<Integer,Integer> letters=new LinkedHashMap<>(), punctuation=new HashMap<>();
        for (int i=0;i<source.text.length();i++) {
            char c=source.text.charAt(i);int color=source.colors[i];
            if (Character.isLetter(c)) letters.merge(color,1,Integer::sum);
            else if (!Character.isWhitespace(c) && !Character.isDigit(c)) observe(punctuation,normalized(c),color);
        }
        int base=UNKNOWN;
        if (!letters.containsKey(WHITE)) {
            // Prefer the native prose gray to a separately highlighted term or value.
            base=letters.entrySet().stream().filter(e->gray(e.getKey()))
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(UNKNOWN);
            if (base==UNKNOWN) base=letters.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(UNKNOWN);
        }
        int[] donors=new int[target.text.length()];Arrays.fill(donors,UNKNOWN);
        Map<String,Integer> words=new HashMap<>();
        for (Span span:source.spans) {
            Matcher word=WORDS.matcher(source.text.substring(span.start,span.end));
            while (word.find()) {
                String term=word.group().strip();
                observe(words,term,source.colors[span.start+word.start()]);
            }
        }
        List<String> terms=new ArrayList<>(words.keySet());terms.sort(Comparator.comparingInt(String::length));
        for (String term:terms) {
            int color=words.get(term);
            if (color==UNKNOWN) continue;
            mark(target.text,donors,engine.exactTranslation(term),color);
            mark(target.text,donors,TERMS.get(term),color);
        }
        Map<String,Integer> units=new HashMap<>();Matcher unit=UNIT.matcher(source.text);
        while(unit.find()) observe(units,unit.group(1)+(unit.group(2).equalsIgnoreCase("s")?"秒":"格"),source.colors[unit.start(2)]);
        unit=ZH_UNIT.matcher(target.text);
        while(unit.find())donors[unit.start(2)]=units.getOrDefault(unit.group(1)+unit.group(2),UNKNOWN);
        boolean changed=false;
        for (int i=0;i<donors.length;i++) {
            char c=target.text.charAt(i);
            if (target.colors[i]!=WHITE || Character.isDigit(c)) {donors[i]=UNKNOWN; continue;}
            if(donors[i]==UNKNOWN)
                donors[i]=han(c)?base:punctuation.getOrDefault(normalized(c),UNKNOWN);
            if(donors[i]!=UNKNOWN && donors[i]!=WHITE) changed=true;
        }
        // Complete field labels and whole explanatory parentheses are semantic anchors.
        // They may already have inherited the wrong non-white gray, so white-only repair is insufficient.
        String[][] labels={{"Radius:","半径","范围"},{"Range:","射程","范围"},
            {"Direct Damage:","直接伤害"},{"Bonus Effect:","额外效果"},{"Area Damage:","范围伤害"},{"Bonus Damage:","额外伤害"},{"Bonus Effects:","额外效果"},
            {"Damage:","伤害"},{"Effect:","效果"},{"Cooldown:","冷却时间"}};
        for(String[] label:labels) {
            int at=source.text.indexOf(label[0]);
            if(at<0 || at>0 && Character.isLetter(source.text.charAt(at-1)))continue;
            int color=source.colors[at];boolean uniform=true;
            for(int i=at;i<at+label[0].length();i++)if(source.colors[i]!=color)uniform=false;
            if(!uniform)continue;
            for(int i=1;i<label.length;i++) {
                Matcher match=Pattern.compile("(?<![\\p{L}])"+Pattern.quote(label[i])+"[ ：:]*[:：]").matcher(target.text);
                while(match.find())Arrays.fill(donors,match.start(),match.end(),color);
            }
        }
        // A stack connector is prose, not part of the preceding dark-gray scaling note.
        Matcher per=Pattern.compile("\\bper\\s+Instability\\b").matcher(source.text);
        while(per.find()) {
            int color=source.colors[per.start()];
            if(source.colors[per.start()+1]!=color || source.colors[per.start()+2]!=color)continue;
            Matcher match=Pattern.compile("每层(?=迸裂)").matcher(target.text);
            while(match.find())Arrays.fill(donors,match.start(),match.end(),color);
        }
        // User-requested inline scaling phrase: one prose color, without recoloring the value.
        if(source.text.contains("(of the ability's damage)") && source.text.contains("Damage:")) {
            int at=source.text.indexOf("Damage:");int color=source.colors[at];boolean uniform=true;
            for(int i=at;i<at+7;i++)if(source.colors[i]!=color)uniform=false;
            Matcher match=Pattern.compile("(?<=伤害：)技能伤害的(?=\\d)").matcher(target.text);
            if(uniform)while(match.find())Arrays.fill(donors,match.start(),match.end(),color);
        }
        Matcher notes=Pattern.compile("\\((?:of (?:your weapon's|weapon|the attack's|potion) damage|max \\d+(?:\\.\\d+)?s|Cone-Shaped|\\d+ block softcap|Max once per cast|Can cast once per vault)\\)",Pattern.CASE_INSENSITIVE).matcher(source.text);
        while(notes.find()) {
            String note=notes.group().toLowerCase(Locale.ROOT);
            String expression=note.contains("damage")?"[（(][^（）()]*伤害[^（）()]*[）)]":
                note.contains("cone-shaped")?"[（(]锥形(?:区域)?[）)]":
                note.contains("softcap")?"[（(]\\d+格[ ]*软上限[）)]":
                note.contains("once")?"[（(]每次[^（）()]*(?:一次)[）)]":"[（(]最多[ ]*\\d+(?:\\.\\d+)?秒[）)]";
            int color=source.colors[notes.start()];boolean uniform=true;
            for(int i=notes.start();i<notes.end();i++)if(source.colors[i]!=color)uniform=false;
            if(!uniform)continue;
            Matcher match=Pattern.compile(expression).matcher(target.text);
            while(match.find())Arrays.fill(donors,match.start(),match.end(),color);
        }
        for(int i=0;i<donors.length;i++)if(donors[i]!=UNKNOWN&&donors[i]!=target.colors[i])changed=true;
        // Match the parentheses of the same damage icon, even in a reordered sentence.
        Map<String,Integer> iconEdges=new HashMap<>();
        Matcher group=ICON_BRACKETS.matcher(source.text);
        while (group.find()) {
            observe(iconEdges,"("+group.group(1),source.colors[group.start()]);
            observe(iconEdges,group.group(1)+")",source.colors[group.end()-1]);
        }
        group=ICON_BRACKETS.matcher(target.text);
        while (group.find()) {
            int left=iconEdges.getOrDefault("("+group.group(1),UNKNOWN);
            int right=iconEdges.getOrDefault(group.group(1)+")",UNKNOWN);
            if (left!=UNKNOWN && left!=target.colors[group.start()]) {donors[group.start()]=left; changed=true;}
            if (right!=UNKNOWN && right!=target.colors[group.end()-1]) {donors[group.end()-1]=right; changed=true;}
        }
        if (!changed)return translated;
        class_5250 result=class_2561.method_43473();
        for (Span span:target.spans) {
            for(int i=span.start;i<span.end;) {
                int color=donors[i],end=i+1;
                while(end<span.end && color==donors[end])end++;
                var style=color==UNKNOWN?span.style:span.style.method_36139(color);
                result.method_10852(class_2561.method_43470(target.text.substring(i,end)).method_10862(style));
                i=end;
            }
        }
        return result;
    }
}
