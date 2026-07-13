package com.rfizzle.instinct.keepsake;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.item.KeepsakeEngraving;
import com.rfizzle.instinct.registry.InstinctDataComponents;
import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The keepsake engine ({@code design/SPEC.md} §7). When a tamed pet dies to a beyond-saving blow —
 * fire, lava, or the void — this drops a {@link com.rfizzle.instinct.item.KeepsakeCollarItem
 * keepsake collar} engraved with the pet's name and veterancy standing at the moment of loss.
 *
 * <p>It rides {@link ServerLivingEntityEvents#AFTER_DEATH} — the death has fully resolved by then,
 * unlike the {@code ALLOW_DEATH} hook the downed state cancels into — and re-checks tame, pet
 * membership, and the damage source on its own; it shares no state with {@code DownedHandler}. The
 * whole handler is fail-open: a broken keepsake computation logs and is swallowed, never altering
 * the death itself. Mounts and livestock leave nothing (a collar is a pet's alone); the {@code /kill}
 * command, though beyond saving, is excluded ({@link Keepsake}).
 *
 * <p>The collar item is fire-resistant, so a fire or lava loss drops it in place and it survives.
 * A void loss would drop it below the world, so the drop is lifted to the ground at the pet's own
 * column — the surface it walked off — resolved through the heightmap; a bottomless column leaves
 * no collar rather than feed it to the void.
 */
public final class KeepsakeHandler {

    private KeepsakeHandler() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof TamableAnimal pet)) {
                return;
            }
            try {
                dropKeepsake(pet, source);
            } catch (Exception e) {
                // Fail open: a broken keepsake drop must never disturb the death itself.
                Instinct.LOGGER.error("Keepsake drop failed for {}", pet.getType(), e);
            }
        });
    }

    /**
     * Drops the engraved collar for a pet lost beyond saving, or does nothing if this loss does not
     * qualify. Public (internal, not API) so gametests can drive a chosen damage source directly.
     */
    public static void dropKeepsake(TamableAnimal pet, DamageSource source) {
        if (!InstinctConfig.get().enableKeepsakeCollar
                || !pet.isTame()
                || !Keepsake.keepsakeWorthy(source)
                || !AnimalCoverage.membershipOf(pet).pet()
                || !(pet.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack collar = new ItemStack(InstinctItems.KEEPSAKE_COLLAR);
        collar.set(InstinctDataComponents.KEEPSAKE_ENGRAVING, engrave(pet));
        double dropY = Keepsake.isVoidLoss(source) ? safeSurfaceY(level, pet) : pet.getY(0.5);
        if (Double.isNaN(dropY)) {
            // A bottomless column with no ground to lay the collar on — leave nothing rather than
            // drop it into the void it was lost to.
            return;
        }
        ItemEntity drop = new ItemEntity(level, pet.getX(), dropY, pet.getZ(), collar);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    /** The engraving frozen onto the collar: the pet's name, rank, and days at the moment of loss. */
    private static KeepsakeEngraving engrave(TamableAnimal pet) {
        return new KeepsakeEngraving(
                pet.getName(),
                InstinctAPI.getVeterancyRank(pet),
                (int) InstinctAPI.getVeterancyDays(pet));
    }

    /**
     * The Y of the ground at the pet's own column — the surface it walked off before the fall — so a
     * void loss lays the collar on solid ground instead of below the world. Returns {@code NaN} when
     * the column is bottomless (no block above the world floor).
     */
    private static double safeSurfaceY(ServerLevel level, TamableAnimal pet) {
        int x = Mth.floor(pet.getX());
        int z = Mth.floor(pet.getZ());
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return surface > level.getMinBuildHeight() ? surface + 0.5 : Double.NaN;
    }
}
