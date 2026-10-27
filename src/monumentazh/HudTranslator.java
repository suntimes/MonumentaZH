package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Translates only recognized ability status displays, preserving their live counters and styles. */
public final class HudTranslator {
    private static final Pattern BRACKETS = Pattern.compile("\\[([^]\\r\\n]+)]");
    private final TranslationEngine engine;
    private final Map<String, List<Alias>> aliases = new LinkedHashMap<>();
    private final Pattern aliasPattern;

    public HudTranslator(TranslationEngine engine, JsonObject dictionary) {
        this.engine = engine;
        if (dictionary.has("hudAliases")) {
            for (var entry : dictionary.getAsJsonObject("hudAliases").entrySet()) {
                List<Alias> candidates = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    JsonObject value = element.getAsJsonObject();
                    candidates.add(new Alias(value.get("name").getAsString(), value.get("group").getAsString(),
                        value.has("color") ? value.get("color").getAsString() : ""));
                }
                aliases.put(entry.getKey(), candidates);
            }
        }
        String keys = aliases.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElse("(?!)");
        aliasPattern = Pattern.compile("(?<![A-Za-z0-9])(?:" + keys + ")(?![A-Za-z0-9])");
    }

    public boolean recognizes(String visible) {
        Matcher labels = BRACKETS.matcher(visible);
        while (labels.find()) if (aliases.containsKey(labels.group(1))) return true;
        if (visible.contains("cooldown") || visible.contains("Cooldown") || visible.contains("Charges:")
            || visible.contains("Stacks:") || visible.contains("Chain:")) return true;
        return aliases.keySet().stream().filter(key -> key.length() > 4)
            .anyMatch(key -> visible.startsWith(key + ":") || visible.startsWith(key + " "));
    }

    public boolean rewrite(JsonElement component, String visible) {
        if (!recognizes(visible)) return false;
        Set<String> groups = new HashSet<>();
        Matcher labels = BRACKETS.matcher(visible);
        while (labels.find()) {
            List<Alias> candidates = aliases.get(labels.group(1));
            if (candidates != null && candidates.stream().map(Alias::group).distinct().count() == 1)
                groups.add(candidates.get(0).group());
        }
        boolean changed = rewriteAliases(component, "", groups);
        return engine.rewriteVisible(component, true) || changed;
    }

    private boolean rewriteAliases(JsonElement value, String inheritedColor, Set<String> groups) {
        boolean changed = false;
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String source = child.getAsString();
                    String result = replaceAliases(source, inheritedColor, groups);
                    if (!source.equals(result)) {
                        JsonObject replacement = new JsonObject();
                        TranslationEngine.replaceTranslatedText(replacement, result);
                        array.set(i, replacement);
                        changed = true;
                    }
                } else changed |= rewriteAliases(child, inheritedColor, groups);
            }
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String color = object.has("color") ? object.get("color").getAsString() : inheritedColor;
            if (object.has("text") && object.get("text").isJsonPrimitive()) {
                String source = object.get("text").getAsString();
                String result = replaceAliases(source, color, groups);
                if (!source.equals(result)) {
                    TranslationEngine.replaceTranslatedText(object, result);
                    changed = true;
                }
            }
            if (object.has("extra")) changed |= rewriteAliases(object.get("extra"), color, groups);
        }
        return changed;
    }

    private String replaceAliases(String source, String color, Set<String> groups) {
        Matcher matcher = aliasPattern.matcher(source);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            List<Alias> candidates = aliases.get(matcher.group());
            Alias resolved = candidates.size() == 1 ? candidates.get(0) : resolve(candidates, color, groups);
            String replacement = resolved == null ? matcher.group() : engine.translate(resolved.name(), false);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return engine.translate(output.toString(), true);
    }

    private Alias resolve(List<Alias> candidates, String color, Set<String> groups) {
        List<Alias> colored = candidates.stream().filter(alias -> colorsMatch(color, alias.color())).toList();
        if (colored.size() == 1) return colored.get(0);
        List<Alias> contextual = candidates.stream().filter(alias -> groups.contains(alias.group())).toList();
        return contextual.size() == 1 ? contextual.get(0) : null;
    }

    private static boolean colorsMatch(String actual, String expected) {
        if (actual.isEmpty() || expected.isEmpty()) return false;
        if (actual.equalsIgnoreCase(expected)) return true;
        if (!actual.matches("#[0-9a-fA-F]{6}") || !expected.matches("#[0-9a-fA-F]{6}")) return false;
        int a = Integer.parseInt(actual.substring(1), 16), b = Integer.parseInt(expected.substring(1), 16);
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) <= 2
            && Math.abs((a >> 8 & 255) - (b >> 8 & 255)) <= 2 && Math.abs((a & 255) - (b & 255)) <= 2;
    }

    private record Alias(String name, String group, String color) {}
}
