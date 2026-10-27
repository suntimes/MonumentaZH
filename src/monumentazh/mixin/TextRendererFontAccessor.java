package monumentazh.mixin;
import net.minecraft.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=class_327.class,remap=false)
public interface TextRendererFontAccessor {
    @Invoker(value="method_27526",remap=false) class_377 monumentazh$font(class_2960 id);
}
