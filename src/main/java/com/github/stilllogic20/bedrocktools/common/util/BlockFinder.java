package com.github.stilllogic20.bedrocktools.common.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

@ParametersAreNonnullByDefault
public class BlockFinder extends RecursiveTask<Set<BlockPos>> {

    private static final long serialVersionUID = -4486076892702618694L;
    private static final EnumFacing[] FACINGS = EnumFacing.values();

    private final Block target;
    private final World world;
    private final int max;
    private final BlockPos position;
    private final Set<BlockPos> found;

    private BlockFinder(Block target, int max, World world, BlockPos position, Set<BlockPos> found) {
        this.target = target;
        this.max = max;
        this.world = world;
        this.position = position;
        this.found = found;
    }

    private static boolean equals(@Nullable Block found, @Nullable Block finding) {
        boolean equal = Objects.equals(found, finding);
        if (Objects.equals(finding, Blocks.REDSTONE_ORE) || Objects.equals(finding, Blocks.LIT_REDSTONE_ORE))
            equal |= Objects.equals(found, Blocks.REDSTONE_ORE) || Objects.equals(found, Blocks.LIT_REDSTONE_ORE);
        return equal;
    }

    public static boolean isOre(@Nullable Block block) {
        if (block == null)
            return false;
        if (block == Blocks.LIT_REDSTONE_ORE)
            return true;
        ItemStack stack = new ItemStack(block);
        if (stack.isEmpty())
            return false;
        return Arrays.stream(OreDictionary.getOreIDs(stack)).mapToObj(OreDictionary::getOreName)
            .anyMatch(name -> name.startsWith("ore") || name.equals("logWood") || name.equals("treeLeaves"));
    }

    @Nonnull
    public static BlockFinder of(Block target, int max, World world, BlockPos origin) {
        return new BlockFinder(
            Objects.requireNonNull(target),
            max,
            Objects.requireNonNull(world),
            Objects.requireNonNull(origin),
            Collections.newSetFromMap(new ConcurrentHashMap<>(max)));
    }

    @Nonnull
    public CompletableFuture<Set<BlockPos>> find() {
        return CompletableFuture.supplyAsync(this::invoke);
    }

    @Override
    @Nonnull
    protected Set<BlockPos> compute() {
        if (found.size() >= max || found.contains(position))
            return found;

        IBlockState state = world.getBlockState(position);
        Block block = state.getBlock();
        if (equals(block, target)) {
            found.add(position);
            final ForkJoinTask<?>[] tasks = Arrays.stream(FACINGS).map(facing ->
                new BlockFinder(target, max, world, position.offset(facing), found)
            ).toArray(ForkJoinTask[]::new);
            ForkJoinTask.invokeAll(tasks);
        }

        return found;
    }

}
