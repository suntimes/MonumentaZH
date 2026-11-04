package monumentazh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Only user-reviewed component identities are excluded; shared game terms still render normally. */
public final class CaptureExclusions {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final Map<String,Set<Integer>> CANDIDATES=new HashMap<>();
    private static final Set<String> IDS=new HashSet<>();
    static {
        try(InputStream in=CaptureExclusions.class.getResourceAsStream("/capture-exclusions.json")) {
            if(in!=null) {
                JsonObject data=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                for(var id:data.getAsJsonArray("ids"))IDS.add(id.getAsString());
                for(var entry:data.getAsJsonObject("candidate_hashes").entrySet()) {
                    Set<Integer> hashes=new HashSet<>();for(var hash:entry.getValue().getAsJsonArray())hashes.add(hash.getAsInt());
                    CANDIDATES.put(entry.getKey(),hashes);
                }
            }
        } catch(IOException|RuntimeException error) { CANDIDATES.clear();IDS.clear(); }
    }
    private CaptureExclusions() {}
    public static boolean excluded(String kind,String raw,JsonElement component) {
        Set<Integer> hashes=CANDIDATES.get(kind);
        if(raw==null||hashes==null||!hashes.contains(raw.hashCode()))return false;
        JsonArray pair=new JsonArray();pair.add(kind);pair.add(sorted(component));
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(JSON.toJson(pair).getBytes(StandardCharsets.UTF_8));
            return IDS.contains(HexFormat.of().formatHex(digest));
        } catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    private static JsonElement sorted(JsonElement value) {
        if(value==null||value.isJsonNull())return JsonNull.INSTANCE;
        if(value.isJsonObject()) {
            JsonObject copy=new JsonObject();for(String key:new TreeSet<>(value.getAsJsonObject().keySet()))copy.add(key,sorted(value.getAsJsonObject().get(key)));return copy;
        }
        if(value.isJsonArray()) {JsonArray copy=new JsonArray();for(var child:value.getAsJsonArray())copy.add(sorted(child));return copy;}
        return value.deepCopy();
    }
}
