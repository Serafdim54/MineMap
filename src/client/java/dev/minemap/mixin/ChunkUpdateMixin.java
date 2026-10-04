package dev.minemap.mixin;

import dev.minemap.MineMapClient;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class ChunkUpdateMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"), require = 0)
    private void minemap$changed(CallbackInfoReturnable<BlockState> result) {
        if (result.getReturnValue() != null) {
            var pos = ((LevelChunk)(Object)this).getPos();
            MineMapClient.chunkChanged(pos.x, pos.z);
        }
    }
}
