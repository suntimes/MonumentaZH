package monumentazh.mixin;

import net.minecraft.class_342;
import net.minecraft.class_481;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=class_481.class,remap=false)
public interface CreativeSearchAccessor {
    @Accessor(value="field_2894",remap=false) class_342 monumentazh$search();
}
