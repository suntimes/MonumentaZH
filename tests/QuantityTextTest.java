package monumentazh;
import com.google.gson.*;
import java.util.*;
public final class QuantityTextTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        JsonObject words=new JsonObject(),dictionary=new JsonObject();words.addProperty("Experience Bottle","经验瓶");dictionary.add("exact",words);
        TranslationEngine engine=new TranslationEngine(dictionary);
        for(int i=1;i<=1000;i++){
            check(engine.translate("("+i+" in wallet)",false).equals("（钱包中："+i+"）"),"Wallet count");
            check(engine.translate("16 Experience Bottle ✓ ("+i+" in wallet)",false).equals("16 经验瓶 ✓（钱包中："+i+"）"),"Counted item name");
            check(engine.translate("("+i+" total)",false).equals("（总计："+i+"）"),"Total count");
        }
        check(engine.translate("102 (1 stack + 38)",false).equals("102（1组+38）"),"Stack remainder");
        check(engine.translate("128 (2 stacks)",false).equals("128（2组）"),"Full stacks");
        for(String raw:List.of("Max Stacks: 8","Maximum stacks of Sharpshooter: 7","16 UnknownObject ✓ (999 in wallet)","UnknownPlayer ✓ (999 in wallet)","(999 in wallet) bonus"))check(engine.translate(raw,false).equals(raw),"Unexpected broad translation: "+raw);
        JsonObject override=new JsonObject(),exact=new JsonObject();exact.addProperty("(7 total)","用户校对的固定译文");override.add("exact",exact);engine.add(override);
        check(engine.translate("(7 total)",false).equals("用户校对的固定译文"),"Exact override priority");
        check(QuantityText.captureKey("(1 in wallet)").equals(QuantityText.captureKey("(999 in wallet)")),"Capture count key");
        check(QuantityText.captureKey("Max Stacks: 8")==null,"Ability capture key");
        check(!QuantityText.captureKey("1 Experience Bottle ✓ (8 in wallet)").equals(QuantityText.captureKey("1 Experience Bottle ✗ (8 in wallet)")),"Preserve status");
        System.out.println("QUANTITY_TEXT_OK: 3000 count variants, fixed overrides, unknown identities, ability stacks and capture status");
    }
}
