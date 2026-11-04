package monumentazh;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
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
    private long revision;
    private Path requestedCheckpoint;
    private SnapshotWork snapshotWork;
    private Future<SaveResult> saving;
    private ExecutorService writer;
    private static final long SNAPSHOT_BUDGET_NANOS = 2_000_000L;
    private static final int SNAPSHOT_RECORDS_PER_TICK = 32;
    // Store records belong to the client thread. The writer receives only detached, cleaned copies.
    @FunctionalInterface interface WriteSink { void write(Path path, String contents) throws IOException; }
    private final WriteSink sink;
    public EncounteredTextStore() { this(EncounteredTextStore::atomicWrite); }
    EncounteredTextStore(WriteSink sink) { this.sink = Objects.requireNonNull(sink); }
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
        revision++;
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
        revision++;
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
        // The private record objects never escape or change after insertion. Detach the page's
        // containers now; copy/privacy work is budgeted below, rather than stalling rollover.
        pendingArchives.put(filename, rawSnapshot());
        archiveFiles.add(filename);
        items.clear();
        texts.clear();
        dirty = true;
    }

    public JsonObject snapshot() {
        return CapturePrivacy.cleanCapture(rawSnapshot());
    }

    private JsonObject rawSnapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("format", "monumenta-zh-capture-v3");
        root.addProperty("identity_redacted",true);
        root.addProperty("exported_at", Instant.now().toString());
        JsonArray itemArray = new JsonArray();
        items.values().forEach(itemArray::add);
        JsonArray textArray = new JsonArray();
        texts.values().forEach(textArray::add);
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
        return root;
    }

    public void write(Path path) throws IOException {
        // Explicit export/flush waits for an older writer before writing the latest generation.
        // Otherwise its stale checkpoint could replace a newer manual export afterwards.
        requestedCheckpoint = null;
        snapshotWork = null;
        if (saving != null) acceptSave(awaitSave());
        Path directory = path.toAbsolutePath().getParent().resolve("capture_archive");
        var iterator = pendingArchives.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            sink.write(directory.resolve(entry.getKey()), GSON.toJson(CapturePrivacy.cleanCapture(entry.getValue())));
            // Failed writes remain queued. Successful archives are never overwritten by a new page.
            iterator.remove();
        }
        sink.write(path, GSON.toJson(snapshot()));
        dirty = false;
    }
    public void checkpoint(Path path) throws IOException { if (dirty) write(path); }

    /** Coalesce autosave requests; this call performs no copying, JSON encoding or disk I/O. */
    public void requestCheckpoint(Path path) { if (dirty) requestedCheckpoint = path; }

    /** Client-thread pump: at most 32 records and a 2ms soft budget, then one background writer. */
    public void tickCheckpoint() throws IOException {
        long started = System.nanoTime();
        if (saving != null && saving.isDone()) {
            IOException failure = acceptSave(awaitSave());
            if (failure != null) throw failure;
        }
        if (saving != null) return;
        if (snapshotWork == null) {
            if (requestedCheckpoint == null) return;
            Path target = requestedCheckpoint; requestedCheckpoint = null;
            if (!dirty) return;
            snapshotWork = new SnapshotWork(target);
        }
        int records = 0;
        while (records < SNAPSHOT_RECORDS_PER_TICK && System.nanoTime() - started < SNAPSHOT_BUDGET_NANOS) {
            if (snapshotWork.step()) { records++; continue; }
            SnapshotWork ready = snapshotWork; snapshotWork = null;
            if (writer == null) writer = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "MonumentaZH capture writer"); thread.setDaemon(true); return thread;
            });
            saving = writer.submit(ready::save);
            return;
        }
    }

    /** Shutdown is an explicit flush point; do not leave a daemon writer holding unsaved data. */
    public void close(Path path) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            for (;;) {
                try { write(path); break; }
                catch (IOException ex) {
                    if (!(ex.getCause() instanceof InterruptedException) && !(ex instanceof java.nio.channels.ClosedByInterruptException)) throw ex;
                    interrupted = true; Thread.interrupted();
                }
            }
        } finally {
            if (writer != null) writer.shutdown();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private SaveResult awaitSave() throws IOException {
        try {
            SaveResult result = saving.get(); saving = null; return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IOException("Interrupted while waiting for capture save", ex);
        } catch (ExecutionException ex) {
            saving = null; return new SaveResult(-1, List.of(), new IOException("Capture writer failed", ex.getCause()));
        }
    }

    private IOException acceptSave(SaveResult result) {
        result.archives.forEach(pendingArchives::remove);
        if (result.failure == null && revision == result.revision) dirty = false;
        return result.failure;
    }

    private record SaveResult(long revision, List<String> archives, IOException failure) {}

    private final class SnapshotWork {
        final CapturePrivacy.SnapshotContext privacy = CapturePrivacy.snapshotContext();
        final long version = revision;
        final Path target;
        final List<String> archiveNames = new ArrayList<>(pendingArchives.keySet());
        final List<JsonObject> sources = new ArrayList<>(pendingArchives.values());
        final List<JsonObject> frozen = new ArrayList<>();
        int page, group, record;
        JsonObject current;
        SnapshotWork(Path target) { this.target = target; sources.add(rawSnapshot()); }
        // Only this method touches CapturePrivacy, on the client thread. Each output record is
        // independently cleaned/copied; no mutable store or privacy state reaches the writer.
        boolean step() {
            while (page < sources.size()) {
                JsonObject source = sources.get(page);
                if (current == null) {
                    current = new JsonObject();
                    for (var e : source.entrySet()) if (!e.getKey().equals("items") && !e.getKey().equals("texts")) current.add(e.getKey(), e.getValue().deepCopy());
                    current.add("items", new JsonArray()); current.add("texts", new JsonArray()); frozen.add(current);
                }
                String key = group == 0 ? "items" : "texts";
                JsonArray entries = source.getAsJsonArray(key);
                if (record < entries.size()) {
                    JsonObject fragment = new JsonObject(); JsonArray one = new JsonArray(); one.add(entries.get(record++)); fragment.add(key, one);
                    JsonArray cleaned = CapturePrivacy.cleanCapture(fragment, privacy).getAsJsonArray(key);
                    for (var value : cleaned) current.getAsJsonArray(key).add(value);
                    return true;
                }
                record = 0;
                if (group == 0) group = 1;
                else { page++; group = 0; current = null; }
            }
            return false;
        }
        // No shared map/record/privacy access here: frozen is owned exclusively by this task.
        SaveResult save() {
            List<String> completed = new ArrayList<>();
            try {
                Path directory = target.toAbsolutePath().getParent().resolve("capture_archive");
                for (int i = 0; i < archiveNames.size(); i++) {
                    sink.write(directory.resolve(archiveNames.get(i)), GSON.toJson(frozen.get(i)));
                    completed.add(archiveNames.get(i));
                }
                sink.write(target, GSON.toJson(frozen.get(frozen.size() - 1)));
                return new SaveResult(version, completed, null);
            } catch (Exception ex) {
                return new SaveResult(version, completed, ex instanceof IOException io ? io : new IOException("Capture serialization failed", ex));
            }
        }
    }
    public int itemCount() { return items.size(); }
    public int textCount() { return texts.size(); }
    public int archivedBatchCount() { return archiveFiles.size(); }
    public static boolean isChatKind(String kind) {
        return "player_chat".equals(kind) || "chat".equals(kind);
    }
    private static String normalizedKind(String kind, String raw, JsonElement component) {
        if (isChatKind(kind)) return null;
        if ("server_message".equals(kind) || "npc_dialogue".equals(kind) || "server_notice".equals(kind)) {
            if(ChatMessageFilter.isLocalModMessage(raw))return null;
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
