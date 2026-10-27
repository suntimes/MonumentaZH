package monumentazh.mixin;

import monumentazh.MonumentaZh;
import net.minecraft.class_1259;
import net.minecraft.class_2561;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = class_1259.class, remap = false)
public abstract class BossBarNameMixin {
    @Inject(method = "method_5414", at = @At("RETURN"), cancellable = true, remap = false)
    private void monumentazh$display(CallbackInfoReturnable<class_2561> callback) {
        callback.setReturnValue(MonumentaZh.translateHud(callback.getReturnValue()));
    }
}
