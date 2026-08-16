package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void sve$disableVoidDamage(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel level
                && SveWorldData.get(level).disableVoidDamage().blocks(entity instanceof Player)) {
            callback.cancel();
        }
    }
}
