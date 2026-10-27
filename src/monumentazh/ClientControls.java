package monumentazh;

import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import monumentazh.mixin.CreativeSearchAccessor;
import monumentazh.mixin.RecipeBookSearchAccessor;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_364;
import net.minecraft.class_4069;
import net.minecraft.class_342;
import net.minecraft.class_437;
import net.minecraft.class_408;
import net.minecraft.class_465;
import net.minecraft.class_481;
import net.minecraft.class_518;

/** Optional local shortcuts. Minecraft owns rebinding and persistence in options.txt. */
public final class ClientControls {
    public enum Action { TOGGLE, EXPORT, ON, OFF }
    private static final Map<Action,class_304> KEYS = new EnumMap<>(Action.class);
    private static final Map<class_437,ScreenInput> CHAT_INPUTS = new WeakHashMap<>();
    private ClientControls() {}

    public static void register() {
        if (!KEYS.isEmpty()) return;
        for (Action action:Action.values()) {
            String id="key.monumentazh."+action.name().toLowerCase(java.util.Locale.ROOT);
            KEYS.put(action,KeyBindingHelper.registerKeyBinding(new class_304(id,-1,"key.categories.monumentazh")));
        }
        ScreenEvents.AFTER_INIT.register((client,screen,width,height) -> {
            if (!(screen instanceof class_465<?>) && !(screen instanceof class_408)) return;
            // Fabric resets these per-screen events on initialization and resize.
            ScreenInput input=new ScreenInput();
            if(screen instanceof class_408)CHAT_INPUTS.put(screen,input);
            ScreenKeyboardEvents.allowKeyPress(screen).register((current,key,scan,mods) ->
                input.key(current,key,scan,client.field_1724!=null,action -> run(client,action)));
            ScreenKeyboardEvents.afterKeyRelease(screen).register((current,key,scan,mods) -> input.releaseKey(key));
            ScreenMouseEvents.allowMouseClick(screen).register((current,x,y,button) ->
                input.mouse(current,button,client.field_1724!=null,action -> run(client,action)));
            ScreenMouseEvents.afterMouseRelease(screen).register((current,x,y,button) -> input.releaseMouse(button));
            ScreenEvents.remove(screen).register(current -> {input.clear();CHAT_INPUTS.remove(current,input);consume(false,action -> {});});
        });
    }

    public static void tick(class_310 client) {
        consume(client.field_1724!=null && client.field_1755==null,action -> run(client,action));
    }

    private static void run(class_310 client,Action action) {
        MonumentaZh.ActionResult result=MonumentaZh.executeAction(action,client);
        client.field_1724.method_7353(MonumentaZh.notice(result.message(),!result.success()),false);
    }

    static boolean allowsScreen(class_437 screen) {
        if(screen instanceof class_408)return true;
        if (!(screen instanceof class_465<?>)) return false;
        if (screen instanceof class_481 creative && typing(((CreativeSearchAccessor)creative).monumentazh$search())) return false;
        if (screen instanceof class_518 recipes) {
            var book=recipes.method_2659();
            if(book!=null && book.method_2605() && typing(((RecipeBookSearchAccessor)book).monumentazh$search())) return false;
        }
        return !hasTextInput(screen,Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /** GLFW emits text separately from key presses; a chat shortcut consumes both parts of that same input. */
    public static boolean suppressChatCharacter(class_437 screen) {
        ScreenInput input=CHAT_INPUTS.get(screen);
        return screen instanceof class_408 && input!=null && input.suppressCharacters;
    }

    private static boolean typing(class_342 field) {
        return field!=null && field.field_22764 && field.field_22763 && field.method_25370();
    }

    private static boolean hasTextInput(class_364 element,Set<class_364> visited) {
        if(element==null || !visited.add(element)) return false;
        if(element instanceof class_342 field && typing(field)) return true;
        if(element instanceof class_4069 parent) {
            if(hasTextInput(parent.method_25399(),visited))return true;
            for(var child:parent.method_25396())if(hasTextInput(child,visited))return true;
        }
        return false;
    }

    /** Direct screen events work where vanilla does not increment KeyBinding's press count. */
    static final class ScreenInput {
        private final Set<Integer> keys=new HashSet<>(),buttons=new HashSet<>();
        private boolean suppressCharacters;
        private int shortcutKey;
        boolean key(class_437 screen,int key,int scan,boolean hasPlayer,Consumer<Action> run) {
            suppressCharacters=false;
            boolean fresh=keys.add(key);
            boolean allowed=press(screen,hasPlayer,fresh,binding -> binding.method_1417(key,scan),run);
            if(!allowed && screen instanceof class_408){suppressCharacters=true;shortcutKey=key;}
            return allowed;
        }
        boolean mouse(class_437 screen,int button,boolean hasPlayer,Consumer<Action> run) {
            boolean fresh=buttons.add(button);
            return press(screen,hasPlayer,fresh,binding -> binding.method_1433(button),run);
        }
        private boolean press(class_437 screen,boolean hasPlayer,boolean fresh,java.util.function.Predicate<class_304> matches,Consumer<Action> run) {
            if(!hasPlayer || !allowsScreen(screen))return true;
            List<Action> actions=new ArrayList<>();
            for(var entry:KEYS.entrySet())if(!entry.getValue().method_1415() && matches.test(entry.getValue()))actions.add(entry.getKey());
            if(actions.isEmpty())return true;
            consume(false,action -> {});
            if(fresh)actions.forEach(run);
            // A shortcut consumes the vanilla action as well, including chat send/history keys.
            return false;
        }
        void releaseKey(int key){keys.remove(key);if(key==shortcutKey)suppressCharacters=false;}
        void releaseMouse(int button){buttons.remove(button);}
        void clear(){keys.clear();buttons.clear();suppressCharacters=false;}
    }

    static void consume(boolean inGame,Consumer<Action> run) {
        for (Action action:Action.values()) {
            class_304 key=KEYS.get(action);
            boolean pressed=false;
            while(key!=null && key.method_1436()) pressed=true;
            // Screen shortcuts are dispatched directly; drain queued gameplay presses in all menus.
            if(pressed && inGame)run.accept(action);
        }
    }
}
