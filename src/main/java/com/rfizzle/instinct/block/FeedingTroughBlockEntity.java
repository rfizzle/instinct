package com.rfizzle.instinct.block;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.InstinctItemTagProvider;
import com.rfizzle.instinct.registry.InstinctBlockEntities;
import com.rfizzle.instinct.trough.Trough;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.NonNullList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The feeding trough's block entity ({@code design/SPEC.md} §5): a single-slot {@link
 * WorldlyContainer} holding one stack of a single accepted {@code #instinct:trough_food} type
 * (hoppers insert from above, never extract; a comparator reads the fill), plus the server ticker
 * that runs the passive feeding loop. All feeding decisions defer to {@link Trough} for the pure
 * logic and its transient claim/cooldown registers.
 */
public class FeedingTroughBlockEntity extends BlockEntity implements WorldlyContainer {

    private static final int[] SLOTS_FROM_ABOVE = {0};
    private static final int[] NO_SLOTS = {};

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    /** The animal this trough has claimed to path in, or {@code null}. Transient, server-only. */
    private UUID claimedAnimal;
    private long lastRepathTick;

    public FeedingTroughBlockEntity(BlockPos pos, BlockState state) {
        super(InstinctBlockEntities.FEEDING_TROUGH, pos, state);
    }

    // ── Storage operations (shared by right-click insert/withdraw and the block) ─────────────────

    /** The single stored stack (never null; {@code EMPTY} when the trough is empty). */
    public ItemStack getStored() {
        return items.get(0);
    }

    /** Whether {@code stack} may enter this trough right now (accepted food, matching type, room). */
    public boolean canAccept(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(InstinctItemTagProvider.TROUGH_FOOD)) {
            return false;
        }
        ItemStack stored = items.get(0);
        boolean sameType = !stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, stack);
        int room = Trough.CAPACITY - stored.getCount();
        return Trough.canInsert(true, stored.isEmpty(), sameType, room);
    }

    /** Moves as much of {@code held} into the trough as fits; returns the count moved (0 if refused). */
    public int insertFood(ItemStack held) {
        if (!canAccept(held)) {
            return 0;
        }
        ItemStack stored = items.get(0);
        int room = Trough.CAPACITY - stored.getCount();
        int moved = Math.min(held.getCount(), room);
        if (moved <= 0) {
            return 0;
        }
        if (stored.isEmpty()) {
            items.set(0, held.copyWithCount(moved));
        } else {
            stored.grow(moved);
        }
        contentsChanged();
        return moved;
    }

    /** Whether a hay bale would convert here (the trough is empty or holds wheat with ≥9 room). */
    public boolean canAcceptHay() {
        ItemStack stored = items.get(0);
        boolean storedWheatOrEmpty = stored.isEmpty() || stored.is(Items.WHEAT);
        return Trough.hayWheatYield(storedWheatOrEmpty, Trough.CAPACITY - stored.getCount()) > 0;
    }

    /** Converts one hay bale to {@link Trough#HAY_WHEAT_YIELD} wheat if the trough allows it. */
    public int insertHay() {
        ItemStack stored = items.get(0);
        boolean storedWheatOrEmpty = stored.isEmpty() || stored.is(Items.WHEAT);
        int room = Trough.CAPACITY - stored.getCount();
        int yield = Trough.hayWheatYield(storedWheatOrEmpty, room);
        if (yield <= 0) {
            return 0;
        }
        if (stored.isEmpty()) {
            items.set(0, new ItemStack(Items.WHEAT, yield));
        } else {
            stored.grow(yield);
        }
        contentsChanged();
        return yield;
    }

    /** Empties the trough, returning the whole stored stack. */
    public ItemStack withdrawAll() {
        ItemStack out = items.get(0);
        items.set(0, ItemStack.EMPTY);
        contentsChanged();
        return out;
    }

    private void contentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            // Sync the stored stack to tracking clients (SPEC: the client receives the trough fill),
            // so client-side interaction prediction sees the real contents.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ── Feeding loop ─────────────────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FeedingTroughBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.isEmpty() || !InstinctConfig.get().enableTrough) {
            be.releaseClaim();
            return;
        }
        long now = serverLevel.getGameTime();
        if (be.claimedAnimal != null) {
            be.progressClaim(serverLevel, pos, now);
            return; // one animal at a time
        }
        // A per-position phase across the full interval so a field of troughs don't all scan together.
        int interval = Math.max(1, InstinctConfig.get().troughFeedIntervalTicks);
        if ((now + Math.floorMod(pos.hashCode(), interval)) % interval == 0) {
            be.scanAndClaim(serverLevel, pos, now);
        }
    }

    /** Drives the claimed animal in, feeding it on arrival or releasing it on timeout/loss. */
    private void progressClaim(ServerLevel level, BlockPos pos, long now) {
        BlockPos claimPos = Trough.claimedTrough(claimedAnimal, now);
        if (claimPos == null || !claimPos.equals(pos)) {
            releaseClaim(); // expired or reassigned
            return;
        }
        Entity entity = level.getEntity(claimedAnimal);
        if (!(entity instanceof Animal animal) || !animal.isAlive()) {
            releaseClaim();
            return;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        if (Trough.withinRadiusSq(animal.distanceToSqr(cx, cy, cz), Trough.ARRIVAL_DISTANCE)) {
            feed(level, animal, now);
            releaseClaim();
            return;
        }
        if (now - lastRepathTick >= Trough.REPATH_INTERVAL_TICKS) {
            animal.getNavigation().moveTo(cx, cy, cz, Trough.MOVE_SPEED);
            lastRepathTick = now;
        }
    }

    /** Scans the radius for one eligible animal (adult preferred, else baby) and claims it. */
    private void scanAndClaim(ServerLevel level, BlockPos pos, long now) {
        InstinctConfig config = InstinctConfig.get();
        ItemStack stored = items.get(0);
        double radius = config.troughRadiusBlocks;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        List<Animal> inRange = new ArrayList<>();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(pos).inflate(radius),
                a -> a.isAlive() && AnimalCoverage.membershipOf(a).livestock())) {
            if (Trough.withinRadiusSq(animal.distanceToSqr(cx, cy, cz), radius)) {
                inRange.add(animal);
            }
        }
        boolean capAllows = Trough.capAllows(inRange.size(), config.troughPopulationCap);

        Animal baby = null;
        for (Animal animal : inRange) {
            if (Trough.isClaimed(animal.getUUID(), now) || !animal.isFood(stored)) {
                continue;
            }
            if (!animal.isBaby()) {
                if (capAllows && animal.getAge() == 0 && animal.canFallInLove()) {
                    claim(animal, pos, now); // an eligible adult wins outright
                    return;
                }
            } else if (baby == null && Trough.canBabyEat(animal.getUUID(), now)) {
                baby = animal;
            }
        }
        if (baby != null) {
            claim(baby, pos, now);
        }
    }

    private void claim(Animal animal, BlockPos pos, long now) {
        Trough.tryClaim(animal.getUUID(), pos, now);
        claimedAnimal = animal.getUUID();
        lastRepathTick = now;
        animal.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Trough.MOVE_SPEED);
    }

    /** Feeds an arrived animal, re-validating eligibility against the current stored item. */
    private void feed(ServerLevel level, Animal animal, long now) {
        ItemStack stored = items.get(0);
        if (stored.isEmpty() || !animal.isFood(stored)) {
            return;
        }
        boolean baby = animal.isBaby();
        if (baby ? !Trough.canBabyEat(animal.getUUID(), now)
                : !(animal.getAge() == 0 && animal.canFallInLove())) {
            return; // no longer eligible on arrival — consume nothing
        }
        ItemStack eaten = stored.copyWithCount(1);
        ContainerHelper.removeItem(items, 0, 1);
        contentsChanged();

        level.playSound(null, animal.getX(), animal.getY(), animal.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F,
                1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.4F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, eaten),
                animal.getX(), animal.getY() + animal.getBbHeight() * 0.5, animal.getZ(),
                8, animal.getBbWidth() * 0.4, animal.getBbHeight() * 0.3, animal.getBbWidth() * 0.4, 0.05);

        if (baby) {
            animal.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
            Trough.markBabyFed(animal.getUUID(), now);
        } else {
            animal.setInLove(null); // unattributed — no "fed by" player, exactly like a dispenser can't
            markTroughFed(animal, now);
        }
    }

    /** Writes {@code lastTroughFeedTime}, preserving the animal's other genetics fields (§3/§5). */
    private static void markTroughFed(Animal animal, long now) {
        GeneticsData existing = animal.getAttachedOrCreate(InstinctAttachments.GENETICS);
        animal.setAttached(InstinctAttachments.GENETICS, new GeneticsData(
                existing.grade(), existing.perk(), existing.primeNextOffspring(), now));
    }

    private void releaseClaim() {
        if (claimedAnimal != null) {
            Trough.release(claimedAnimal);
            claimedAnimal = null;
        }
    }

    // ── WorldlyContainer: hoppers insert from above, never extract ───────────────────────────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? SLOTS_FROM_ABOVE : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.UP && canAccept(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    // ── Container ────────────────────────────────────────────────────────────────────────────────

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            contentsChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        contentsChanged();
    }

    @Override
    public int getMaxStackSize() {
        return Trough.CAPACITY;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return canAccept(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
    }

    // ── Persistence ──────────────────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.set(0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
