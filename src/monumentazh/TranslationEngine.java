package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Offline display dictionary. All values in wildcard captures remain unchanged. */
public final class TranslationEngine {
    private static final Pattern TOKEN = Pattern.compile("%[dprtsu][0-9]*[Reu]*");
    private static final Pattern LEGACY = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern EMPHASIS = Pattern.compile("\\*([^*]+)\\*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern BULLET = Pattern.compile("^▶\\s*");
    private final Map<String, String> exact = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();
    private final List<Fragment> fragments = new ArrayList<>();
    private record Fragment(String key,String value,Pattern pattern) {}

    public TranslationEngine(JsonObject dictionary) {
        add(dictionary);
    }

    public void add(JsonObject dictionary) {
        if (dictionary.has("exact")) {
            dictionary.getAsJsonObject("exact").entrySet().forEach(e -> exact.put(normalize(e.getKey()), e.getValue().getAsString()));
        }
        if (dictionary.has("templates")) {
            dictionary.getAsJsonObject("templates").entrySet().forEach(e -> rules.add(new Rule(e.getKey(), e.getValue().getAsString())));
            rules.sort(Comparator.comparingInt((Rule r) -> r.specificity).reversed());
        }
        if (dictionary.has("fragments")) {
            dictionary.getAsJsonObject("fragments").entrySet().forEach(e -> {
                String key=e.getKey();
                if(key.isEmpty())return;
                String before=isLatin(key.charAt(0))?"(?<![A-Za-z])":"";
                String after=isLatin(key.charAt(key.length()-1))?"(?![A-Za-z])":"";
                fragments.add(new Fragment(key,e.getValue().getAsString(),Pattern.compile(before+Pattern.quote(key)+after)));
            });
            fragments.sort(Comparator.comparingInt((Fragment e) -> e.key().length()).reversed());
        }
    }

    public static String normalize(String text) {
        String value=LEGACY.matcher(text).replaceAll("");
        value=EMPHASIS.matcher(value).replaceAll("$1")
            .replace("\uFE0F", "").replace("(⭐)", "(s)").replace("(🗡)", "(m)").replace("(🏹)", "(p)")
            .replace("→", "->");
        return BULLET.matcher(WHITESPACE.matcher(value).replaceAll(" ").trim()).replaceFirst("");
    }

    public String translate(String text, boolean partial) {
        return translatePreserving(text, partial, Set.of());
    }

    public String translatePreserving(String text, boolean partial, Set<String> protectedValues) {
        String normalized = normalize(text);
        if (protectedValues.contains(normalized)) return text;
        String translated = exact.get(normalized);
        if (translated != null) return normalized.equals(normalize(translated))?text:decorate(text, translated);
        String quantity=QuantityText.translate(normalized,exact,protectedValues);
        if(quantity!=null)return decorate(text,quantity);
        for (Rule rule : rules) {
            if(!normalized.contains(rule.literal))continue;
            Matcher matcher = rule.pattern.matcher(normalized);
            if (matcher.matches()) {
                String result = rule.output;
                for (int i = matcher.groupCount() - 1; i >= 0; i--) {
                    String raw = matcher.group(i + 1), key = normalize(raw);
                    result = result.replace("{" + i + "}", rule.userGroups.get(i) || protectedValues.contains(key) ? raw : exact.getOrDefault(key, raw));
                }
                return decorate(text, result);
            }
        }
        if (partial) {
            String result = text;
            for (var fragment : fragments) {
                if(result.indexOf(fragment.key())>=0)
                    result=fragment.pattern().matcher(result).replaceAll(Matcher.quoteReplacement(fragment.value()));
            }
            return result;
        }
        return text;
    }

    private static String decorate(String original, String result) {
        String visible=LEGACY.matcher(original).replaceAll("").strip();
        if(visible.startsWith("* ")&&visible.endsWith(" *")&&visible.length()>4
            &&!(result.strip().startsWith("*")&&result.strip().endsWith("*")))result="* "+result+" *";
        return original.stripLeading().startsWith("▶") ? " ▶ " + result : result;
    }
    private static boolean isLatin(char c){return c>='A'&&c<='Z'||c>='a'&&c<='z';}

    /** Rewrite visible text only; commands, hover metadata and translation keys stay intact. */
    public boolean rewriteVisible(JsonElement value) {
        return rewriteVisible(value, true);
    }

    public boolean rewriteVisible(JsonElement value, boolean partial) {
        return rewriteVisible(value, partial, false);
    }

    /** Book prose uses complete phrases and preserves linked player identities and author attribution. */
    public boolean rewriteBookVisible(JsonElement value) {
        return rewriteVisible(value, false, true);
    }

    private boolean rewriteVisible(JsonElement value, boolean partial, boolean book) {
        if (value == null || value.isJsonNull()) return false;
        boolean changed = false;
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String original = child.getAsString();
                    String result = translate(original, partial);
                    if (!original.equals(result)) {
                        JsonObject replacement = new JsonObject();
                        replacement.addProperty("text",original);
                        replaceTranslatedText(replacement, result);
                        array.set(i, replacement);
                        changed = true;
                    }
                } else {
                    changed |= rewriteVisible(child, partial, book);
                }
            }
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if(bookIdentity(object))return false;
            // Translate only source children: generated bilingual font runs already contain final text.
            JsonElement originalExtra = object.get("extra");
            if (originalExtra != null) changed |= rewriteVisible(originalExtra, partial, book);
            if (object.has("text") && object.get("text").isJsonPrimitive()) {
                String original = object.get("text").getAsString();
                String result = translate(original, partial);
                if (!original.equals(result)) { replaceTranslatedText(object, result); changed = true; }
            }
            if (object.has("translate") && object.has("with")) {
                String key=object.get("translate").getAsString();
                if(key.equals("container.shulkerBox.itemCount"))changed|=rewriteVisible(object.get("with"),partial,book);
                else if(key.equals("chat.square_brackets"))changed|=rewriteVisible(object.get("with"),partial,book);
            }
        }
        return changed;
    }

    private static boolean bookIdentity(JsonObject object) {
        if(object.has("translate")&&object.get("translate").getAsString().equals("book.byAuthor"))return true;
        if(!object.has("hoverEvent")||!object.get("hoverEvent").isJsonObject())return false;
        JsonObject hover=object.getAsJsonObject("hoverEvent");
        if(!hover.has("action")||!hover.get("action").getAsString().equals("show_entity")||!hover.has("contents")||!hover.get("contents").isJsonObject())return false;
        JsonObject contents=hover.getAsJsonObject("contents");
        return contents.has("type")&&contents.get("type").getAsString().equals("minecraft:player");
    }

    /** Chinese runs use the vanilla font, while icons and existing children keep their original font. */
    public static void replaceTranslatedText(JsonObject object, String translated) {
        JsonObject legacy=uniformLegacyPrefix(object);
        if (translated.codePoints().noneMatch(TranslationEngine::usesChineseFont)) {
            if(legacy==null||legacy.isEmpty())object.addProperty("text", translated);
            else {
                JsonObject run=legacy.deepCopy();run.addProperty("text",translated);JsonArray extra=new JsonArray();extra.add(run);
                if(object.has("extra"))for(JsonElement child:object.getAsJsonArray("extra"))extra.add(child);
                object.addProperty("text","");object.add("extra",extra);
            }
            return;
        }
        JsonArray extra = new JsonArray();
        int start = 0;
        boolean chinese = usesChineseFont(translated.codePointAt(0));
        for (int offset = 0; offset < translated.length();) {
            int codePoint = translated.codePointAt(offset);
            boolean nextChinese = usesChineseFont(codePoint);
            if (nextChinese != chinese) {
                extra.add(fontRun(translated.substring(start, offset), chinese,legacy));
                start = offset;
                chinese = nextChinese;
            }
            offset += Character.charCount(codePoint);
        }
        extra.add(fontRun(translated.substring(start), chinese,legacy));
        if (object.has("extra")) {
            for (JsonElement child : object.getAsJsonArray("extra")) extra.add(child);
        }
        object.addProperty("text", "");
        object.add("extra", extra);
    }

    private static JsonObject fontRun(String text, boolean chinese,JsonObject legacy) {
        JsonObject run = legacy==null?new JsonObject():legacy.deepCopy();
        run.addProperty("text", text);
        if (chinese) run.addProperty("font", "minecraft:default");
        return run;
    }

    /** A leading legacy color styles this literal run, never its existing siblings or metadata. */
    private static JsonObject uniformLegacyPrefix(JsonObject object) {
        if(!object.has("text")||!object.get("text").isJsonPrimitive())return null;
        String text=object.get("text").getAsString();int end=0;
        while(end+1<text.length()&&text.charAt(end)=='§'&&"0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(end+1)))>=0)end+=2;
        // Internal color changes require separate source spans; do not guess one color for them.
        if(end==0||end==text.length()||LEGACY.matcher(text.substring(end)).find())return null;
        JsonObject style=new JsonObject();
        String[] colors={"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
        for(int at=1;at<end;at+=2) {
            char code=Character.toLowerCase(text.charAt(at));int color="0123456789abcdef".indexOf(code);
            if(color>=0){style.addProperty("color",colors[color]);for(String flag:List.of("bold","italic","underlined","strikethrough","obfuscated"))style.addProperty(flag,false);}
            else if(code=='r')style=new JsonObject(); // Native reset returns to this literal's initial component style.
            else style.addProperty(switch(code){case 'k'->"obfuscated";case 'l'->"bold";case 'm'->"strikethrough";case 'n'->"underlined";case 'o'->"italic";default->throw new IllegalStateException();},true);
        }
        return style;
    }

    private static boolean usesChineseFont(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
            || (codePoint >= 0x3000 && codePoint <= 0x303F)
            || (codePoint >= 0xFF00 && codePoint <= 0xFFEF);
    }

    private static final class Rule {
        final Pattern pattern;
        final BitSet userGroups = new BitSet();
        final String output;
        final int specificity;
        final String literal;
        Rule(String input, String output) {
            input = normalize(input);
            Matcher matcher = TOKEN.matcher(input);
            StringBuilder regex = new StringBuilder("^");
            int position = 0, fixed = 0, group = 0;String longest="";
            while (matcher.find()) {
                if (matcher.group().equals("%u")) userGroups.set(group);
                group++;
                String literal = input.substring(position, matcher.start());
                if(literal.length()>longest.length())longest=literal;
                regex.append(Pattern.quote(literal)).append(matcher.group().equals("%u")?"([A-Za-z0-9_]{1,16})":"(.+?)");
                fixed += literal.length();
                position = matcher.end();
            }
            String tail = input.substring(position);
            this.literal=tail.length()>longest.length()?tail:longest;
            regex.append(Pattern.quote(tail)).append("$");
            pattern = Pattern.compile(regex.toString());
            this.output = output;
            specificity = fixed + tail.length();
        }
    }
}
