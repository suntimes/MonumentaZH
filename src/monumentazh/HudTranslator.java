package monumentazh;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Translates recognized ability and encounter status displays, preserving live counters and styles. */
public final class HudTranslator {
    private static final Pattern BRACKETS = Pattern.compile("\\[([^]\\r\\n]+)]");
    private static final Pattern KAUL_WAKES = Pattern.compile("^Kaul Wakes: ([0-9]+)s$");
    private static final Pattern CORRUPTION = Pattern.compile("^Time Until Corruption : ([0-9]+)s$");
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
        if (KAUL_WAKES.matcher(visible).matches() || CORRUPTION.matcher(visible).matches()) return true;
        Matcher labels = BRACKETS.matcher(visible);
        while (labels.find()) if (aliases.containsKey(labels.group(1))) return true;
        if (visible.contains("cooldown") || visible.contains("Cooldown") || visible.contains("Charges:")
            || visible.contains("Stacks:") || visible.contains("Chain:")) return true;
        return aliases.keySet().stream().filter(key -> key.length() > 4)
            .anyMatch(key -> visible.startsWith(key + ":") || visible.startsWith(key + " "));
    }

    public boolean rewrite(JsonElement component, String visible) {
        // Only this complete server countdown uses this scoped rewrite. Never teach the
        // general dictionary a short "Wakes"/"s" fragment or relax identity filtering.
        if (KAUL_WAKES.matcher(visible).matches()) return rewriteKaulCountdown(component, visible);
        if (CORRUPTION.matcher(visible).matches()) return rewriteCorruptionCountdown(component, visible);
        if (!recognizes(visible)) return false;
        // Complete single-leaf messages take precedence over skill aliases.
        // Do not flatten multi-colour/translated components or embedded section formatting.
        if (visible.indexOf('§') < 0 && component.isJsonObject()) {
            JsonObject object = component.getAsJsonObject();
            if (object.has("text") && object.get("text").isJsonPrimitive()
                && object.get("text").getAsJsonPrimitive().isString()
                && object.get("text").getAsString().equals(visible)
                && !object.has("extra") && !object.has("translate") && !object.has("with")
                && !object.has("keybind") && !object.has("selector") && !object.has("score") && !object.has("nbt")) {
                String exact = engine.exactTranslation(visible);
                if (exact != null && !exact.equals(visible)) {
                    TranslationEngine.replaceTranslatedText(object, exact);
                    return true;
                }
            }
        }

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

    private boolean rewriteKaulCountdown(JsonElement component, String visible) {
        return rewriteCountdown(component,visible,KAUL_WAKES.matcher(visible),engine.translate(visible,false));
    }

    private boolean rewriteCorruptionCountdown(JsonElement component,String visible) {
        Matcher match=CORRUPTION.matcher(visible);
        if(!match.matches())return false;
        String translated=engine.translate(visible,false);
        if(translated.equals(visible)) {
            // Respect a user-changed complete countdown sample instead of overriding it.
            if(!"距离腐化：5秒".equals(engine.exactTranslation("Time Until Corruption : 5s")))return false;
            translated="距离腐化："+match.group(1)+"秒";
        }
        return rewriteCountdown(component,visible,match,translated);
    }

    private boolean rewriteCountdown(JsonElement component,String visible,Matcher match,String translated) {
        if (CapturePrivacy.playerComponent(component) || translated.equals(visible) || !match.matches()) return false;
        Matcher translatedTime = Pattern.compile("(?<![0-9])" + Pattern.quote(match.group(1)) + "(?![0-9])").matcher(translated);
        if (!translatedTime.find()) return false;
        int numberStart = translatedTime.start(), numberEnd = translatedTime.end();
        if (translatedTime.find()) return false;
        List<CountdownRun> runs = new ArrayList<>();
        if (!countdownRuns(component, runs)) return false;
        StringBuilder source = new StringBuilder();
        for (CountdownRun run : runs) source.append(run.text());
        if (!source.toString().equals(visible)) return false;
        String prefix = translated.substring(0, numberStart), suffix = translated.substring(numberEnd);
        List<String> replacements = new ArrayList<>();
        int position = 0;
        for (CountdownRun run : runs) {
            int start = position, end = position + run.text().length();
            StringBuilder replacement = new StringBuilder();
            // Insert the prose at its original label/unit locations. The digits are copied
            // from their own source runs, including counters split across multiple styles.
            if (start == 0 && end > 0) replacement.append(prefix);
            for (int i = start; i < end; i++) {
                if (i == match.end(1)) replacement.append(suffix);
                if (i >= match.start(1) && i < match.end(1)) replacement.append(visible.charAt(i));
            }
            replacements.add(replacement.toString());
            position = end;
        }
        // Children first: a translated parent creates a fresh extra array for font runs.
        for (int i = runs.size() - 1; i >= 0; i--)
            if (!replacements.get(i).equals(runs.get(i).text())) runs.get(i).replace(replacements.get(i));
        return true;
    }

    private static boolean countdownRuns(JsonElement value, List<CountdownRun> runs) {
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString())
                    runs.add(new CountdownRun(null, array, i, child.getAsString()));
                else if (!countdownRuns(child, runs)) return false;
            }
            return true;
        }
        if (!value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        if (object.has("translate") || object.has("keybind") || object.has("selector") || object.has("score") || object.has("nbt")) return false;
        if (object.has("text")) {
            if (!object.get("text").isJsonPrimitive() || !object.getAsJsonPrimitive("text").isString()) return false;
            runs.add(new CountdownRun(object, null, -1, object.get("text").getAsString()));
        }
        return !object.has("extra") || countdownRuns(object.get("extra"), runs);
    }

    private record CountdownRun(JsonObject object, JsonArray array, int index, String text) {
        void replace(String translated) {
            if (object != null) TranslationEngine.replaceTranslatedText(object, translated);
            else {
                JsonObject replacement = new JsonObject();
                replacement.addProperty("text", text);
                TranslationEngine.replaceTranslatedText(replacement, translated);
                array.set(index, replacement);
            }
        }
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
