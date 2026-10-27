package monumentazh.mixin;

import monumentazh.MonumentaZh;

import net.minecraft.class_2561;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = class_437.class, remap = false)
public abstract class ScreenTitleMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static class_2561 monumentazh$title(class_2561 title) {
        return MonumentaZh.translateTitle(title);
    }
}
