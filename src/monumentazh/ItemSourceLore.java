package monumentazh;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.class_2561;

/** Local overrides for item lore. Do not change the same names in books or NPC labels. */
final class ItemSourceLore {
    private ItemSourceLore() {}

    static class_2561 localize(class_2561 source, class_2561 shown) {
        String raw = source.getString();
        String known = sourceName(raw);
        if (known != null)
            return known.equals(shown.getString()) ? shown : MonumentaZh.translatedTooltipLine(source, known);
        return shown;
    }

    static class_2561 visibleDenomination(class_2561 source) {
        // This currency has a deliberately obfuscated region followed by a visible, styled denomination.
        if (!source.getString().endsWith(" : Currency")) return source;
        JsonElement json = class_2561.class_2562.method_10868(source);
        JsonElement updated = translateLastCurrency(json, false);
        if (updated == null) return source;
        class_2561 result = class_2561.class_2562.method_10872(updated);
        return result == null ? source : result;
    }

    private static String sourceName(String raw) {
        String text = TranslationEngine.normalize(raw).replace('（','(').replace('）',')').replace(" (","(");
        return switch (text) {
            case "Soulwoven", "灵魂编织", "灵魂编织(Soulwoven)" -> "灵魂编织";
            case "Halloween Event", "万圣节活动", "万圣节活动(Halloween Event)" -> "万圣节活动";
            default -> null;
        };
    }

    /** Follow the last visible text run; preserve the prefix tree, formatting and all events verbatim. */
    private static JsonElement translateLastCurrency(JsonElement node, boolean hidden) {
        if (node.isJsonArray()) {
            JsonArray children = node.getAsJsonArray();
            if (children.size() == 0) return null;
            int last = children.size() - 1;
            JsonElement updated = translateLastCurrency(children.get(last), hidden);
            if (updated == null) return null;
            children.set(last, updated);
            return children;
        }
        JsonObject object;
        if (node.isJsonObject()) {
            object = node.getAsJsonObject();
            if (object.has("obfuscated")) hidden = object.get("obfuscated").getAsBoolean();
            if (object.has("extra") && !object.getAsJsonArray("extra").isEmpty())
                return translateLastCurrency(object.get("extra"), hidden) == null ? null : object;
        } else {
            object = new JsonObject();
            object.add("text", node);
        }
        if (hidden || !object.has("text") || !object.get("text").isJsonPrimitive()
                || !"Currency".equals(object.get("text").getAsString())) return null;
        TranslationEngine.replaceTranslatedText(object, "货币");
        return object;
    }
}
