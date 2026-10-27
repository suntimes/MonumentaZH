package monumentazh.mixin;
import net.minecraft.class_382;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=class_382.class,remap=false)
public interface BakedGlyphBoundsAccessor {
    @Accessor(value="field_2279",remap=false) float monumentazh$minY();
    @Accessor(value="field_2278",remap=false) float monumentazh$maxY();
}
