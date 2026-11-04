package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Contextual display labels. Commands, hover metadata, book authors and player arguments stay original. */
public final class NameLocalizer {
    public record Name(String english, String chinese, boolean npc) {}
    private static final Name KIM_ANNOTATION=new Name("Kim","金中士",true);
    private final List<Name> names=new ArrayList<>();
    private final Map<String,Name> npcByName=new HashMap<>();
    
    private final Map<String,Pattern> englishPatterns=new HashMap<>();
    private final Set<String> labels=new HashSet<>();
    private final Set<String> guildLabels=new HashSet<>();
    public NameLocalizer(JsonObject catalog){add(catalog);}
    public void add(JsonObject catalog) {
        for(String section:List.of("npc_names","place_names")) {
            if(!catalog.has(section))continue;
            for(var entry:catalog.getAsJsonObject(section).entrySet()) {
                String en=TranslationEngine.normalize(entry.getKey()),zh=entry.getValue().getAsString().replaceAll("§[0-9A-FK-ORa-fk-or]","").strip();
                if(en.isBlank()||zh.isBlank()||en.equals(zh))continue;
                Name name=new Name(en,zh,section.equals("npc_names"));
                englishPatterns.put(en,Pattern.compile("(?<![A-Za-z0-9_])"+Pattern.quote(en)+"(?![A-Za-z0-9_])"));
                names.removeIf(old->old.english().equals(en)&&old.npc()==name.npc());names.add(name);
                if(name.npc()){npcByName.entrySet().removeIf(e->e.getValue().english().equals(en));npcByName.put(en,name);npcByName.put(zh,name);}
            }
        }
        if(catalog.has("guild_labels"))for(var v:catalog.getAsJsonArray("guild_labels"))guildLabels.add(TranslationEngine.normalize(v.getAsString()));
        CapturePrivacy.catalog(catalog);
        names.sort(Comparator.comparingInt((Name n)->n.chinese().length()).reversed());
        labels.clear();for(Name n:names){labels.add(n.english());labels.add(n.chinese());}
    }
    public void observePlayer(String name){if(name!=null)CapturePrivacy.observePlayer(name);}
    public boolean guildIdentity(String value){return guildLabels.contains(TranslationEngine.normalize(value));}
    public boolean playerIdentity(String value){return CapturePrivacy.playerName(value);}
    public Name npc(String raw){return npcByName.get(TranslationEngine.normalize(raw));}
    /** Discovery subtitles are complete labels, not the animated prefixes used by town arrivals. */
    public Name exactPlace(String raw) {
        String source=TranslationEngine.normalize(raw);
        for(Name n:names)if(!n.npc()&&(source.equals(n.english())||source.equals(n.chinese())
            ||source.equals(n.chinese()+"（"+n.english()+"）")))return n;
        return null;
    }
    /** Arrival animations may send an unambiguous prefix; only catalogued places qualify. */
    public Name arrivalPlace(String raw) {
        String source=TranslationEngine.normalize(raw);List<Name> prefixes=new ArrayList<>();
        for(Name n:names)if(!n.npc()&&(source.equals(n.english())||source.equals(n.chinese())||source.equals(n.chinese()+"（"+n.english()+"）")))return n;
        for(Name n:names)if(!n.npc()) {
            if(source.length()>1&&n.english().startsWith(source)) {
                prefixes.add(n);
            }
        }
        if(prefixes.isEmpty())return null;
        Name base=prefixes.stream().min(Comparator.comparingInt(n->n.english().length())).orElseThrow();
        return prefixes.stream().allMatch(n->n.chinese().equals(base.chinese())||n.english().startsWith(base.english()+" "))?base:null;
    }
    public String withoutKnownReferences(String text) {
        for(Name n:names)text=text.replace("（"+n.english()+"）","").replace("("+n.english()+")","");
        return text;
    }
    public boolean knownLabel(String raw){return labels.contains(TranslationEngine.normalize(raw));}
    public static boolean cleanLegacyCodes(JsonElement value) {
        if(value==null)return false;
        if(value.isJsonArray()) {
            boolean changed=false;JsonArray array=value.getAsJsonArray();
            for(int i=0;i<array.size();i++){JsonElement child=array.get(i);if(child.isJsonPrimitive()&&child.getAsJsonPrimitive().isString()){String raw=child.getAsString(),clean=raw.replaceAll("§[0-9A-FK-ORa-fk-or]","");if(!raw.equals(clean)){array.set(i,new JsonPrimitive(clean));changed=true;}}else changed|=cleanLegacyCodes(child);}
            return changed;
        }
        if(!value.isJsonObject())return false;JsonObject o=value.getAsJsonObject();boolean changed=false;
        if(o.has("text")){String raw=o.get("text").getAsString(),clean=raw.replaceAll("§[0-9A-FK-ORa-fk-or]","");if(!clean.equals(raw)){o.addProperty("text",clean);changed=true;}}
        if(o.has("extra"))changed|=cleanLegacyCodes(o.get("extra"));
        return changed;
    }
    /** Names captured only as dialogue prefixes also need standalone translations on signs and labels. */
    public void fillMissingTranslations(TranslationEngine engine) {
        JsonObject exact=new JsonObject();
        for(Name n:names) {
            if(engine.translate(n.english(),false).equals(n.english()))exact.addProperty(n.english(),n.chinese());
            if(n.npc()) {
                String prefix="["+n.english()+"]";
                if(engine.translate(prefix,false).equals(prefix))exact.addProperty(prefix,"["+n.chinese()+"]");
            }
        }
        JsonObject dictionary=new JsonObject();dictionary.add("exact",exact);engine.add(dictionary);
    }
    public boolean rewrite(JsonElement translated,String original,boolean npcNames,boolean places,Set<String> protectedNames) {
        String normalized=TranslationEngine.normalize(original);
        List<Name> referenced=names.stream().filter(n->(n.npc()?npcNames:places)&&!annotationProtected(n,protectedNames)
            &&!(n.npc()&&n.english().equals("White")&&whiteColorReference(original))
            &&(containsEnglish(original,n.english())||original.contains(annotationName(n).chinese())))
            .map(NameLocalizer::annotationName).distinct()
            .sorted(Comparator.comparingInt((Name n)->n.chinese().length()).reversed()
                .thenComparingInt(n->normalized.equals(n.english())?0:1)
                .thenComparing(Comparator.comparingInt((Name n)->n.english().length()).reversed())).toList();
        if(referenced.isEmpty())return false;
        List<JsonElement> leaves=new ArrayList<>();collectLeaves(translated,leaves);
        IdentityHashMap<JsonElement,String> replacements=new IdentityHashMap<>();
        List<String> chunks=new ArrayList<>();List<Integer> starts=new ArrayList<>();StringBuilder joined=new StringBuilder();
        for(JsonElement leaf:leaves){String raw=leaf.isJsonObject()?leaf.getAsJsonObject().get("text").getAsString():leaf.getAsString();starts.add(joined.length());chunks.add(raw);joined.append(raw);}
        Map<Integer,TreeMap<Integer,String>> insertions=new HashMap<>();String full=joined.toString();
        // Wrapped POIs identify a complete registered source place. A legacy Chinese alias
        // can differ from the catalogue, so annotate the wrapper rather than guessing a name.
        if(places&&normalized.startsWith("[[")&&normalized.endsWith("]]")) {
            Name place=exactPlace(normalized.substring(2,normalized.length()-2).strip());
            String label=TranslationEngine.normalize(full);
            if(place!=null&&!annotationProtected(place,protectedNames)&&label.startsWith("[[")&&label.endsWith("]]")) {
                if(full.contains("（"+place.english()+"）")||full.contains("("+place.english()+")"))return false;
                int end=full.lastIndexOf("]]");
                while(end>0&&Character.isWhitespace(full.charAt(end-1)))end--;
                if(full.substring(0,end).codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)) {
                    for(int i=0;i<leaves.size();i++)if(starts.get(i)<end&&starts.get(i)+chunks.get(i).length()>=end) {
                        if(protectedNames.contains(TranslationEngine.normalize(chunks.get(i))))return false;
                        StringBuilder value=new StringBuilder(chunks.get(i));value.insert(end-starts.get(i),"（"+place.english()+"）");
                        replacements.put(leaves.get(i),value.toString());
                        return rewriteNode(translated,replacements);
                    }
                }
            }
        }
        for(int at=0;at<full.length();) {
            Name match=null;for(Name n:referenced)if(annotationAt(n,full,at)){match=n;break;}
            if(match==null){at++;continue;}
            int end=at+match.chinese().length();String suffix="（"+match.english()+"）";
            // An exact reference to this same name may precede its Chinese label across color runs.
            if(alreadyAnnotated(match,full,at,end)){at=end;continue;}
            int target=-1;boolean protectedRun=false;
            for(int i=0;i<leaves.size();i++) {
                int start=starts.get(i),stop=start+chunks.get(i).length();
                if(stop>at&&start<end&&protectedNames.contains(TranslationEngine.normalize(chunks.get(i))))protectedRun=true;
                if(target<0&&stop>=end&&start<end)target=i;
            }
            if(target>=0&&!protectedRun)insertions.computeIfAbsent(target,k->new TreeMap<>(Comparator.reverseOrder())).put(end-starts.get(target),suffix);
            at=end;
        }
        for(var entry:insertions.entrySet()) {
            StringBuilder value=new StringBuilder(chunks.get(entry.getKey()));
            for(var addition:entry.getValue().entrySet())value.insert(addition.getKey(),addition.getValue());
            replacements.put(leaves.get(entry.getKey()),value.toString());
        }
        return rewriteNode(translated,replacements);
    }
    private static boolean whiteColorReference(String source) {
        String plain=source.replaceAll("§[0-9A-FK-ORa-fk-or]","");
        return Pattern.compile("(?i)(?<![A-Za-z0-9_])White[ -]+(?:Wool|Fragment|Concrete|Terracotta|Glass|Stained|Carpet|Banner|Dye|Tulip|Shulker|Candle|Bed|Dungeon)\\b").matcher(plain).find()
            ||Pattern.compile("白色(?:羊毛|碎片|混凝土|陶瓦|玻璃|地毯|旗帜|染料|郁金香|潜影盒|蜡烛|床|地牢)").matcher(plain).find();
    }
    /** A single 金 is a common noun fragment; only the complete sergeant label identifies this NPC. */
    private static Name annotationName(Name name) {
        return name.npc()&&(name.english().equals("Kim")||name.english().equals("Sergeant Kim"))
            &&(name.chinese().equals("金")||name.chinese().equals("金中士"))?KIM_ANNOTATION:name;
    }
    private static boolean annotationProtected(Name name,Set<String> protectedNames) {
        return protectedNames.contains(name.english())||annotationName(name)==KIM_ANNOTATION
            &&(protectedNames.contains("Kim")||protectedNames.contains("Sergeant Kim"));
    }
    private static boolean annotationAt(Name name,String text,int at) {
        if(!text.startsWith(name.chinese(),at))return false;
        // These are bounty/place nouns, rather than a reference to the NPC called The King.
        return !(name.npc()&&name.chinese().equals("国王")&&(text.startsWith("国王悬赏",at)
            ||text.startsWith("国王谷",at)||text.startsWith("国王峡谷",at)));
    }
    private static boolean alreadyAnnotated(Name name,String text,int start,int end) {
        int before=start,after=end;
        while(before>0&&Character.isWhitespace(text.charAt(before-1)))before--;
        while(after<text.length()&&Character.isWhitespace(text.charAt(after)))after++;
        return hasAnnotation(text,before,after,name.english())
            ||name==KIM_ANNOTATION&&hasAnnotation(text,before,after,"Sergeant Kim");
    }
    private static boolean hasAnnotation(String text,int before,int after,String english) {
        String label="（"+english+"）",ascii="("+english+")";
        return text.startsWith(label,before-label.length())||text.startsWith(label,after)
            ||text.startsWith(ascii,before-ascii.length())||text.startsWith(ascii,after);
    }
    /** Keep an existing reference intact when font/color splitting leaves its English in a separate node. */
    public static boolean hasKimAnnotation(String source) {
        if(source==null||!source.contains("Kim"))return false;
        String text=TranslationEngine.normalize(source);
        for(int at=text.indexOf("金中士");at>=0;at=text.indexOf("金中士",at+3))
            if(alreadyAnnotated(KIM_ANNOTATION,text,at,at+3))return true;
        for(String english:List.of("Kim","Sergeant Kim"))
            if(text.contains("金（"+english+"）中士")||text.contains("金("+english+")中士"))return true;
        return false;
    }
    private boolean containsEnglish(String source,String name){return englishPatterns.get(name).matcher(source).find();}
    private static String annotate(String raw,String following,List<Name> referenced,Set<String> protectedNames) {
        if(protectedNames.contains(TranslationEngine.normalize(raw)))return raw;
        StringBuilder result=new StringBuilder();
        for(int i=0;i<raw.length();) {
            Name match=null;for(Name n:referenced)if(raw.startsWith(n.chinese(),i)){match=n;break;}
            if(match==null){result.append(raw.charAt(i++));continue;}
            int end=i+match.chinese().length();String suffix="（"+match.english()+"）",ascii="("+match.english()+")";
            result.append(match.chinese());
            if(raw.startsWith(suffix,end)){result.append(suffix);end+=suffix.length();}
            else if(raw.startsWith(ascii,end)){result.append(suffix);end+=ascii.length();}
            else if((raw.substring(end)+following).startsWith(suffix)){} // Suffix can span Minecraft font/color runs.
            else result.append(suffix);
            i=end;
        }
        return result.toString();
    }
    private static boolean rewriteNode(JsonElement value,IdentityHashMap<JsonElement,String> replacements) {
        if(value==null||value.isJsonNull())return false;boolean changed=false;
        if(value.isJsonArray()) {
            JsonArray a=value.getAsJsonArray();
            for(int i=0;i<a.size();i++) {
                JsonElement child=a.get(i);
                if(child.isJsonPrimitive()&&child.getAsJsonPrimitive().isString()) {
                    String raw=child.getAsString(),result=replacements.getOrDefault(child,raw);
                    if(!raw.equals(result)){JsonObject o=new JsonObject();TranslationEngine.replaceTranslatedText(o,result);a.set(i,o);changed=true;}
                }else changed|=rewriteNode(child,replacements);
            }
            return changed;
        }
        if(!value.isJsonObject())return false;JsonObject o=value.getAsJsonObject();
        // Walk old children only: freshly inserted bilingual runs must not be annotated twice.
        JsonElement extra=o.get("extra");
        if(o.has("text")) {
            String raw=o.get("text").getAsString(),result=replacements.getOrDefault(o,raw);
            if(!raw.equals(result)){TranslationEngine.replaceTranslatedText(o,result);changed=true;}
        }
        if(extra!=null)changed|=rewriteNode(extra,replacements);
        if(o.has("with")) {
            String key=o.has("translate")?o.get("translate").getAsString():"";
            if(!key.equals("book.byAuthor")) {
                JsonArray args=o.getAsJsonArray("with");int first=key.startsWith("death.attack.player")?2:key.startsWith("death.")||key.startsWith("chat.type.advancement.")?1:0;
                for(int i=first;i<args.size();i++) {
                    JsonElement child=args.get(i);
                    if(ChatMessageFilter.classify(child,plain(child))==ChatMessageFilter.Kind.PLAYER)continue;
                    if(child.isJsonPrimitive()) {
                        String raw=child.getAsString(),result=replacements.getOrDefault(child,raw);
                        if(!raw.equals(result)){JsonObject replacement=new JsonObject();TranslationEngine.replaceTranslatedText(replacement,result);args.set(i,replacement);changed=true;}
                    }else changed|=rewriteNode(child,replacements);
                }
            }
        }
        return changed;
    }
    private static void collectLeaves(JsonElement value,List<JsonElement> leaves) {
        if(value==null||value.isJsonNull())return;
        if(value.isJsonPrimitive()){if(value.getAsJsonPrimitive().isString())leaves.add(value);return;}
        if(value.isJsonArray()){for(var child:value.getAsJsonArray())collectLeaves(child,leaves);return;}
        JsonObject o=value.getAsJsonObject();if(o.has("text"))leaves.add(o);
        if(o.has("extra"))collectLeaves(o.get("extra"),leaves);
        if(!o.has("with"))return;
        String key=o.has("translate")?o.get("translate").getAsString():"";if(key.equals("book.byAuthor"))return;
        JsonArray args=o.getAsJsonArray("with");int first=key.startsWith("death.attack.player")?2:key.startsWith("death.")||key.startsWith("chat.type.advancement.")?1:0;
        for(int i=first;i<args.size();i++) {
            // Translation arguments have intervening template words; they are not adjacent font runs.
            leaves.add(new JsonPrimitive("\u0000"));
            var child=args.get(i);if(ChatMessageFilter.classify(child,plain(child))!=ChatMessageFilter.Kind.PLAYER)collectLeaves(child,leaves);
            leaves.add(new JsonPrimitive("\u0000"));
        }
    }
    private static String plain(JsonElement v) {
        if(v==null||v.isJsonNull())return"";if(v.isJsonPrimitive())return v.getAsString();
        if(v.isJsonArray()){StringBuilder b=new StringBuilder();for(var c:v.getAsJsonArray())b.append(plain(c));return b.toString();}
        JsonObject o=v.getAsJsonObject();return(o.has("text")?o.get("text").getAsString():"")+(o.has("extra")?plain(o.get("extra")):"");
    }
}
