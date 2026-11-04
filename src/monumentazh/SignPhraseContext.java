package monumentazh;

import com.google.gson.*;
import net.minecraft.class_2561;
import net.minecraft.class_327;
import net.minecraft.class_8242;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Complete audited adjacent sign phrases, applied only while rebuilding displaySign's cache. */
public final class SignPhraseContext {
    private record Rule(List<String> sources,List<String> targets,List<String> guardLines,
                        String guardSource,String guardTarget,TranslationEngine local) {}
    private static final List<Rule> RULES=load();
    private SignPhraseContext() {}

    /** The caller gates this on enabled/signTranslationEnabled and keeps its existing weak sign cache. */
    public static class_8242 apply(class_8242 original,class_8242 displayed,int maxWidth,
                                  TranslationEngine global,class_327 font) {
        if(original==null||displayed==null||global==null||font==null||maxWidth<=0||RULES.isEmpty())return displayed;
        class_8242 result=displayed;
        for(boolean filtered:List.of(false,true)) {
            for(int at=0;at<4;at++) {
                for(Rule rule:RULES) {
                    int count=rule.sources().size();
                    if(at+count>4||!key(original.method_49859(at,filtered).getString()).equals(key(rule.sources().get(0))))continue;
                    // A changed full phrase or any changed per-line user override wins over this layout.
                    if(!global.translate(rule.guardSource(),false).equals(rule.guardTarget()))continue;
                    boolean matches=true;
                    for(int row=0;row<count;row++) {
                        if(!key(original.method_49859(at+row,filtered).getString()).equals(key(rule.sources().get(row)))
                            ||!global.translate(rule.sources().get(row),false).equals(rule.guardLines().get(row))) {
                            matches=false;break;
                        }
                    }
                    if(!matches)continue;
                    List<class_2561> replacements=new ArrayList<>(count);
                    for(int row=0;row<count;row++) {
                        class_2561 source=original.method_49859(at+row,filtered);
                        JsonElement copy=class_2561.class_2562.method_10868(source).deepCopy();
                        if(copy.isJsonPrimitive()){JsonObject wrapped=new JsonObject();wrapped.add("text",copy);copy=wrapped;}
                        if(CapturePrivacy.playerComponent(copy)||!rule.local().rewriteVisible(copy,false)){matches=false;break;}
                        class_2561 rewritten=class_2561.class_2562.method_10872(copy);
                        if(rewritten==null||!key(rewritten.getString()).equals(key(rule.targets().get(row)))
                            ||font.method_27525(rewritten)>maxWidth) {matches=false;break;}
                        replacements.add(rewritten);
                    }
                    if(!matches)continue;
                    // Commit all rows together; the other filtered variant and unrelated rows stay untouched.
                    for(int row=0;row<count;row++) {
                        int index=at+row;class_2561 value=replacements.get(row);
                        result=result.method_49858(index,filtered?result.method_49859(index,false):value,
                            filtered?value:result.method_49859(index,true));
                    }
                    at+=count-1;break;
                }
            }
        }
        return result;
    }

    private static String key(String text){return TranslationEngine.normalize(text);}
    private static List<String> strings(JsonArray array)throws IOException {
        List<String> result=new ArrayList<>();
        for(JsonElement value:array){String text=value.getAsString();if(key(text).isEmpty())throw new IOException("Empty sign context line");result.add(text);}
        return List.copyOf(result);
    }
    private static List<Rule> load() {
        try(InputStream stream=SignPhraseContext.class.getResourceAsStream("/sign-contexts.json")) {
            if(stream==null)return List.of();
            JsonArray rows;
            try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){rows=JsonParser.parseReader(reader).getAsJsonArray();}
            List<Rule> rules=new ArrayList<>();Set<List<String>> seen=new HashSet<>();
            for(JsonElement value:rows) {
                JsonObject row=value.getAsJsonObject();List<String> sources=strings(row.getAsJsonArray("source_lines"));
                List<String> targets=strings(row.getAsJsonArray("target_lines")),guards=strings(row.getAsJsonArray("guard_line_translations"));
                String fullSource=row.get("guard_exact_source").getAsString(),fullTarget=row.get("guard_expected").getAsString();
                if(sources.size()<2||sources.size()>4||targets.size()!=sources.size()||guards.size()!=sources.size()
                    ||!String.join(" ",sources).equals(fullSource)||!String.join("",targets).equals(fullTarget)
                    ||!seen.add(sources.stream().map(SignPhraseContext::key).toList()))throw new IOException("Invalid complete sign phrase");
                JsonObject exact=new JsonObject();for(int i=0;i<sources.size();i++)exact.addProperty(sources.get(i),targets.get(i));
                JsonObject dictionary=new JsonObject();dictionary.add("exact",exact);
                rules.add(new Rule(sources,targets,guards,fullSource,fullTarget,new TranslationEngine(dictionary)));
            }
            return List.copyOf(rules);
        } catch(IOException|RuntimeException invalid) {return List.of();}
    }
}
