package com.copycatsplus.copycats.mixin.foundation.copycat;

import com.copycatsplus.copycats.foundation.copycat.CopycatLightHelper;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Let copycats with a light-transmitting material (e.g. glass) transmit light and skip ambient occlusion
 * like the material does, instead of behaving like an opaque block.
 */
@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")
public class BlockStateBaseLightMixin {

    @ModifyReturnValue(method = "getLightBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
    private int copycats$materialLightBlock(int original, BlockGetter level, BlockPos pos) {
        if (original == 0 || !(((BlockBehaviour.BlockStateBase) (Object) this).getBlock() instanceof ICopycatBlock))
            return original;
        return CopycatLightHelper.getLightBlock(level, pos, original);
    }

    @ModifyReturnValue(method = "propagatesSkylightDown(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
    private boolean copycats$materialPropagatesSkylightDown(boolean original, BlockGetter level, BlockPos pos) {
        if (original || !(((BlockBehaviour.BlockStateBase) (Object) this).getBlock() instanceof ICopycatBlock))
            return original;
        return CopycatLightHelper.propagatesSkylightDown(level, pos, false);
    }

    @ModifyReturnValue(method = "getShadeBrightness(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F", at = @At("RETURN"))
    private float copycats$materialShadeBrightness(float original, BlockGetter level, BlockPos pos) {
        if (original >= 1.0f || !(((BlockBehaviour.BlockStateBase) (Object) this).getBlock() instanceof ICopycatBlock))
            return original;
        return CopycatLightHelper.getShadeBrightness(level, pos, original);
    }

    @ModifyReturnValue(method = "isSolidRender(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
    private boolean copycats$materialSolidRender(boolean original, BlockGetter level, BlockPos pos) {
        if (!original || !(((BlockBehaviour.BlockStateBase) (Object) this).getBlock() instanceof ICopycatBlock))
            return original;
        return CopycatLightHelper.isSolidRender(level, pos, true);
    }
}
