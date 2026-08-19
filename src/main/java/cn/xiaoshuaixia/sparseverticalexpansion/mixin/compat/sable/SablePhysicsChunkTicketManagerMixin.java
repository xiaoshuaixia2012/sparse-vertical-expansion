package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sable;

import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicket;
import java.util.Map;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The "perfect" Sable physics integration for SVE sparse sections.
 *
 * <p>Vanilla Sable builds the expensive Rapier voxel data (a full 16x16x16 section scan, ~28k block
 * lookups) lazily: only when a ship's bounding box moves near a section, via
 * {@code PhysicsChunkTicketManager#update}. Block placement/removal in a vanilla chunk only runs the
 * cheap 7-block {@code handleBlockChange}. The earlier compat attempt instead ran the full-section
 * registration synchronously on the block-placement path, which is what made placement lag.</p>
 *
 * <p>This mixin makes sparse sections behave exactly like vanilla sections inside that lazy pipeline:</p>
 * <ul>
 *   <li>{@link #sve$sectionIndex0}/{@link #sve$sectionIndex1} redirect the two
 *       {@code ServerLevel#getSectionIndexFromSectionY} calls in {@code update()}. For an extended-Y
 *       section they return a valid in-range sentinel ({@code 0}) so the vanilla
 *       {@code index >= 0 && index < getSectionsCount()} guard lets the section through to
 *       {@code addTicket}; vanilla Y passes through unchanged.</li>
 *   <li>{@link #sve$addSparseTicket} intercepts {@code addTicket} at HEAD for extended-Y sections and,
 *       instead of {@code chunk.getSection(index)} (which cannot reach SVE's sparse storage), reads the
 *       sparse section from the SVE chunk attachment, registers it via
 *       {@code handleChunkSectionAddition}, and tracks it in the manager's own {@code physicsChunks}
 *       map with the current game time. Empty/missing sparse sections register an empty section and are
 *       tracked too (exactly as vanilla tracks empty sections), so the chunk is not re-probed every tick
 *       and a later block change flows through the cheap {@code handleBlockChange}. Expiry still runs
 *       through the existing {@code expirePhysicsChunkTickets} path, exactly as for vanilla sections.</li>
 * </ul>
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager", remap = false)
public abstract class SablePhysicsChunkTicketManagerMixin {

    @Shadow
    private Map<SectionPos, PhysicsChunkTicket> physicsChunks;

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getSectionIndexFromSectionY(I)I",
                    ordinal = 0))
    private int sve$sectionIndex0(ServerLevel level, int y) {
        return sve$sectionIndex(level, y);
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getSectionIndexFromSectionY(I)I",
                    ordinal = 1))
    private int sve$sectionIndex1(ServerLevel level, int y) {
        return sve$sectionIndex(level, y);
    }

    @Unique
    private int sve$sectionIndex(ServerLevel level, int sectionY) {
        if (isExtendedSectionY(sectionY)) {
            // In-range sentinel so the vanilla guard lets addTicket run; sve$addSparseTicket takes over.
            return 0;
        }
        return level.getSectionIndexFromSectionY(sectionY);
    }

    @Inject(method = "addTicket", at = @At("HEAD"), cancellable = true)
    private void sve$addSparseTicket(
            Level level, PhysicsPipeline pipeline, SectionPos sectionPos,
            int x, int y, int z, int index, long gameTime,
            CallbackInfoReturnable<PhysicsChunkTicket> cir) {
        if (!isExtendedSectionY(y)) {
            return;
        }

        PhysicsChunkTicket existing = this.physicsChunks.get(sectionPos);
        if (existing == null) {
            LevelChunk chunk = level.getChunk(x, z);
            SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
            LevelChunkSection section = storage == null ? null : storage.getSection(y);
            if (section == null || section.hasOnlyAir()) {
                // Register an empty section and still track it, exactly as vanilla addTicket does for empty
                // vanilla sections: this avoids re-probing the chunk every tick and lets a later block change
                // update the voxel through the cheap handleBlockChange path instead of a full re-scan.
                section = sve$emptySection(level);
            }
            pipeline.handleChunkSectionAddition(section, x, y, z, false);
            existing = new PhysicsChunkTicket(sectionPos, gameTime, null);
            this.physicsChunks.put(sectionPos, existing);
        }

        existing.setLastInhabitedTick(gameTime);
        cir.setReturnValue(existing);
    }

    @Unique
    private static LevelChunkSection sve$emptySection;

    @Unique
    private static LevelChunkSection sve$emptySection(Level level) {
        LevelChunkSection section = sve$emptySection;
        if (section == null) {
            section = new LevelChunkSection(level.registryAccess().registryOrThrow(Registries.BIOME));
            sve$emptySection = section;
        }
        return section;
    }

    @Unique
    private static boolean isExtendedSectionY(int sectionY) {
        return sectionY < SectionPos.blockToSectionCoord(ExtendedYRange.VANILLA_MIN_Y)
                || sectionY > SectionPos.blockToSectionCoord(ExtendedYRange.VANILLA_MAX_Y);
    }
}
