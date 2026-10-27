package monumentazh;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Offline collection with bounded pages; full pages are archived instead of stopping collection. */
public final class EncounteredTextStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern ENGLISH = Pattern.compile("[A-Za-z]{2,}");
    private static final int ITEM_PAGE_SIZE = 2000;
    private static final int TEXT_PAGE_SIZE = 5000;
    private final Map<String, JsonObject> items = new LinkedHashMap<>();
    private final Map<String, JsonObject> texts = new LinkedHashMap<>();
    // Keep only deduplication keys for old pages, rather than every archived component tree.
    private final Set<String> seenItems = new HashSet<>();
    private final Set<String> seenTexts = new HashSet<>();
    private final Set<String> seenBookComponents = new HashSet<>();
    private final Set<String> archiveFiles = new LinkedHashSet<>();
    private final Map<String, JsonObject> pendingArchives = new LinkedHashMap<>();
    private boolean dirty;
    private final LinkedHashSet<String> sourceItems=new LinkedHashSet<>(),sourceTexts=new LinkedHashSet<>();
    private static boolean repeated(LinkedHashSet<String> set,String key){if(set.contains(key))return true;if(set.size()>=4096)set.remove(set.iterator().next());set.add(key);return false;}

    public void addItem(JsonObject item) {
        if(repeated(sourceItems,itemKey(item)))return;
        item=CapturePrivacy.item(item);
        String key = itemKey(item);
        if (seenItems.contains(key)) return;
        if (items.size() >= ITEM_PAGE_SIZE) archivePage();
        items.put(key, item.deepCopy());
        seenItems.add(key);
        dirty = true;
    }

    public void addText(String kind, String raw, JsonElement component, String preview) {
        if (raw == null || raw.length() > 8192 || !ENGLISH.matcher(raw).find()) return;
        kind = normalizedKind(kind, raw, component);
        if (kind == null) return;
        if(repeated(sourceTexts,kind+"\n"+canonical(kind,raw)+"\n"+CapturePrivacy.playerComponent(component)))return;
        JsonObject safe=CapturePrivacy.text(kind,raw,component,preview);
        if(safe==null)return;
        raw=safe.get("text").getAsString();component=safe.get("component");preview=safe.get("translated_preview").getAsString();
        String canonical = canonical(kind, raw);
        String key = kind + "\n" + canonical;
        if (seenTexts.contains(key)) return;
        if (texts.size() >= TEXT_PAGE_SIZE) archivePage();
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", kind);
        entry.addProperty("text", raw);
        entry.addProperty("match_example", canonical);
        entry.add("component", component.deepCopy());
        entry.addProperty("translated_preview", preview);
        entry.addProperty("first_seen", Instant.now().toString());
        texts.put(key, entry);
        seenTexts.add(key);
        if(kind.equals("book_page"))seenBookComponents.add(component.toString());
        dirty = true;
    }

    /** Loaded archives/current records can be recognized before building another translated preview. */
    public boolean hasBookPage(String raw) {
        return raw!=null&&seenTexts.contains("book_page\n"+canonical("book_page",raw));
    }
    public boolean hasBookComponent(String rawJson) { return seenBookComponents.contains(rawJson); }

    public void load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return;
        JsonObject root = CapturePrivacy.cleanCapture(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject());
        if (root.has("items")) for (JsonElement value : root.getAsJsonArray("items")) {
            if (value.isJsonObject() && value.getAsJsonObject().has("name") && value.getAsJsonObject().has("lore"))
                addItem(value.getAsJsonObject());
        }
        if (root.has("texts")) for (JsonElement value : root.getAsJsonArray("texts")) {
            if (!value.isJsonObject()) continue;
            JsonObject item = value.getAsJsonObject();
            if (!item.has("kind") || !item.has("text") || !item.has("component")) continue;
            String kind = item.get("kind").getAsString();
            String raw = item.get("text").getAsString();
            kind = normalizedKind(kind, raw, item.get("component"));
            if (kind == null) continue;
            addText(kind, raw, item.get("component"), item.has("translated_preview") ? item.get("translated_preview").getAsString() : raw);
            String canonical = canonical(kind, raw);
            JsonObject saved = texts.get(kind + "\n" + canonical);
            if (saved != null && item.has("first_seen")) saved.add("first_seen", item.get("first_seen").deepCopy());
        }
    }

    /** Index history once on startup, so old menus do not fill the next page again. */
    public void loadArchives(Path configDirectory) throws IOException {
        Path directory = configDirectory.resolve("capture_archive");
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "batch-*.json")) {
            for (Path path : files) {
                JsonObject root = CapturePrivacy.cleanCapture(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject());
                if (root.has("items")) for (JsonElement value : root.getAsJsonArray("items")) {
                    if (value.isJsonObject() && value.getAsJsonObject().has("name") && value.getAsJsonObject().has("lore"))
                        seenItems.add(itemKey(value.getAsJsonObject()));
                }
                if (root.has("texts")) for (JsonElement value : root.getAsJsonArray("texts")) {
                    if (!value.isJsonObject()) continue;
                    JsonObject entry = value.getAsJsonObject();
                    if (!entry.has("kind") || !entry.has("text") || !entry.has("component")) continue;
                    String raw = entry.get("text").getAsString();
                    String kind = normalizedKind(entry.get("kind").getAsString(), raw, entry.get("component"));
                    if (kind != null) {
                        seenTexts.add(kind + "\n" + canonical(kind, raw));
                        if(kind.equals("book_page"))seenBookComponents.add(entry.get("component").toString());
                    }
                }
                archiveFiles.add(path.getFileName().toString());
            }
        }
    }

    private static String itemKey(JsonObject item) {
        String rawName=item.get("name").getAsString(),quantityName=QuantityText.captureKey(TranslationEngine.normalize(rawName));
        String name=quantityName==null?rawName:quantityName;JsonArray lore=new JsonArray();boolean changed=false;
        for(JsonElement line:item.getAsJsonArray("lore")) {
            String raw=line.getAsString(),key=QuantityText.captureKey(TranslationEngine.normalize(raw));
            if(key!=null){lore.add(key);changed=true;}else lore.add(line);
        }
        return name + "\n" + (changed?lore:item.get("lore"));
    }

    private static String canonical(String kind, String raw) {
        String value = TranslationEngine.normalize(raw);
        if(kind.equals("tooltip")||kind.equals("item_name")) {
            String quantity=QuantityText.captureKey(value);if(quantity!=null)return quantity;
        }
        // One example per ticking HUD phrase, rather than one entry per frame/count.
        return kind.equals("bossbar") || kind.equals("actionbar")
            ? value.replaceAll("-?\\d+(?:\\.\\d+)?", "{n}") : value;
    }

    private void archivePage() {
        String filename = "batch-" + Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID() + ".json";
        pendingArchives.put(filename, snapshot());
        archiveFiles.add(filename);
        items.clear();
        texts.clear();
        dirty = true;
    }

    public JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("format", "monumenta-zh-capture-v3");
        root.addProperty("identity_redacted",true);
        root.addProperty("exported_at", Instant.now().toString());
        JsonArray itemArray = new JsonArray();
        items.values().forEach(value -> itemArray.add(value.deepCopy()));
        JsonArray textArray = new JsonArray();
        texts.values().forEach(value -> textArray.add(value.deepCopy()));
        root.add("items", itemArray);
        root.add("texts", textArray);
        // Retain the v3 schema for existing translation tools; these limits now mean page boundaries.
        root.addProperty("item_limit_reached", items.size() >= ITEM_PAGE_SIZE);
        root.addProperty("text_limit_reached", texts.size() >= TEXT_PAGE_SIZE);
        root.addProperty("collection_continues", true);
        root.addProperty("archive_directory", "capture_archive");
        JsonArray archives = new JsonArray();
        archiveFiles.forEach(archives::add);
        root.add("archive_files", archives);
        return CapturePrivacy.cleanCapture(root);
    }

    public void write(Path path) throws IOException {
        Path directory = path.toAbsolutePath().getParent().resolve("capture_archive");
        var iterator = pendingArchives.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            atomicWrite(directory.resolve(entry.getKey()), GSON.toJson(CapturePrivacy.cleanCapture(entry.getValue())));
            // Failed writes remain queued. Successful archives are never overwritten by a new page.
            iterator.remove();
        }
        atomicWrite(path, GSON.toJson(snapshot()));
        dirty = false;
    }
    public void checkpoint(Path path) throws IOException { if (dirty) write(path); }
    public int itemCount() { return items.size(); }
    public int textCount() { return texts.size(); }
    public int archivedBatchCount() { return archiveFiles.size(); }
    public static boolean isChatKind(String kind) {
        return "player_chat".equals(kind) || "chat".equals(kind);
    }
    private static String normalizedKind(String kind, String raw, JsonElement component) {
        if (isChatKind(kind)) return null;
        if ("server_message".equals(kind) || "npc_dialogue".equals(kind) || "server_notice".equals(kind)) {
            return switch (ChatMessageFilter.classify(component, raw)) {
                case PLAYER -> null;
                case NPC -> "npc_dialogue";
                case SYSTEM -> "server_notice";
            };
        }
        return kind;
    }

    public static void atomicWrite(Path path, String contents) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
