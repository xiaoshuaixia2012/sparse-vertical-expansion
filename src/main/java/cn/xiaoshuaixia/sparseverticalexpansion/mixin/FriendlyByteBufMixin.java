package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FriendlyByteBuf.class)
abstract class FriendlyByteBufMixin {
    @Inject(method = "readBlockPos(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private static void sve$readBlockPos(ByteBuf buffer, CallbackInfoReturnable<BlockPos> callback) {
        callback.setReturnValue(new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt()));
    }

    @Inject(method = "writeBlockPos(Lio/netty/buffer/ByteBuf;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true)
    private static void sve$writeBlockPos(ByteBuf buffer, BlockPos pos, CallbackInfo callback) {
        buffer.writeInt(pos.getX()).writeInt(pos.getY()).writeInt(pos.getZ());
        callback.cancel();
    }
}
