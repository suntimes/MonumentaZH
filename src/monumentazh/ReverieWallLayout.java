package monumentazh;
import com.google.gson.*;
import net.minecraft.class_2561;
import net.minecraft.class_8242;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Approved fifteen-sign wall. Match the complete original sign, never individual phrases. */
public final class ReverieWallLayout {
    private static final Map<List<String>,List<String>> RULES=load();
    private ReverieWallLayout() {}
    public static class_8242 apply(class_8242 original,class_8242 displayed) {
        class_8242 result=displayed;
        for(boolean filtered:List.of(false,true)) {
            List<String> keys=new ArrayList<>();boolean unsafe=false;
            for(int i=0;i<4;i++) {
                class_2561 line=original.method_49859(i,filtered);
                JsonElement json=class_2561.class_2562.method_10868(line);
                unsafe|=TranslationEngine.hasHiddenText(json)||CapturePrivacy.playerComponent(json);
                keys.add(TranslationEngine.normalize(line.getString()));
            }
            List<String> target=RULES.get(keys);
            if(unsafe||target==null)continue;
            class_2561 styleSource=original.method_49859(0,filtered);
            for(int i=0;i<4;i++) {
                class_2561 source=original.method_49859(i,filtered);
                class_2561 replacement=class_2561.method_43470(target.get(i)).method_10862(
                    source.getString().isBlank()?styleSource.method_10866():source.method_10866());
                result=result.method_49858(i,filtered?result.method_49859(i,false):replacement,
                    filtered?replacement:result.method_49859(i,true));
            }
        }
        return result;
    }
    private static Map<List<String>,List<String>> load() {
        try(InputStream in=ReverieWallLayout.class.getResourceAsStream("/reverie-wall.json")) {
            if(in==null)return Map.of();
            JsonArray rows=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();
            Map<List<String>,List<String>> rules=new HashMap<>();
            for(JsonElement row:rows) {
                List<String> s=new ArrayList<>(),t=new ArrayList<>();
                row.getAsJsonObject().getAsJsonArray("source_lines").forEach(x->s.add(TranslationEngine.normalize(x.getAsString())));
                row.getAsJsonObject().getAsJsonArray("target_lines").forEach(x->t.add(x.getAsString()));
                if(s.size()!=4||t.size()!=4||rules.put(List.copyOf(s),List.copyOf(t))!=null)throw new IOException("Invalid wall rule");
            }
            if(rules.size()!=15)throw new IOException("Incomplete wall");
            return Map.copyOf(rules);
        }catch(IOException|RuntimeException e){return Map.of();}
    }
}
