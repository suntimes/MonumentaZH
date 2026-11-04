package monumentazh;

import com.google.gson.*;
import net.minecraft.class_2561;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Narrow display-only translations for audited complete line/message contexts. */
public final class ContextTranslations {
    private enum ServerKind { NPC, SERVER_NOTICE }
    private record TooltipRule(List<String> sources,List<String> targets,
        String guardSource,String guardExpected,List<String> guardLines,TranslationEngine local) {}
    private record ServerRule(ServerKind kind,String source,String guardExpected,String rewrittenVisible,
        Map<String,String> guardParts,TranslationEngine local) {}
    private record Rules(Map<String,List<TooltipRule>> tooltips,Map<String,ServerRule> servers) {}
    // The resource and local dictionaries are read once. No display call opens a file.
    private static final Rules RULES=load();

    private record BookRule(String source,String guardExpected,TranslationEngine local) {}
    private static final Map<String,BookRule> BOOK_RULES=loadBooks();

    /** The caller owns this serialized copy; only exact leaves in an audited full page change. */
    public static boolean bookRewrite(JsonElement copy,String originalVisible,TranslationEngine global) {
        if(copy==null||global==null||originalVisible==null)return false;
        BookRule rule=BOOK_RULES.get(TranslationEngine.normalize(originalVisible));
        if(rule==null||!rule.guardExpected().equals(global.exactTranslation(rule.source())))return false;
        return rule.local().rewriteBookVisible(copy);
    }

    private static Map<String,BookRule> loadBooks() {
        try(InputStream stream=ContextTranslations.class.getResourceAsStream("/monumenta_book_contexts.json")) {
            if(stream==null)return Map.of();
            JsonObject root;
            try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                root=JsonParser.parseReader(reader).getAsJsonObject();
            }
            Map<String,BookRule> result=new HashMap<>();
            for(JsonElement element:root.getAsJsonArray("books")) {
                JsonObject row=element.getAsJsonObject();
                String source=row.get("source").getAsString(),guard=row.get("guard_expected").getAsString();
                if(TranslationEngine.normalize(source).isEmpty()||guard.isEmpty())throw new IOException("Empty book context");
                BookRule rule=new BookRule(source,guard,local(row.getAsJsonObject("exact")));
                if(result.putIfAbsent(TranslationEngine.normalize(source),rule)!=null)throw new IOException("Duplicate book context");
            }
            return Map.copyOf(result);
        } catch(IOException|RuntimeException error) { return Map.of(); }
    }

    private static final Pattern QUEST_LITERAL=Pattern.compile(
        "^§b§l(Shurima's Big Day Off|The Elder's Scrolls)§r§b (\\[\\d+/\\d+\\])$");
    private ContextTranslations() {}

    /** Keep the native reset before progress numbers in these two captured titles. */
    public static class_2561 questTitle(class_2561 original,TranslationEngine global) {
        if(original==null||global==null)return null;
        String raw=original.getString();
        if(!raw.contains("§b§lShurima's Big Day Off§r§b [")
            &&!raw.contains("§b§lThe Elder's Scrolls§r§b ["))return null;
        JsonElement copy=componentCopy(original);
        if(CapturePrivacy.playerComponent(copy))return null;
        if(rewriteQuestLiteral(copy,global)!=1)return null;
        // Existing tracking children keep their own dark_aqua/false style; only their
        // text follows the ordinary dictionary. All metadata stays on its source node.
        global.rewriteVisible(copy,false);
        class_2561 result=class_2561.class_2562.method_10872(copy);
        if(result==null||!key(result.getString()).equals(key(global.translate(raw,false))))return null;
        return result;
    }

    private static int rewriteQuestLiteral(JsonElement value,TranslationEngine global) {
        if(value==null||value.isJsonNull()||value.isJsonPrimitive())return 0;
        if(value.isJsonArray()) {
            int count=0;
            for(JsonElement child:value.getAsJsonArray()) {
                int changed=rewriteQuestLiteral(child,global);
                if(changed<0)return -1;
                count+=changed;
            }
            return count;
        }
        JsonObject object=value.getAsJsonObject();
        int count=rewriteQuestLiteral(object.get("extra"),global);
        if(count<0||!object.has("text")||!object.get("text").isJsonPrimitive())return count;
        String literal=object.get("text").getAsString();
        Matcher match=QUEST_LITERAL.matcher(literal);
        if(!match.matches())return count;
        String english=match.group(1),progress=match.group(2);
        String translated=global.translate(english,false);
        // Exact normalized keys can resolve to the established formatted base title.
        // Only its known aqua/bold wrapper is consumed here; other user formatting
        // still falls back instead of silently replacing a custom presentation.
        if(translated.indexOf('§')>=0) {
            if(!translated.matches("^§b§l[^§]+§r§b$"))return -1;
            translated=key(translated);
        }
        if(key(translated).equals(key(english))
            ||!key(global.translate(literal,false)).equals(key(translated+" "+progress)))return -1;
        JsonArray extra=new JsonArray();
        extra.add(questRun(translated,true,true));
        extra.add(questRun(" "+progress,false,false));
        if(object.has("extra"))for(JsonElement child:object.getAsJsonArray("extra"))extra.add(child);
        // Keep this node's original interactions and style inheritance. Explicit child
        // runs replace only its known legacy literal, not any of its original children.
        object.addProperty("text","");object.add("extra",extra);
        return count+1;
    }

    private static JsonObject questRun(String text,boolean bold,boolean chinese) {
        JsonObject run=new JsonObject();run.addProperty("text",text);
        run.addProperty("color","aqua");run.addProperty("bold",bold);
        run.addProperty("italic",false);run.addProperty("underlined",false);
        run.addProperty("strikethrough",false);run.addProperty("obfuscated",false);
        if(chinese)run.addProperty("font","minecraft:default");
        return run;
    }

    /** No snapshots or component serialization for unrelated or disabled tooltips. */
    public static boolean hasTooltipCandidate(List<class_2561> lines) {
        if(lines==null||RULES.tooltips().isEmpty())return false;
        for(class_2561 line:lines) {
            if(line==null)continue;
            String raw=line.getString();
            if(RULES.tooltips().containsKey(raw)
                ||raw.indexOf('§')>=0&&RULES.tooltips().containsKey(key(raw)))return true;
        }
        return false;
    }

    /**
     * The callback has already completed its normal display/capture flow. Only matched
     * display-list slots are replaced, atomically per complete consecutive rule.
     */
    public static boolean tooltip(List<class_2561> original,List<class_2561> displayed,
                                  TranslationEngine global) {
        if(global==null||original==null||displayed==null||original.size()!=displayed.size()
            ||RULES.tooltips().isEmpty())return false;
        boolean changed=false;
        for(int at=0;at<original.size();at++) {
            class_2561 first=original.get(at);
            if(first==null)continue;
            List<TooltipRule> candidates=RULES.tooltips().get(key(first.getString()));
            if(candidates==null)continue;
            for(TooltipRule rule:candidates) {
                int count=rule.sources().size();
                if(at+count>original.size()||!guard(global,rule.guardSource(),rule.guardExpected()))continue;
                boolean matches=true;
                for(int row=0;row<count;row++) {
                    class_2561 source=original.get(at+row);
                    if(source==null||!key(source.getString()).equals(key(rule.sources().get(row)))
                        ||!guard(global,rule.sources().get(row),rule.guardLines().get(row))) {
                        matches=false;break;
                    }
                }
                if(!matches)continue;
                List<class_2561> replacements=new ArrayList<>(count);
                for(int row=0;row<count;row++) {
                    class_2561 source=original.get(at+row);
                    JsonElement copy=componentCopy(source);
                    // Identity-bearing subtrees are never reinterpreted as item prose.
                    if(CapturePrivacy.playerComponent(copy)||!rule.local().rewriteVisible(copy,false)) {
                        matches=false;break;
                    }
                    class_2561 result=class_2561.class_2562.method_10872(copy);
                    if(result==null||!key(result.getString()).equals(key(rule.targets().get(row)))) {
                        // An unexpected source tree (e.g. a phrase split into new runs)
                        // falls back without flattening or deleting any source subtree.
                        matches=false;break;
                    }
                    replacements.add(MonumentaZh.formatNames(source,result,true));
                }
                if(!matches)continue;
                for(int row=0;row<count;row++)displayed.set(at+row,replacements.get(row));
                changed=true;at+=count-1;break;
            }
        }
        return changed;
    }

    /**
     * Returns a fresh serialized display copy, or null for the ordinary server flow.
     * Server rules require a full NPC-prefixed source or one audited complete notice,
     * and explicit post-rewrite text.
     * The caller still performs its normal generic wording/name pass afterwards.
     */
    public static JsonElement serverCopy(JsonElement displayJson,String source,TranslationEngine global) {
        if(displayJson==null||displayJson.isJsonNull()||source==null||global==null)return null;
        JsonElement enough=cshuraEnoughCopy(displayJson,source,global);
        if(enough!=null)return enough;
        JsonElement bounty=auditedBountyCopy(displayJson,source,global);
        if(bounty!=null)return bounty;
        ServerRule rule=RULES.servers().get(key(source));
        if(rule==null||ChatMessageFilter.classify(displayJson,source)==ChatMessageFilter.Kind.PLAYER
            ||!guard(global,rule.source(),rule.guardExpected()))return null;
        for(var part:rule.guardParts().entrySet())
            if(!guard(global,part.getKey(),part.getValue()))return null;
        JsonElement copy=displayJson.deepCopy();
        if(!rule.local().rewriteVisible(copy,false))return null;
        class_2561 result=class_2561.class_2562.method_10872(copy);
        return result!=null&&key(result.getString()).equals(key(rule.rewrittenVisible()))?copy:null;
    }

    /** Only the audited C'Shura sentence may reinterpret its coloured purpose leaf. */
    private static JsonElement cshuraEnoughCopy(JsonElement original,String source,TranslationEngine global) {
        String body="Enough! My purpose here is too great for you to interrupt!";
        String target="够了！我在这里的使命太过重大，容不得你打断！";
        if(!key(source).equals("[C'Shura] "+body)
            ||!target.equals(global.exactTranslation(body))
            ||!("[克舒拉] "+target).equals(global.exactTranslation("[C'Shura] "+body))
            ||ChatMessageFilter.classify(original,source)!=ChatMessageFilter.Kind.NPC
            ||CapturePrivacy.playerComponent(original))return null;
        JsonObject exact=new JsonObject();
        exact.addProperty("Enough! My", "够了！我在这里的");
        exact.addProperty("purpose", "使命");
        exact.addProperty("here is too great for you to interrupt!", "太过重大，容不得你打断！");
        JsonObject dictionary=new JsonObject();dictionary.add("exact",exact);
        JsonElement copy=original.deepCopy();
        if(!new TranslationEngine(dictionary).rewriteVisible(copy,false))return null;
        class_2561 translated=class_2561.class_2562.method_10872(copy);
        return translated!=null&&key(translated.getString()).equals("[C'Shura] "+target)?copy:null;
    }

    /** Two audited complete bounty dialogues; short leaves never enter the global dictionary. */
    private static JsonElement auditedBountyCopy(JsonElement original,String source,TranslationEngine global) {
        if(ChatMessageFilter.classify(original,source)!=ChatMessageFilter.Kind.NPC
            ||CapturePrivacy.playerComponent(original))return null;
        JsonObject exact=new JsonObject();
        String expected,rewritten;
        if(key(source).equals("[Seer] Swords upon swords await you. In the ocean, a towering Barracks is filled with foes, yet if you wish for treasure, they must be slain.")) {
            expected="[占卜师] 重重刀剑正等着你。海中一座高耸的兵营里满是敌人；若想得到宝藏，你就必须将他们消灭。";
            rewritten="[Seer] 重重刀剑正等着你。海中一座高耸的兵营里满是敌人；若想得到宝藏，你就必须将他们消灭。";
            exact.addProperty("Swords upon swords await you. In the ocean, a towering","重重刀剑正等着你。海中一座高耸的");
            exact.addProperty("Barracks","兵营");
            exact.addProperty("is filled with foes, yet if you wish for treasure, they must be slain.","里满是敌人；若想得到宝藏，你就必须将他们消灭。");
        }
        else if(key(source).equals("[Herald] Strange noises reverbate through Submerged caves in the Narsen Sea. The King demands investigation!")) {
            expected="[传令官] 纳尔森海中被淹没的洞穴回荡着奇怪的声响。国王要求前去调查！";
            rewritten="[Herald] 纳尔森海中被淹没的洞穴回荡着奇怪的声响。国王要求前去调查！";
            exact.addProperty("Strange noises reverbate through","纳尔森海中");
            exact.addProperty("Submerged","被淹没");
            exact.addProperty("caves in the Narsen Sea. The King demands investigation!","的洞穴回荡着奇怪的声响。国王要求前去调查！");
        }
        else return null;
        if(!expected.equals(global.exactTranslation(source)))return null;
        JsonObject dictionary=new JsonObject();dictionary.add("exact",exact);
        JsonElement copy=original.deepCopy();
        if(!new TranslationEngine(dictionary).rewriteVisible(copy,false))return null;
        class_2561 result=class_2561.class_2562.method_10872(copy);
        return result!=null&&key(result.getString()).equals(rewritten)?copy:null;
    }

    private static String key(String value){return TranslationEngine.normalize(value);}
    private static boolean guard(TranslationEngine engine,String source,String expected) {
        // A changed full-sentence or per-line user override disables the bundled rule.
        return key(engine.translate(source,false)).equals(key(expected));
    }
    private static JsonElement componentCopy(class_2561 source) {
        JsonElement copy=class_2561.class_2562.method_10868(source).deepCopy();
        if(copy.isJsonPrimitive()){JsonObject wrapped=new JsonObject();wrapped.add("text",copy);return wrapped;}
        return copy;
    }
    private static List<String> strings(JsonArray values)throws IOException {
        List<String> out=new ArrayList<>();
        for(JsonElement value:values) {
            String raw=value.getAsString();
            if(key(raw).isEmpty())throw new IOException("Empty contextual line");
            out.add(raw);
        }
        return List.copyOf(out);
    }
    private static TranslationEngine local(JsonObject exact)throws IOException {
        if(exact==null||exact.size()==0)throw new IOException("Empty contextual dictionary");
        Map<String,String> unique=new HashMap<>();
        for(var pair:exact.entrySet()) {
            String source=key(pair.getKey()),target=pair.getValue().getAsString();
            if(source.isEmpty()||key(target).isEmpty())throw new IOException("Empty contextual mapping");
            String before=unique.putIfAbsent(source,target);
            if(before!=null&&!before.equals(target))throw new IOException("Conflicting contextual mapping");
        }
        JsonObject dictionary=new JsonObject();dictionary.add("exact",exact);
        return new TranslationEngine(dictionary);
    }
    private static Rules load() {
        try(InputStream stream=ContextTranslations.class.getResourceAsStream("/monumenta_contexts.json")) {
            if(stream==null)return new Rules(Map.of(),Map.of());
            JsonObject root;
            try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                root=JsonParser.parseReader(reader).getAsJsonObject();
            }
            Map<String,List<TooltipRule>> tooltips=new HashMap<>();
            Set<List<String>> sequences=new HashSet<>();
            for(JsonElement value:root.getAsJsonArray("tooltips")) {
                JsonObject row=value.getAsJsonObject();
                List<String> sources=strings(row.getAsJsonArray("source_lines"));
                List<String> targets=strings(row.getAsJsonArray("target_lines"));
                List<String> guards=strings(row.getAsJsonArray("guard_line_translations"));
                String source=row.get("guard_exact_source").getAsString();
                String expected=row.get("guard_expected").getAsString();
                if(sources.size()<2||targets.size()!=sources.size()||guards.size()!=sources.size()
                    ||!key(String.join(" ",sources)).equals(key(source))
                    ||!key(String.join("",targets)).equals(key(expected))
                    ||!sequences.add(sources.stream().map(ContextTranslations::key).toList()))
                    throw new IOException("Invalid complete tooltip context");
                JsonObject exact=new JsonObject();
                for(int i=0;i<sources.size();i++)exact.addProperty(sources.get(i),targets.get(i));
                TooltipRule rule=new TooltipRule(sources,targets,source,expected,guards,local(exact));
                tooltips.computeIfAbsent(key(sources.get(0)),unused->new ArrayList<>()).add(rule);
            }
            Map<String,List<TooltipRule>> frozen=new HashMap<>();
            for(var entry:tooltips.entrySet())frozen.put(entry.getKey(),List.copyOf(entry.getValue()));
            Map<String,ServerRule> servers=new HashMap<>();
            for(JsonElement value:root.getAsJsonArray("servers")) {
                JsonObject row=value.getAsJsonObject();
                ServerKind kind=switch(row.has("kind")?row.get("kind").getAsString():"npc") {
                    case "npc" -> ServerKind.NPC;
                    case "server_notice" -> ServerKind.SERVER_NOTICE;
                    default -> throw new IOException("Unknown server context kind");
                };
                String source=row.get("guard_exact_source").getAsString();
                String expected=row.get("guard_expected").getAsString();
                String rewritten=row.get("rewritten_visible").getAsString();
                if(kind==ServerKind.NPC&&!source.matches("^\\[[^]\\r\\n]{1,100}\\] .+"))
                    throw new IOException("NPC context requires a complete NPC-prefixed source");
                if(kind==ServerKind.SERVER_NOTICE&&!monumentNotice(source))
                    throw new IOException("Unsupported complete server notice context");
                Map<String,String> guards=new LinkedHashMap<>();
                for(var entry:row.getAsJsonObject("guard_part_translations").entrySet()) {
                    if(key(entry.getKey()).isEmpty()||key(entry.getValue().getAsString()).isEmpty())
                        throw new IOException("Empty server context guard");
                    guards.put(entry.getKey(),entry.getValue().getAsString());
                }
                if(key(expected).isEmpty()||key(rewritten).isEmpty()||guards.isEmpty())
                    throw new IOException("Incomplete server context guard");
                ServerRule rule=new ServerRule(kind,source,expected,rewritten,Map.copyOf(guards),local(row.getAsJsonObject("exact")));
                if(servers.putIfAbsent(key(source),rule)!=null)throw new IOException("Duplicate server context");
            }
            return new Rules(Map.copyOf(frozen),Map.copyOf(servers));
        }catch(Exception ex) {
            // Optional context support cannot interfere with the established display path.
            System.err.println("[MonumentaZH] Narrow display contexts disabled: "+ex.getMessage());
            return new Rules(Map.of(),Map.of());
        }
    }

    private static boolean monumentNotice(String source) {
        // Additional complete captured notices only; never match fragments or suffixes.
        if(Set.of(
            "2 Totems remain.",
            "You collected 1 item from the grave",
            "Aww, look at the cute dioramas of the seasons surrounding the WHAT IS THAT?!",
            "A collection of pillars and arches sit in a sand pit...with a lava moat? Is this a toy for blazes? Surely kitten would find this too warm.",
            "- Post non-critical bugs in the #bug-report-bot-feed Discord channel with relevant screenshots / information - https://discord.gg/eep9qcu"
        ).contains(source))return true;
        // Only the five captured failure sentences qualify; no generic suffix match.
        for(String wool:List.of("White","Orange","Magenta","Light Blue","Yellow"))
            if(source.equals("You can not complete the monument because you have not placed the "+wool+" Wool correctly."))return true;
        return false;
    }
}
