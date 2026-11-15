package com.copycatsplus.copycats.foundation.copycat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Client-only part of {@link CopycatLightHelper}. Only load this class after checking {@link Level#isClientSide()}.
 */
@Environment(EnvType.CLIENT)
final class CopycatLightClientHelper {

    private CopycatLightClientHelper() {
    }

    /**
     * Whether the level is the world the player is in, as opposed to a virtual level such as a Ponder scene,
     * a schematic preview or a contraption render world.
     */
    static boolean isClientWorld(Level level) {
        return level == Minecraft.getInstance().level;
    }
}
