package monumentazh.mixin;

import net.minecraft.class_342;
import net.minecraft.class_507;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=class_507.class,remap=false)
public interface RecipeBookSearchAccessor {
    @Accessor(value="field_3089",remap=false) class_342 monumentazh$search();
}
