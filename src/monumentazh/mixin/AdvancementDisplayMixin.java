package monumentazh.mixin;

import monumentazh.MonumentaZh;
import net.minecraft.class_185;
import net.minecraft.class_2561;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = class_185.class, remap = false)
public abstract class AdvancementDisplayMixin {
    @Inject(method = "method_811", at = @At("RETURN"), cancellable = true, remap = false)
    private void monumentazh$title(CallbackInfoReturnable<class_2561> callback) {
        callback.setReturnValue(MonumentaZh.display(callback.getReturnValue(), "advancement_title"));
    }
    @Inject(method = "method_817", at = @At("RETURN"), cancellable = true, remap = false)
    private void monumentazh$description(CallbackInfoReturnable<class_2561> callback) {
        callback.setReturnValue(MonumentaZh.display(callback.getReturnValue(), "advancement_description"));
    }
}
