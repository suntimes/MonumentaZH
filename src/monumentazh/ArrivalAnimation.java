package monumentazh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Resolve audited layout targets; progress always comes from the server's current text. */
public final class ArrivalAnimation {
    private record Rules(Map<String,String> descriptions,Set<String> candidates) {}
    private static final Rules RULES=load();
    private ArrivalAnimation() {}
    private static Rules load() {
        Map<String,String> descriptions=new LinkedHashMap<>();Set<String> candidates=new LinkedHashSet<>();
        try(InputStream input=ArrivalAnimation.class.getResourceAsStream("/arrival-animation.json")) {
            if(input!=null) {
                JsonObject json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
                if(json.has("descriptions"))for(var pair:json.getAsJsonObject("descriptions").entrySet()) {
                    String full=pair.getValue().getAsString();if(!full.isEmpty()){descriptions.put(pair.getKey(),full);}
                }
                if(json.has("subtitle_candidates"))for(JsonElement value:json.getAsJsonArray("subtitle_candidates")) {
                    String full=value.getAsString();if(!full.isEmpty())candidates.add(full);
                }
            }
        } catch(Exception ex) {System.err.println("[MonumentaZH] Arrival animation rules unavailable: "+ex.getMessage());}
        return new Rules(Map.copyOf(descriptions),Set.copyOf(candidates));
    }
    public static boolean isArrivalPrefix(String raw) {
        if(raw==null||raw.isEmpty())return false;
        for(String city:RULES.descriptions().keySet())if(city.startsWith(raw))return true;
        return false;
    }
    public static String completeSubtitle(String cityEnglish,String raw) {
        if(raw==null)return null;
        String bound=cityEnglish==null?null:RULES.descriptions().get(cityEnglish);
        if(bound!=null&&bound.startsWith(raw))return bound;
        String found=null;
        for(String full:RULES.candidates())if(full.startsWith(raw)) {
            if(found!=null&&!found.equals(full))return raw;
            found=full;
        }
        return found==null?raw:found;
    }
    public static int visible(String full,String raw) {
        if(full==null)return 0;
        if(raw!=null&&full.startsWith(raw))return raw.codePointCount(0,raw.length());
        return full.codePointCount(0,full.length());
    }
    public static boolean isStaleSubtitle(String cityEnglish,String raw) {
        if(raw==null||raw.codePointCount(0,raw.length())<=1)return false;
        String bound=cityEnglish==null?null:RULES.descriptions().get(cityEnglish);
        if(bound==null||bound.startsWith(raw))return false;
        for(var pair:RULES.descriptions().entrySet())
            if(!pair.getKey().equals(cityEnglish)&&pair.getValue().startsWith(raw))return true;
        return false;
    }
}
