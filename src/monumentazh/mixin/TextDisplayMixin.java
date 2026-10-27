package monumentazh.mixin;

import monumentazh.MonumentaZh;
import net.minecraft.class_2561;
import net.minecraft.class_8113;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Translate before wrapping into lines; invalidate only the client line cache when toggled. */
@Mixin(value = class_8113.class_8123.class, remap = false)
public abstract class TextDisplayMixin {
    @Shadow private class_8113.class_8123.class_8125 field_42442;
    @Unique private long monumentazh$revision = -1;

    @Inject(method = "method_48905", at = @At("HEAD"), remap = false)
    private void monumentazh$refresh(class_8113.class_8123.class_8127 splitter,
        CallbackInfoReturnable<class_8113.class_8123.class_8125> callback) {
        long current = MonumentaZh.displayRevision();
        if (current != monumentazh$revision) { field_42442 = null; monumentazh$revision = current; }
    }

    @ModifyArg(method = "method_48905", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_8113$class_8123$class_8127;split(Lnet/minecraft/class_2561;I)Lnet/minecraft/class_8113$class_8123$class_8125;"), index = 0, remap = false)
    private class_2561 monumentazh$text(class_2561 text) { return MonumentaZh.display(text, "text_display"); }
}
