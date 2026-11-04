package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Distinguishes server-forwarded player chat from NPC/quest/announcement text. */
public final class ChatMessageFilter {
    public enum Kind { PLAYER, NPC, SYSTEM }
    private static final Pattern CHANNEL = Pattern.compile("^\\s*<[^>\\r\\n]{1,64}>\\s*");
    private static final Pattern SENDER = Pattern.compile("^\\s*(?:\\[[^]\\r\\n]{1,64}\\]\\s*){0,4}[A-Za-z0-9_]{1,16}\\s*(?:»|>>|:)\\s*");
    private static final Pattern PRIVATE = Pattern.compile("^\\s*(?:(?:From|To)\\s+[A-Za-z0-9_]{1,16}\\s*[:»>]|[A-Za-z0-9_]{1,16}\\s+whispers\\b|\\[[^]\\r\\n]*(?:->|→)[^]\\r\\n]*\\])", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC = Pattern.compile("^\\s*\\[[^]\\r\\n]{1,100}\\]\\s+");
    private static final Pattern CHAT_COMMAND = Pattern.compile("^/(?:chat\\s+gui\\s+message|tell|msg|w|whisper|reply|r)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> PLAYER_CHAT_KEYS = Set.of("chat.type.text", "chat.type.text.narrate",
        "chat.type.announcement", "chat.type.emote", "chat.type.team.text", "chat.type.team.sent");
    private static final List<Pattern> ANNOUNCEMENT_NAMES = List.of(
        Pattern.compile("^CONGRATULATIONS!\\s+([A-Za-z0-9_]{1,16})\\b"),
        Pattern.compile("^Welcome\\s+([A-Za-z0-9_]{1,16})\\s+to the server!"),
        Pattern.compile("^([A-Za-z0-9_]{1,16})\\s+(?:has\\s+(?:bought|freed|succumbed|defeated|completed|conquered)\\b|defeated\\s|completed\\s|joined\\s|left\\s)"),
        Pattern.compile("\\bThanks\\s+([A-Za-z0-9_]{1,16})\\s+for supporting the server!")
    );

    private ChatMessageFilter() {}
    /** VF messages originate from the local finder mod, not an NPC or server translation. */
    public static boolean isLocalModMessage(String plain) {
        if(plain==null||plain.indexOf('[')<0)return false;
        String value=plain.replaceAll("§[0-9A-FK-ORa-fk-or]", "").stripLeading();
        return value.startsWith("[VF]")&&(value.length()==4||Character.isWhitespace(value.charAt(4)));
    }
    public static Kind classify(JsonElement component, String plain) {
        String value = plain == null ? "" : plain.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        if (CHANNEL.matcher(value).find() || PRIVATE.matcher(value).find() || hasPlayerMarker(component))
            return Kind.PLAYER;
        if (SENDER.matcher(value).find() && !questMetadata(component, value)) return Kind.PLAYER;
        return NPC.matcher(value).find() ? Kind.NPC : Kind.SYSTEM;
    }

    // Only the server's aqua quest-card fields bypass the ambiguous "name: message" heuristic.
    // Explicit player metadata, channels and whispers always take precedence in classify().
    private static boolean questMetadata(JsonElement component, String plain) {
        String value = plain.replaceAll("§[0-9A-FK-ORa-fk-or]", "").replace('\u00a0', ' ');
        if (!value.matches("(?:Length *: *(?:Short|Medium|Long|Very Short|Very Long)|Difficulty *: *(?:Easy|Medium|Hard|Very Easy|Very Hard)|Description *: *\\S[^\\r\\n]*)")) return false;
        return aquaVisible(component, false);
    }

    private static boolean aquaVisible(JsonElement value, boolean inherited) {
        if (value == null || value.isJsonNull()) return true;
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (!aquaVisible(child, inherited)) return false;
            return true;
        }
        if (value.isJsonPrimitive()) return aquaText(value.getAsString(), inherited, inherited);
        if (!value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        if (object.has("translate") || object.has("keybind") || object.has("selector") || object.has("score")) return false;
        boolean color = inherited;
        if (object.has("color")) {
            String name = object.get("color").getAsString();
            color = name.equalsIgnoreCase("aqua") || name.equalsIgnoreCase("#55ffff");
        }
        if (object.has("text") && !aquaText(object.get("text").getAsString(), color, color)) return false;
        return !object.has("extra") || aquaVisible(object.get("extra"), color);
    }

    private static boolean aquaText(String text, boolean aqua, boolean reset) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if ("0123456789abcdef".indexOf(code) >= 0) aqua = code == 'b';
                else if (code == 'r') aqua = reset;
            } else if (!Character.isWhitespace(c) && c != '\u00a0' && !aqua) return false;
        }
        return true;
    }

    private static String visibleText(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonElement child : value.getAsJsonArray()) text.append(visibleText(child));
            return text.toString();
        }
        JsonObject object = value.getAsJsonObject();
        return (object.has("text") ? object.get("text").getAsString() : "") + (object.has("extra") ? visibleText(object.get("extra")) : "");
    }

    private static boolean rewriteQuestMetadata(JsonElement value, TranslationEngine engine, Set<String> protectedNames) {
        String plain = visibleText(value);
        if (!questMetadata(value, plain) || hasPlayerMarker(value)) return false;
        String clean = plain.replaceAll("§[0-9A-FK-ORa-fk-or]", "").replace('\u00a0', ' ');
        String translated = engine.translatePreserving(clean, false, protectedNames);
        int colon = clean.indexOf(':'), targetColon = translated.indexOf('：');
        if (clean.equals(translated) || targetColon < 0) return false;
        JsonObject exact = new JsonObject();
        exact.addProperty(clean, translated);
        exact.addProperty(plain, translated);
        exact.addProperty(plain.substring(0, plain.indexOf(':') + 1), translated.substring(0, targetColon + 1));
        exact.addProperty(clean.substring(0, colon + 1), translated.substring(0, targetColon + 1));
        exact.addProperty(clean.substring(0, colon).trim(), translated.substring(0, targetColon));
        exact.addProperty(":", "：");
        exact.addProperty(clean.substring(colon + 1).trim(), translated.substring(targetColon + 1));
        JsonObject dictionary = new JsonObject(); dictionary.add("exact", exact);
        return new TranslationEngine(dictionary).rewriteVisible(value, false);
    }

    private static boolean hasPlayerMarker(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonArray()) { for (JsonElement child : value.getAsJsonArray()) if (hasPlayerMarker(child)) return true; }
        if (!value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        if (object.has("translate")) {
            String key = object.get("translate").getAsString();
            // Advancement broadcasts also use chat.type.*; they are server notices, not conversations.
            if (PLAYER_CHAT_KEYS.contains(key) || key.startsWith("commands.message.display.")) return true;
        }
        if (object.has("clickEvent") && object.get("clickEvent").isJsonObject()) {
            JsonObject click = object.getAsJsonObject("clickEvent");
            if (click.has("value") && CHAT_COMMAND.matcher(click.get("value").getAsString().stripLeading()).find()) return true;
        }
        if (object.has("hoverEvent") && object.get("hoverEvent").isJsonObject()) {
            JsonObject hover = object.getAsJsonObject("hoverEvent");
            if (hover.has("action") && hover.get("action").getAsString().equals("show_entity") && hover.has("contents") && hover.get("contents").isJsonObject()) {
                JsonObject entity = hover.getAsJsonObject("contents");
                if (entity.has("type") && entity.get("type").getAsString().equals("minecraft:player")) return true;
            }
        }
        // Hover text is metadata, not chat: only walk actual visible children.
        return object.has("extra") && hasPlayerMarker(object.get("extra"));
    }

    public static Set<String> protectedNames(String plain) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Pattern pattern : ANNOUNCEMENT_NAMES) {
            Matcher matcher = pattern.matcher(plain);
            while (matcher.find()) names.add(matcher.group(1));
        }
        return names;
    }

    /** Exact phrases/templates only: an unknown sentence is captured intact, without scattered word replacement. */
    public static boolean rewriteVisible(JsonElement value, TranslationEngine engine, Set<String> protectedNames) {
        if (value == null || value.isJsonNull()) return false;
        boolean changed = rewriteQuestMetadata(value, engine, protectedNames);
        if (value.isJsonObject()) changed |= rewriteServerHover(value.getAsJsonObject(), engine, protectedNames);
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String raw = child.getAsString(), translated = engine.translatePreserving(raw, false, protectedNames);
                    if (!raw.equals(translated)) {
                        JsonObject replacement = new JsonObject(); replacement.addProperty("text", raw); TranslationEngine.replaceTranslatedText(replacement, translated);
                        array.set(i, replacement); changed = true;
                    }
                } else changed |= rewriteVisible(child, engine, protectedNames);
            }
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonElement originalExtra = object.get("extra");
            if (originalExtra != null) changed |= rewriteVisible(originalExtra, engine, protectedNames);
            if (object.has("text") && object.get("text").isJsonPrimitive()) {
                String raw = object.get("text").getAsString(), translated = engine.translatePreserving(raw, false, protectedNames);
                if (!raw.equals(translated)) { TranslationEngine.replaceTranslatedText(object, translated); changed = true; }
            }
            // Vanilla system notices put names inside translation arguments rather than extra.
            // Their first argument is the player; nested player components also retain their name.
            if (object.has("translate") && object.has("with") && object.get("with").isJsonArray()) {
                String key = object.get("translate").getAsString();
                int first = key.startsWith("death.attack.player") ? 2
                    : key.startsWith("death.") || key.startsWith("chat.type.advancement.") ? 1
                    : key.equals("chat.square_brackets") ? 0 : -1;
                if (first >= 0) changed |= rewriteSystemArguments(object.getAsJsonArray("with"), first, engine, protectedNames);
            }
        }
        return changed;
    }

    /** Known dictionary phrases in server-authored help; native hover layout and other metadata stay intact. */
    private static boolean rewriteServerHover(JsonObject object, TranslationEngine engine, Set<String> protectedNames) {
        if (!object.has("hoverEvent")) return false;
        JsonObject hover = object.getAsJsonObject("hoverEvent");
        if (!hover.get("action").getAsString().equals("show_text")) return false;
        if (QuestHoverLayout.rewrite(hover, engine, protectedNames)) return true;
        JsonElement contents = hover.get("contents");
        if (contents.isJsonPrimitive() && contents.getAsJsonPrimitive().isString()) {
            String raw = contents.getAsString(), translated = engine.translatePreserving(raw, false, protectedNames);
            if (raw.equals(translated)) return false;
            JsonObject replacement = new JsonObject();
            TranslationEngine.replaceTranslatedText(replacement, translated);
            hover.add("contents", replacement);
            return true;
        }
        JsonObject component = contents.isJsonObject() ? contents.getAsJsonObject() : null;
        String originalText = component != null && component.has("text") ? component.get("text").getAsString() : "";
        int originalChildren = component != null && component.has("extra") ? component.getAsJsonArray("extra").size() : 0;
        boolean changed = engine.rewriteVisible(contents, false);
        // Keep a title's original line break before its existing styled description.
        if (changed && originalText.endsWith("\n") && component.has("extra")) {
            JsonArray extra = component.getAsJsonArray("extra");
            int boundary = extra.size() - originalChildren;
            if (boundary > 0 && !extra.get(boundary - 1).getAsJsonObject().get("text").getAsString().endsWith("\n")) {
                JsonArray preserved = new JsonArray();
                for (int i = 0; i < extra.size(); i++) {
                    if (i == boundary) preserved.add("\n");
                    preserved.add(extra.get(i));
                }
                component.add("extra", preserved);
            }
        }
        return changed;
    }

    private static boolean rewriteSystemArguments(JsonArray arguments, int first, TranslationEngine engine, Set<String> protectedNames) {
        boolean changed = false;
        for (int i = first; i < arguments.size(); i++) {
            JsonElement child = arguments.get(i);
            if (hasPlayerMarker(child)) continue;
            if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                String raw = child.getAsString(), translated = engine.translatePreserving(raw, false, protectedNames);
                if (!raw.equals(translated)) {
                    JsonObject replacement = new JsonObject(); replacement.addProperty("text", raw); TranslationEngine.replaceTranslatedText(replacement, translated);
                    arguments.set(i, replacement); changed = true;
                }
            } else changed |= rewriteVisible(child, engine, protectedNames);
        }
        return changed;
    }
}
