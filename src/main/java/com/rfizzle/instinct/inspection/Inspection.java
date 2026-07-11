package com.rfizzle.instinct.inspection;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.veterancy.Veterancy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crouch-look inspection ({@code design/SPEC.md} §2): a crouching owner whose crosshair rests on
 * their own pet within 8 blocks gets the ✦ days/rank action-bar line, once per crosshair
 * acquisition — re-emitted only after the crosshair leaves the pet or crouch is released.
 * Server-computed, action bar only, nothing persistent on screen. The per-player acquisition edge
 * is transient by design (a restart re-emitting one line is harmless). §3's livestock line will
 * share this same pass when genetics lands.
 */
public final class Inspection {

    /** How far the crosshair reaches, per SPEC §2 (same 8 blocks as {@code /instinct info}). */
    public static final double INSPECT_RANGE_BLOCKS = 8.0;

    /**
     * The raycast cadence (the {@code mc-tick-work} modulo gate for AABB entity queries): a
     * 4-tick acquisition lag is imperceptible on an action-bar line, and the per-player edge in
     * {@link #LAST_INSPECTED} tolerates coarse sampling.
     */
    static final int INSPECT_INTERVAL_TICKS = 4;

    /** player UUID → entity id currently under the crouching crosshair (the acquisition edge). */
    private static final Map<UUID, Integer> LAST_INSPECTED = new HashMap<>();

    private static int tickCounter;

    private Inspection() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Inspection::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_INSPECTED.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_INSPECTED.clear();
            tickCounter = 0;
        });
    }

    private static void onServerTick(MinecraftServer server) {
        if (++tickCounter % INSPECT_INTERVAL_TICKS != 0 || !InstinctConfig.get().enableInspection) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(player);
            } catch (Exception e) {
                Instinct.LOGGER.error("Inspection failed for {}", player.getGameProfile().getName(), e);
            }
        }
    }

    /**
     * One player's inspection tick; returns whether a line was emitted. Public (internal, not
     * API) so gametests can drive the acquisition edge deterministically.
     */
    public static boolean tickPlayer(ServerPlayer player) {
        if (!player.isShiftKeyDown()) {
            LAST_INSPECTED.remove(player.getUUID());
            return false;
        }
        Animal animal = animalOnCrosshair(player, INSPECT_RANGE_BLOCKS);
        if (animal == null) {
            LAST_INSPECTED.remove(player.getUUID());
            return false;
        }
        Integer previous = LAST_INSPECTED.put(player.getUUID(), animal.getId());
        if (previous != null && previous == animal.getId()) {
            return false; // same acquisition — already answered
        }
        return emitPetLine(player, animal);
    }

    /** The §2 pet line: days at rank 0, days-and-rank at rank 1+; the pet's owner only. */
    private static boolean emitPetLine(ServerPlayer player, Animal animal) {
        if (!InstinctConfig.get().enableVeterancy
                || !(animal instanceof TamableAnimal pet)
                || !pet.isTame() || !pet.isOwnedBy(player)
                || !AnimalCoverage.membershipOf(pet).pet()) {
            return false;
        }
        long days = (long) InstinctAPI.getVeterancyDays(pet);
        int rank = InstinctAPI.getVeterancyRank(pet);
        Component line = rank > 0
                ? Component.translatable("notification.instinct.inspect_pet.ranked",
                        pet.getName(), days, Component.translatable(Veterancy.rankKey(rank)))
                : Component.translatable("notification.instinct.inspect_pet", pet.getName(), days);
        player.displayClientMessage(line, true);
        return true;
    }

    /**
     * Raycast from the player's eye along their view vector and return the first living
     * {@link Animal} within range, or {@code null}. Entity bounding boxes only — partial wall
     * cover is intentional; the shared crosshair for inspection and {@code /instinct info}.
     */
    public static Animal animalOnCrosshair(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 view = player.getViewVector(1.0f);
        Vec3 end = eye.add(view.x * range, view.y * range, view.z * range);
        AABB box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> e instanceof Animal && !e.isSpectator() && e.isAlive(),
                range * range);
        if (hit == null) {
            return null;
        }
        Entity entity = hit.getEntity();
        return entity instanceof Animal animal ? animal : null;
    }
}
