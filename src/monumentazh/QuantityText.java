package monumentazh;
import java.util.*;
import java.util.regex.*;

/** Strict inventory-count grammar; ordinary ability stacks and player identities aren't word replacements. */
final class QuantityText {
    private static final String NUMBER="([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)";
    private static final Pattern TAIL=Pattern.compile("^\\("+NUMBER+" (total|in wallet)\\)$");
    private static final Pattern COUNTED=Pattern.compile("^"+NUMBER+" (.+?)(?: ([✓✗]))? \\("+NUMBER+" (total|in wallet)\\)$");
    private static final Pattern STACKS=Pattern.compile("^(?:([0-9]+) )?\\(([0-9]+) stacks?(?: \\+ ([0-9]+))?\\)(?: (.+))?$");
    static String translate(String source,Map<String,String> exact,Set<String> protectedValues) {
        Matcher m=TAIL.matcher(source);
        if(m.matches())return tail(m.group(1),m.group(2));
        m=COUNTED.matcher(source);
        if(m.matches()) {
            String name=known(m.group(2),exact,protectedValues);if(name==null)return null;
            return m.group(1)+" "+name+(m.group(3)==null?"":" "+m.group(3))+tail(m.group(4),m.group(5));
        }
        m=STACKS.matcher(source);
        if(m.matches()) {
            String name=m.group(4)==null?"":known(m.group(4),exact,protectedValues);if(name==null)return null;
            return (m.group(1)==null?"":m.group(1))+"（"+m.group(2)+"组"+(m.group(3)==null?"":"+"+m.group(3))+"）"+(name.isEmpty()?"":" "+name);
        }
        return null;
    }
    static String captureKey(String source) {
        Matcher m=TAIL.matcher(source);
        if(m.matches())return "({n} "+m.group(2)+")";
        m=COUNTED.matcher(source);
        if(m.matches())return "{n} "+m.group(2)+(m.group(3)==null?"":" "+m.group(3))+" ({n} "+m.group(5)+")";
        m=STACKS.matcher(source);
        if(m.matches())return (m.group(1)==null?"":"{n} ")+"({n} stacks"+(m.group(3)==null?"":" + {n}")+")"+(m.group(4)==null?"":" "+m.group(4));
        return null;
    }
    private static String tail(String number,String type){return "（"+(type.equals("total")?"总计：":"钱包中：")+number+"）";}
    private static String known(String source,Map<String,String> exact,Set<String> protectedValues) {
        String key=TranslationEngine.normalize(source);
        if(protectedValues.contains(key))return source;
        return exact.get(key);
    }
}
