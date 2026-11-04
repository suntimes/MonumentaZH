package monumentazh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Applies audited color-run wording only inside one complete, known server bounty. */
public final class BountyDialogue {
    private record Entry(String source, String body, String targetBody, TranslationEngine local) {}
    // Resources and local engines are prepared once; the display path performs no file IO.
    private static final Map<String, Entry> ENTRIES = load();

    private BountyDialogue() {}

    /** The caller supplies a serialized display copy; server components are never modified. */
    public static boolean rewrite(JsonElement json, String source, TranslationEngine global) {
        if (json == null || json.isJsonNull() || source == null || global == null) return false;
        String sourceKey = TranslationEngine.normalize(source);
        Entry entry = ENTRIES.get(sourceKey);
        if (entry == null || ChatMessageFilter.classify(json, source) == ChatMessageFilter.Kind.PLAYER) return false;
        // A later user override wins over this bundled, context-specific run allocation.
        if (!TranslationEngine.normalize(global.translate(entry.body(), false))
            .equals(TranslationEngine.normalize(entry.targetBody()))) return false;
        if (sourceKey.equals(TranslationEngine.normalize(entry.source()))) {
            String expected = entry.source().equals(entry.body()) ? entry.targetBody()
                : "[传令官] " + entry.targetBody();
            if (!TranslationEngine.normalize(global.translate(source, false))
                .equals(TranslationEngine.normalize(expected))) return false;
        }
        return entry.local().rewriteVisible(json, false);
    }

    private static Map<String, Entry> load() {
        try (InputStream stream = BountyDialogue.class.getResourceAsStream("/monumenta_bounties.json")) {
            if (stream == null) return Map.of();
            JsonObject resource;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                resource = JsonParser.parseReader(reader).getAsJsonObject();
            }
            Map<String, Entry> entries = new HashMap<>();
            for (JsonElement value : resource.getAsJsonArray("entries")) {
                JsonObject row = value.getAsJsonObject();
                String source = row.get("guard_exact_source").getAsString();
                String body = row.get("guard_exact_body").getAsString();
                String target = row.get("target_body").getAsString();
                String sourceKey = TranslationEngine.normalize(source);
                String bodyKey = TranslationEngine.normalize(body);
                boolean herald = source.startsWith("[Herald] ")
                    && TranslationEngine.normalize(source.substring("[Herald] ".length())).equals(bodyKey);
                // The captured Chinese announcement includes its title in the approved body.
                boolean chineseNotice = source.startsWith("国王峡谷悬赏任务:") && sourceKey.equals(bodyKey);
                if ((!herald && !chineseNotice) || bodyKey.isEmpty()
                    || TranslationEngine.normalize(target).isEmpty())
                    throw new IOException("Invalid complete server bounty guard");
                JsonObject exact = row.getAsJsonObject("exact");
                Map<String, String> checked = new HashMap<>();
                for (Map.Entry<String, JsonElement> pair : exact.entrySet()) {
                    String key = TranslationEngine.normalize(pair.getKey());
                    String translated = pair.getValue().getAsString();
                    if (key.isEmpty()) throw new IOException("Empty contextual bounty run");
                    String previous = checked.putIfAbsent(key, translated);
                    if (previous != null && !previous.equals(translated))
                        throw new IOException("Conflicting contextual bounty run: " + key);
                }
                if (!target.equals(checked.get(bodyKey)))
                    throw new IOException("Contextual bounty body differs from its full translation");
                JsonObject dictionary = new JsonObject();
                dictionary.add("exact", exact);
                Entry entry = new Entry(source, body, target, new TranslationEngine(dictionary));
                if (entries.putIfAbsent(sourceKey, entry) != null
                    || !sourceKey.equals(bodyKey) && entries.putIfAbsent(bodyKey, entry) != null)
                    throw new IOException("Duplicate contextual bounty guard");
            }
            return Collections.unmodifiableMap(entries);
        } catch (Exception ex) {
            // A malformed optional resource must fall back to the normal dictionary flow.
            System.err.println("[MonumentaZH] Contextual bounty wording disabled: " + ex.getMessage());
            return Map.of();
        }
    }
}
