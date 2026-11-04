package monumentazh;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Evidence-based identity handling. Originals used for rendering never pass through this exporter. */
public final class CapturePrivacy {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern ACCOUNT=Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern NIGHTMARE_COMPLETION=Pattern.compile("^([A-Za-z0-9_]{1,16}) has dispelled the Nightmare in .+! \\(Wave: \\d+\\)$");
    private static final Pattern WORLD_RECORD=Pattern.compile("^([A-Za-z0-9_]{1,16}) has set a new world record for [^\\r\\n]+\\r?\\nNew time: [0-9]+(?::[0-9]+){1,3}$");
    private static final Pattern WORD=Pattern.compile("[A-Za-z0-9_]+(?:[-][A-Za-z0-9_]+)*");
    private static final List<Pattern> ROLES=List.of(
        Pattern.compile("^(?:Social > )?([A-Za-z0-9_]{1,16}) (?:has (?:claimed|conquered|won|bought|freed|succumbed|defeated|completed)\\b|joined[.!]|quit[.!]|left[.!]|died\\b|was (?:killed|slain|blown|burned|doomed)\\b|fell (?:from|out|off|into|to|while)\\b|burned to death|drowned\\b|defeated\\b|completed\\b)"),
        Pattern.compile("^CONGRATULATIONS!\\s+([A-Za-z0-9_]{1,16})\\b"),
        Pattern.compile("^Welcome\\s+([A-Za-z0-9_]{1,16})\\s+to the server!"),
        Pattern.compile("\\bThanks ([A-Za-z0-9_]{1,16}) for supporting the server!"),
        Pattern.compile("\\b(?:Soulbound to|Reinforced by|Tracked by|Hoped by) ([A-Za-z0-9_]{1,16})"),
        Pattern.compile("^(?:(?:Mailbox|Mail) )?(?:From|To|For) ([A-Za-z0-9_]{1,16})(?:$| To )"),
        Pattern.compile("^Mailbox From [A-Za-z0-9_]{1,16} To ([A-Za-z0-9_]{1,16})$"),
        Pattern.compile("(?:friend request to|now friends with|People with no instance:) ([A-Za-z0-9_]{1,16})(?=[\\s.!]|$)"),
        Pattern.compile("(?:The player name '|Hey there |good luck )([A-Za-z0-9_]{1,16})(?=[\\s'.,!?]|$)"),
        Pattern.compile("You're ([A-Za-z0-9_]{1,16}), right\\?"),
        Pattern.compile("Friend request from (?:\\[[^]]+\\] )?([A-Za-z0-9_]{1,16})"),
        Pattern.compile("^View ([A-Za-z0-9_]{1,16})'s (?:gear|class|inventory)"),
        Pattern.compile("^([A-Za-z0-9_]{1,16})'s (?:Inventory|Details)$"),
        Pattern.compile("^([A-Za-z0-9_]{1,16}) \\(#\\d+\\)$"),
        Pattern.compile("^\\[([A-Za-z0-9_]{1,16}) got mail\\]$")
    );
    private static final class IdentityContext {
        final LinkedHashSet<String> players=new LinkedHashSet<>();
        final Set<String> npcNames=new HashSet<>(),guildLabels=new HashSet<>(),guildNames=new HashSet<>();
        final Set<String> legacyLiterals=new HashSet<>(),legacyAccounts=new HashSet<>();
        IdentityContext() {}
        IdentityContext(IdentityContext source) {
            players.addAll(source.players);npcNames.addAll(source.npcNames);guildLabels.addAll(source.guildLabels);
            guildNames.addAll(source.guildNames);legacyLiterals.addAll(source.legacyLiterals);legacyAccounts.addAll(source.legacyAccounts);
        }
    }
    private static final IdentityContext live=new IdentityContext();
    private static final ThreadLocal<IdentityContext> scoped=new ThreadLocal<>();
    private static IdentityContext identities() { IdentityContext value=scoped.get();return value==null?live:value; }
    /** One autosave uses the identities known at its start, even across ticks/disconnection. */
    static final class SnapshotContext {
        private final IdentityContext value=new IdentityContext(identities());
    }
    static SnapshotContext snapshotContext() { return new SnapshotContext(); }
    static JsonObject cleanCapture(JsonObject root,SnapshotContext context) {
        IdentityContext previous=scoped.get();scoped.set(context.value);
        try { return cleanCapture(root); }
        finally { if(previous==null)scoped.remove();else scoped.set(previous); }
    }
    private static final Set<String> RESERVED=Set.of("you","your","i","he","she","they","we","the","a","an","to","from","guild","player","server","none","unknown","owner","it","their","has","have","with","this","that","then","not","quite","very","right","wrong","sure","welcome","friend","friends","adventurer","traveler","champion","traveller","ready","doing","all","still","only","so","going","here","now","in","out","back","safe","who","which","what");
    private CapturePrivacy() {}
    public static void observePlayer(String value) {
        String name=TranslationEngine.normalize(value);
        if(!ACCOUNT.matcher(name).matches())return;
        if(identities().players.size()>=4096)identities().players.remove(identities().players.iterator().next());
        identities().players.add(name.toLowerCase(Locale.ROOT));
    }
    public static void clearSession(){identities().players.clear();}
    public static boolean playerName(String raw){return identities().players.contains(TranslationEngine.normalize(raw).toLowerCase(Locale.ROOT));}
    public static void catalog(JsonObject catalog) {
        for(String group:List.of("npc_names","place_names"))if(catalog.has(group))for(var e:catalog.getAsJsonObject(group).entrySet())identities().npcNames.add(e.getKey());
        if(catalog.has("guild_labels"))for(var v:catalog.getAsJsonArray("guild_labels")) {
            String label=v.getAsString();identities().guildLabels.add(label);int end=label.indexOf("] ");if(end>=0)identities().guildNames.add(label.substring(end+2).toLowerCase(Locale.ROOT));identities().guildNames.add(label.substring(1,label.indexOf(']')).toLowerCase(Locale.ROOT));
        }
    }
    private static boolean excluded(String name){return RESERVED.contains(name.toLowerCase(Locale.ROOT))&&!playerName(name)||identities().npcNames.contains(name)||identities().guildNames.contains(name.toLowerCase(Locale.ROOT))||Set.of("valley","plots","guildplots","labs-4").contains(name);}
    public static Set<String> roleNames(String raw) {
        Set<String> out=new LinkedHashSet<>();
        // A marketplace seller field is an account, even when it matches an NPC or a common word.
        if(raw.startsWith("Sold by: ")) {
            String seller=raw.substring("Sold by: ".length());
            if(ACCOUNT.matcher(seller).matches())out.add(seller);
            return out;
        }
        // A completed Nightmare broadcast explicitly identifies the leading account, even if it shares an NPC name.
        if(raw.contains(" has dispelled the Nightmare in ")) {
            Matcher completion=NIGHTMARE_COMPLETION.matcher(raw);
            if(completion.matches()){out.add(completion.group(1));return out;}
        }
        // Timed world-record notices identify an account, not a class or an NPC with the same name.
        if(raw.contains(" has set a new world record for ")) {
            Matcher record=WORLD_RECORD.matcher(raw);
            if(record.matches()){out.add(record.group(1));return out;}
        }
        if(!(raw.contains(" has ")||raw.contains("Social >")||raw.contains("Soulbound to")||raw.contains("Reinforced by")||raw.contains("Tracked by")||raw.contains("Hoped by")||raw.contains("From ")||raw.contains("To ")||raw.contains("For ")||raw.contains("friend")||raw.contains("got mail")||raw.contains("People with")||raw.contains("CONGRATULATIONS!")||raw.startsWith("Welcome ")||raw.contains("Thanks ")||raw.contains(" joined.")||raw.contains(" quit.")||raw.contains(" died")||raw.contains(" was ")||raw.contains(" fell")||raw.contains(" burned ")||raw.contains(" drowned")||raw.contains(" defeated ")||raw.contains(" completed ")||raw.contains("player name")||raw.contains("You're ")||raw.contains("Hey there ")||raw.contains("good luck ")||raw.contains("Friend request from")||raw.startsWith("View ")||raw.contains("'s Inventory")||raw.contains("'s Details")||raw.contains(" (#")))return out;
        for(Pattern role:ROLES){Matcher m=role.matcher(raw);while(m.find())if(!excluded(m.group(1)))out.add(m.group(1));}
        return out;
    }
    private static String visible(JsonElement value) {
        if(value==null||value.isJsonNull())return "";
        if(value.isJsonPrimitive())return value.getAsString();
        StringBuilder out=new StringBuilder();
        if(value.isJsonArray())for(var child:value.getAsJsonArray())out.append(visible(child));
        else {JsonObject o=value.getAsJsonObject();if(o.has("text"))out.append(visible(o.get("text")));if(o.has("extra"))out.append(visible(o.get("extra")));}
        return out.toString();
    }
    public static boolean playerComponent(JsonElement value) {
        if(value==null||value.isJsonNull()||value.isJsonPrimitive())return false;
        if(value.isJsonArray()){for(var v:value.getAsJsonArray())if(playerComponent(v))return true;return false;}
        JsonObject o=value.getAsJsonObject();
        if(o.has("hoverEvent")&&o.get("hoverEvent").isJsonObject()) {
            JsonObject h=o.getAsJsonObject("hoverEvent");JsonElement c=h.get("contents");
            if(c!=null&&c.isJsonObject()&&c.getAsJsonObject().has("type")&&c.getAsJsonObject().get("type").getAsString().equals("minecraft:player"))return true;
        }
        return o.has("extra")&&playerComponent(o.get("extra"));
    }
    public static boolean nonPlayerComponent(JsonElement value) {
        if(value==null||!value.isJsonObject())return false;
        JsonObject o=value.getAsJsonObject();
        if(!o.has("hoverEvent")||!o.get("hoverEvent").isJsonObject())return false;
        JsonElement c=o.getAsJsonObject("hoverEvent").get("contents");
        return c!=null&&c.isJsonObject()&&c.getAsJsonObject().has("type")&&!c.getAsJsonObject().get("type").getAsString().equals("minecraft:player");
    }
    private static void evidence(JsonElement value,Set<String> out) {
        if(value==null||value.isJsonNull())return;
        if(value.isJsonPrimitive()){if(value.getAsJsonPrimitive().isString()){String raw=value.getAsString();out.addAll(roleNames(raw));if(raw.startsWith("by ")&&playerName(raw.substring(3))&&!excluded(raw.substring(3)))out.add(raw.substring(3));}return;}
        if(value.isJsonArray()){for(var v:value.getAsJsonArray())evidence(v,out);return;}
        JsonObject o=value.getAsJsonObject();
        if(o.has("clickEvent")&&o.get("clickEvent").isJsonObject()) {
            JsonObject click=o.getAsJsonObject("clickEvent");
            if(click.has("value")){Matcher m=Pattern.compile("^/(?:tell|msg|w|whisper|chat gui message)\\s+([A-Za-z0-9_]{1,16})(?:\\s|$)").matcher(click.get("value").getAsString());if(m.find())out.add(m.group(1));}
        }
        if(o.has("hoverEvent")&&o.get("hoverEvent").isJsonObject()) {
            JsonObject h=o.getAsJsonObject("hoverEvent");JsonElement c=h.get("contents");
            if(c!=null&&c.isJsonObject()&&c.getAsJsonObject().has("type")&&c.getAsJsonObject().get("type").getAsString().equals("minecraft:player")) {
                String name=visible(c.getAsJsonObject().get("name"));if(!name.isBlank())out.add(name);
                name=visible(o);if(ACCOUNT.matcher(name).matches())out.add(name);
            }
        }
        if(o.has("translate")&&o.has("with")&&o.get("with").isJsonArray()) {
            String key=o.get("translate").getAsString();JsonArray args=o.getAsJsonArray("with");
            int count=key.startsWith("death.attack.player")?2:key.startsWith("death.")||key.startsWith("chat.type.advancement.")?1:0;
            if(key.equals("book.byAuthor")&&args.size()>0){String name=visible(args.get(0));if(playerName(name)&&!excluded(name))out.add(name);}
            for(int i=0;i<Math.min(count,args.size());i++){String name=visible(args.get(i));if(!name.isBlank())out.add(name);}
        }
        for(var e:o.entrySet())evidence(e.getValue(),out);
    }
    private static Set<String> namesFor(String raw,JsonElement component) {
        Set<String> out=new LinkedHashSet<>(roleNames(raw));evidence(component,out);
        Matcher token=WORD.matcher(raw);
        while(token.find()) {
            String name=token.group();
            if(identities().legacyAccounts.contains(name.toLowerCase(Locale.ROOT))&&!excluded(name))out.add(name);
        }
        // Role evidence is local to this record; a colliding account must not disable the actual NPC's label.
        for(String name:out)if(!identities().npcNames.contains(name))observePlayer(name);
        return out;
    }
    private static String replace(String source,Set<String> names,String token) {
        if(names.isEmpty())return source;
        // Tokenize once: costs do not grow with the number of online identities().players.
        Set<String> folded=new HashSet<>();for(String n:names)folded.add(n.toLowerCase(Locale.ROOT));
        Matcher m=WORD.matcher(source);StringBuffer out=new StringBuffer();
        while(m.find())if(folded.contains(m.group().toLowerCase(Locale.ROOT)))m.appendReplacement(out,Matcher.quoteReplacement(token));
        m.appendTail(out);String result=out.toString();
        for(String name:names)if(name.indexOf(' ')>=0)result=Pattern.compile("(?<![A-Za-z0-9_])"+Pattern.quote(name)+"(?![A-Za-z0-9_])",Pattern.CASE_INSENSITIVE).matcher(result).replaceAll(Matcher.quoteReplacement(token));
        return result;
    }
    private static JsonElement scrub(JsonElement value,Set<String> names) {
        if(value==null||value.isJsonNull())return JsonNull.INSTANCE;
        if(value.isJsonPrimitive())return value.getAsJsonPrimitive().isString()?new JsonPrimitive(replace(value.getAsString(),names,"{player}")):value.deepCopy();
        if(value.isJsonArray()){JsonArray a=new JsonArray();for(var v:value.getAsJsonArray())a.add(scrub(v,names));return a;}
        JsonObject source=value.getAsJsonObject(),out=new JsonObject();
        if(source.has("hoverEvent")&&source.get("hoverEvent").isJsonObject()) {
            JsonElement c=source.getAsJsonObject("hoverEvent").get("contents");
            if(c!=null&&c.isJsonObject()&&c.getAsJsonObject().has("type")&&c.getAsJsonObject().get("type").getAsString().equals("minecraft:player")) {
                out.addProperty("text","{player}");for(String style:List.of("color","font","bold","italic","underlined","strikethrough","obfuscated"))if(source.has(style))out.add(style,source.get(style).deepCopy());return out;
            }
        }
        boolean player=source.has("type")&&source.get("type").isJsonPrimitive()&&source.get("type").getAsString().equals("minecraft:player");
        for(var e:source.entrySet()) {
            // Export copies do not need identity UUIDs or insertion payloads. Runtime components remain intact.
            if(e.getKey().equals("insertion")||(player&&(e.getKey().equals("id")||e.getKey().equals("uuid"))))continue;
            out.add(e.getKey(),scrub(e.getValue(),names));
        }
        return out;
    }
    public static JsonObject text(String kind,String raw,JsonElement component,String preview) {
        if(Set.of("server_message","npc_dialogue","server_notice").contains(kind)&&ChatMessageFilter.isLocalModMessage(raw))return null;
        if((kind.equals("entity_label")||kind.equals("text_display"))&&(playerName(raw)&&!nonPlayerComponent(component)||playerComponent(component)))return null;
        // Generic item/enchantment words remain usable even when a player happens to share the word.
        Set<String> names=namesFor(raw,component);
        Set<String> explicit=new LinkedHashSet<>(roleNames(raw));evidence(component,explicit);
        names.removeIf(n->!explicit.contains(n)&&identities().legacyAccounts.contains(n.toLowerCase(Locale.ROOT))&&raw.equals(preview)&&!raw.matches(".*[\\u4e00-\\u9fff].*")&&!n.matches(".*[0-9_].*")&&!n.matches(".*[a-z][A-Z].*"));
        if(kind.equals("tooltip")||kind.equals("item_name")) {
            names=explicit;
        }
        JsonObject out=new JsonObject();out.addProperty("kind",kind);out.addProperty("text",replace(raw,names,"{player}"));
        out.add("component",scrub(component,names));out.addProperty("translated_preview",replace(preview,names,"{player}"));
        // Match the exported identity after removing private insertion/UUID metadata.
        if(CaptureExclusions.excluded(kind,out.get("text").getAsString(),out.get("component")))return null;
        return out;
    }
    public static JsonObject item(JsonObject item) {
        String name=item.get("name").getAsString();if(identities().guildLabels.contains(name))return item.deepCopy();
        Set<String> names=new LinkedHashSet<>();evidence(item,names);for(String n:names)observePlayer(n);
        return scrub(item,names).getAsJsonObject();
    }
    public static JsonObject cleanCapture(JsonObject root) {
        JsonObject out=root.deepCopy();JsonArray items=new JsonArray(),texts=new JsonArray();
        if(root.has("items"))for(var v:root.getAsJsonArray("items"))items.add(item(v.getAsJsonObject()));
        if(root.has("texts"))for(var v:root.getAsJsonArray("texts")) {
            JsonObject t=v.getAsJsonObject();String kind=t.get("kind").getAsString(),raw=t.get("text").getAsString();
            if(EncounteredTextStore.isChatKind(kind)||Set.of("server_message","npc_dialogue","server_notice").contains(kind)&&ChatMessageFilter.classify(t.get("component"),raw)==ChatMessageFilter.Kind.PLAYER)continue;
            JsonObject safe=text(kind,raw,t.get("component"),t.has("translated_preview")?t.get("translated_preview").getAsString():raw);
            if(safe!=null){if(t.has("first_seen"))safe.add("first_seen",t.get("first_seen"));texts.add(safe);}
        }
        out.add("items",items);out.add("texts",texts);out.addProperty("identity_redacted",true);return out;
    }
    public static JsonObject cleanNames(JsonObject names) {catalog(names);JsonObject out=names.deepCopy();out.remove("player_names");out.addProperty("identity_policy","runtime_only");return out;}
    public static void legacy(JsonObject names) {
        catalog(names);if(names.has("player_names"))for(var n:names.getAsJsonArray("player_names")){identities().legacyLiterals.add(n.getAsString());if(!excluded(n.getAsString())){observePlayer(n.getAsString());identities().legacyAccounts.add(n.getAsString().toLowerCase(Locale.ROOT));}}
    }
    /** Store generic %u templates, never a list of captured account names. */
    public static JsonObject cleanDictionary(JsonObject flat) {
        JsonObject out=new JsonObject();
        for(var e:flat.entrySet()) {
            String raw=e.getKey(),value=e.getValue().getAsString();
            if(raw.equals(value)&&identities().legacyLiterals.contains(raw))continue;
            if(identities().guildLabels.contains(raw)){out.addProperty(raw,value);continue;}
            Set<String> names=new LinkedHashSet<>(roleNames(raw));Matcher account=WORD.matcher(raw);while(account.find())if(identities().legacyAccounts.contains(account.group().toLowerCase(Locale.ROOT))&&!excluded(account.group()))names.add(account.group());
            if(names.isEmpty()){out.addProperty(raw,value);continue;}
            List<String> ordered=new ArrayList<>();Matcher m=WORD.matcher(raw);boolean repeated=false;
            while(m.find())for(String name:names)if(name.equalsIgnoreCase(m.group())){if(ordered.contains(name))repeated=true;else ordered.add(name);}
            if(repeated){if(roleNames(raw).isEmpty())out.addProperty(raw,value);continue;}
            String key=raw,result=value;boolean usable=true;
            for(int i=0;i<ordered.size();i++) {
                String name=ordered.get(i);String replaced=replace(result,Set.of(name),"{"+i+"}");
                if(replaced.equals(result)){usable=false;break;}
                key=replace(key,Set.of(name),"%u");result=replaced;
            }
            if(usable)out.addProperty(key,result);
            else if(roleNames(raw).isEmpty())out.addProperty(raw,value);
        }
        return out;
    }
    public static JsonObject runtimeDictionary(JsonObject flat) {
        JsonObject exact=new JsonObject(),templates=new JsonObject();
        for(var e:flat.entrySet())(e.getKey().contains("%u")&&e.getValue().getAsString().contains("{0}")?templates:exact).add(e.getKey(),e.getValue());
        JsonObject out=new JsonObject();out.add("exact",exact);out.add("templates",templates);return out;
    }
    public static void migrateConfig(Path folder)throws IOException {
        try { migrate(folder); } finally {identities().legacyLiterals.clear();identities().legacyAccounts.clear();clearSession();}
    }
    private static void migrate(Path folder)throws IOException {
        JsonObject names=Files.isRegularFile(folder.resolve("names.json"))?JsonParser.parseString(Files.readString(folder.resolve("names.json"))).getAsJsonObject():new JsonObject();
        legacy(names);LinkedHashMap<Path,String> changes=new LinkedHashMap<>();
        if(names.has("player_names"))changes.put(folder.resolve("names.json"),GSON.toJson(cleanNames(names)));
        List<Path> captures=new ArrayList<>();for(String n:List.of("encountered_text.json","captured_text.json"))if(Files.isRegularFile(folder.resolve(n)))captures.add(folder.resolve(n));
        if(Files.isDirectory(folder.resolve("capture_archive")))try(var files=Files.newDirectoryStream(folder.resolve("capture_archive"),"batch-*.json")){for(Path p:files)captures.add(p);}
        for(Path p:captures){JsonObject raw=JsonParser.parseString(Files.readString(p)).getAsJsonObject();if(!raw.has("identity_redacted"))changes.put(p,GSON.toJson(cleanCapture(raw)));}
        Path dictionary=folder.resolve("overrides.json");
        if(Files.isRegularFile(dictionary)){JsonObject raw=JsonParser.parseString(Files.readString(dictionary)).getAsJsonObject(),safe=cleanDictionary(raw);if(!safe.equals(raw))changes.put(dictionary,GSON.toJson(safe));}
        if(!changes.isEmpty()) {
            Path backup=folder.resolve("private_backups/identity-v1-"+UUID.randomUUID());Files.createDirectories(backup);List<Path> committed=new ArrayList<>();
            try {
                for(Path p:changes.keySet()){Path b=backup.resolve(folder.relativize(p));Files.createDirectories(b.getParent());Files.copy(p,b);}
                for(var e:changes.entrySet()){EncounteredTextStore.atomicWrite(e.getKey(),e.getValue());committed.add(e.getKey());}
            }catch(IOException error){for(Path p:committed)Files.copy(backup.resolve(folder.relativize(p)),p,StandardCopyOption.REPLACE_EXISTING);throw error;}
        }
        identities().legacyLiterals.clear();clearSession();
    }
}
