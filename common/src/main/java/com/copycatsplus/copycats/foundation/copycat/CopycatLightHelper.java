package com.copycatsplus.copycats.foundation.copycat;

import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Lets copycats with a light-transmitting material (glass, ice, ...) transmit light like that material does.
 * <p>
 * Vanilla caches light properties per block state, so a full copycat block always counts as an opaque block,
 * which makes copycats with glass cast shadows and darken their surroundings. These helpers refine the
 * state-based values with the material stored in the block entity. They only ever let more light through
 * than the state-based value, so copycats with opaque materials behave exactly as before.
 */
public final class CopycatLightHelper {

    private CopycatLightHelper() {
    }

    public static int getLightBlock(BlockGetter level, BlockPos pos, int original) {
        if (original == 0) return original;
        Collection<BlockState> materials = getMaterials(level, pos);
        if (materials.isEmpty()) return original;
        int lightBlock = 0;
        for (BlockState material : materials) {
            if (isUnusable(material)) return original;
            lightBlock = Math.max(lightBlock, material.getLightBlock(level, pos));
        }
        return Math.min(original, lightBlock);
    }

    public static boolean propagatesSkylightDown(BlockGetter level, BlockPos pos, boolean original) {
        if (original) return true;
        Collection<BlockState> materials = getMaterials(level, pos);
        if (materials.isEmpty()) return false;
        for (BlockState material : materials) {
            if (isUnusable(material) || !material.propagatesSkylightDown(level, pos)) return false;
        }
        return true;
    }

    public static float getShadeBrightness(BlockGetter level, BlockPos pos, float original) {
        if (original >= 1.0f) return original;
        Collection<BlockState> materials = getMaterials(level, pos);
        if (materials.isEmpty()) return original;
        float brightness = 1.0f;
        for (BlockState material : materials) {
            if (isUnusable(material)) return original;
            brightness = Math.min(brightness, material.getShadeBrightness(level, pos));
        }
        return Math.max(original, brightness);
    }

    public static boolean isSolidRender(BlockGetter level, BlockPos pos, boolean original) {
        if (!original) return false;
        Collection<BlockState> materials = getMaterials(level, pos);
        if (materials.isEmpty()) return true;
        for (BlockState material : materials) {
            if (isUnusable(material) || material.isSolidRender(level, pos)) return true;
        }
        return false;
    }

    /**
     * Recompute lighting at the copycat's position after its material changed.
     * Runs on the server and in the client world: the client computes light for placed blocks itself, before it
     * receives the copycat's material, so it has to be corrected there too.
     */
    public static void refreshLight(Level level, BlockPos pos) {
        if (!isRealLevel(level)) return;
        // neither call loads or waits for a chunk, so this is safe while the chunk itself is loading
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        LightChunk lightChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (lightChunk == null)
            lightChunk = level.getChunkSource().getChunkForLighting(chunkX, chunkZ);
        if (lightChunk instanceof ChunkAccess chunk) {
            ChunkSkyLightSources skyLightSources = chunk.getSkyLightSources();
            if (skyLightSources != null)
                skyLightSources.update(chunk, SectionPos.sectionRelative(pos.getX()), pos.getY(), SectionPos.sectionRelative(pos.getZ()));
        }
        level.getLightEngine().checkBlock(pos);
    }

    /**
     * Recompute lighting when a copycat with a light-transmitting material is loaded. Vanilla computes sky light
     * sources before block entities exist, so without this such copycats would count as opaque after a reload.
     */
    public static void refreshLightIfTransmitting(Level level, BlockPos pos) {
        if (!isRealLevel(level)) return;
        if (getLightBlock(level, pos, level.getMaxLightLevel()) < level.getMaxLightLevel() || propagatesSkylightDown(level, pos, false))
            refreshLight(level, pos);
    }

    private static boolean isRealLevel(Level level) {
        if (level instanceof ServerLevel) return true;
        return level.isClientSide() && CopycatLightClientHelper.isClientWorld(level);
    }

    private static boolean isUnusable(@Nullable BlockState material) {
        return material == null || material.isAir() || AllBlocks.COPYCAT_BASE.has(material) || material.getBlock() instanceof ICopycatBlock;
    }

    private static Collection<BlockState> getMaterials(BlockGetter level, BlockPos pos) {
        BlockEntity be = peekBlockEntity(level, pos);
        if (be instanceof IMultiStateCopycatBlockEntity multiStateBE)
            return multiStateBE.getMaterialItemStorage().getAllMaterials();
        if (be instanceof ICopycatBlockEntity copycatBE) {
            BlockState material = copycatBE.getMaterial();
            return material == null ? List.of() : List.of(material);
        }
        return List.of();
    }

    /**
     * Look up an existing block entity without creating or promoting one. Light calculations run off the main
     * thread, where {@link Level#getBlockEntity} is not safe to call.
     */
    @Nullable
    private static BlockEntity peekBlockEntity(BlockGetter level, BlockPos pos) {
        if (level instanceof Level realLevel) {
            if (realLevel.getChunkSource() == null) return null;
            LightChunk chunk = realLevel.getChunkSource().getChunkForLighting(
                    SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
            return chunk instanceof BlockGetter chunkGetter ? peekBlockEntity(chunkGetter, pos) : null;
        }
        if (level instanceof ChunkAccess chunk) {
            // some callers (e.g. ChunkSkyLightSources) pass chunk-relative coordinates
            ChunkPos chunkPos = chunk.getPos();
            BlockPos worldPos = new BlockPos(chunkPos.getBlockX(SectionPos.sectionRelative(pos.getX())), pos.getY(),
                    chunkPos.getBlockZ(SectionPos.sectionRelative(pos.getZ())));
            if (chunk instanceof LevelChunk levelChunk)
                return levelChunk.getBlockEntities().get(worldPos);
            return chunk.getBlockEntity(worldPos);
        }
        return level.getBlockEntity(pos);
    }
}
