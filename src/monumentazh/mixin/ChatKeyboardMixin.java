package monumentazh.mixin;

import monumentazh.ClientControls;
import net.minecraft.class_309;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Consume only the text callback paired with a configured chat shortcut; ordinary chat stays vanilla. */
@Mixin(value=class_309.class,remap=false)
public abstract class ChatKeyboardMixin {
    @Shadow @Final private class_310 field_1678;

    @Inject(method="method_1457(JII)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void monumentazh$shortcutCharacter(long window,int codePoint,int modifiers,CallbackInfo ci) {
        if(ClientControls.suppressChatCharacter(field_1678.field_1755))ci.cancel();
    }
}
