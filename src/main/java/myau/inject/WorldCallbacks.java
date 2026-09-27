package myau.inject;

import myau.Myau;
import myau.module.modules.AntiObbyTrap;
import myau.module.modules.BedESP;
import myau.module.modules.Chams;
import myau.module.modules.ESP;
import myau.module.modules.Jesus;
import myau.module.modules.ViewClip;
import myau.module.modules.Xray;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.nio.IntBuffer;

public final class WorldCallbacks {
    public static final String OWNER = "myau/inject/WorldCallbacks";
    private WorldCallbacks() {
    }
    public static Object shouldSideBeRendered(Block block, IBlockAccess access,
                                              BlockPos pos, EnumFacing facing) {
        try {
            if (Myau.moduleManager == null) {
                return null;
            }
            Xray xray = (Xray) Myau.moduleManager.modules.get(Xray.class);
            if (xray.isEnabled() && xray.mode.getValue() == 1
                    && xray.shouldRenderSide(Block.getIdFromBlock(block))) {
                BlockPos behind = new BlockPos(
                        pos.getX() - facing.getDirectionVec().getX(),
                        pos.getY() - facing.getDirectionVec().getY(),
                        pos.getZ() - facing.getDirectionVec().getZ());
                if (xray.checkBlock(behind)) {
                    return Boolean.TRUE;
                }
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return null;
    }
    public static Object getBlockLayer(Block block) {
        try {
            if (Myau.moduleManager == null) {
                return null;
            }
            Xray xray = (Xray) Myau.moduleManager.modules.get(Xray.class);
            if (xray.isEnabled()) {
                int id = Block.getIdFromBlock(block);
                if (!xray.shouldRenderSide(id)
                        || xray.mode.getValue() == 0 && !xray.isXrayBlock(id)) {
                    return new Object[]{EnumWorldBlockLayer.TRANSLUCENT};
                }
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return null;
    }
    public static Object getBlockLayerAlwaysTranslucent() {
        try {
            if (Myau.moduleManager != null
                    && Myau.moduleManager.modules.get(Xray.class).isEnabled()) {
                return new Object[]{EnumWorldBlockLayer.TRANSLUCENT};
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return null;
    }

    public static Object renderModel(BlockModelRenderer renderer, IBlockAccess access,
                                     IBakedModel model, IBlockState state, BlockPos pos,
                                     WorldRenderer worldRenderer, boolean checkSides) {
        try {
            if (Myau.moduleManager != null
                    && Myau.moduleManager.modules.get(Xray.class).isEnabled()) {
                return Boolean.valueOf(renderer.renderModelAmbientOcclusion(
                        access, model, state.getBlock(), pos, worldRenderer, checkSides));
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return null;
    }
    public static void renderBlock(IBlockState state, BlockPos pos) {
        try {
            if (Myau.moduleManager == null) {
                return;
            }
            BedESP bedESP = (BedESP) Myau.moduleManager.modules.get(BedESP.class);
            if (bedESP.isEnabled() && state.getBlock() instanceof BlockBed
                    && state.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                bedESP.beds.add(new BlockPos(pos));
            }
            Xray xray = (Xray) Myau.moduleManager.modules.get(Xray.class);
            if (xray.isEnabled() && xray.isXrayBlock(Block.getIdFromBlock(state.getBlock()))) {
                if (xray.checkBlock(pos)) {
                    xray.trackedBlocks.add(new BlockPos(pos));
                } else {
                    xray.trackedBlocks.remove(pos);
                }
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
    private static boolean wantsEverythingVisible() {
        return Myau.moduleManager != null
                && (Myau.moduleManager.modules.get(Chams.class).isEnabled()
                || Myau.moduleManager.modules.get(ViewClip.class).isEnabled()
                || Myau.moduleManager.modules.get(Xray.class).isEnabled());
    }
    public static boolean setOpaqueCube() {
        try {
            return wantsEverythingVisible();
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }
    public static Object computeVisibility() {
        try {
            if (wantsEverythingVisible()) {
                SetVisibility visibility = new SetVisibility();
                visibility.setAllVisible(true);
                return new Object[]{visibility};
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return null;
    }
    public static Object hasEffect(ItemStack stack) {
        try {
            if (Myau.moduleManager != null) {
                ESP esp = (ESP) Myau.moduleManager.modules.get(ESP.class);
                if (esp.isEnabled() && !esp.isGlowEnabled()) {
                    return Boolean.FALSE;
                }
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return null;
    }
    public static IntBuffer putColorMultiplier(IntBuffer buffer, int index, int colour) {
        try {
            if (Myau.moduleManager != null) {
                Xray xray = (Xray) Myau.moduleManager.modules.get(Xray.class);
                if (xray.isEnabled()) {
                    int alpha = (int) ((float) xray.opacity.getValue().intValue() * 255.0F / 100.0F);
                    return buffer.put(index, colour & 16777215 | alpha << 24);
                }
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return buffer.put(index, colour);
    }
    public static boolean isPushedByWater(Entity entity) {
        try {
            if (entity instanceof EntityPlayerSP && Myau.moduleManager != null) {
                Jesus jesus = (Jesus) Myau.moduleManager.modules.get(Jesus.class);
                if (jesus.isEnabled() && jesus.noPush.getValue()) {
                    return false;
                }
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return entity.isPushedByWater();
    }
    public static IBlockState rayTraceBlockState(World world, BlockPos pos) {
        try {
            if (Myau.moduleManager != null) {
                AntiObbyTrap trap = (AntiObbyTrap) Myau.moduleManager.modules.get(AntiObbyTrap.class);
                if (trap.isEnabled() && trap.isInsideBlock(world, pos)) {
                    if (trap.setAir.getValue()) {
                        world.setBlockToAir(pos);
                    }
                    return Blocks.air.getDefaultState();
                }
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        return world.getBlockState(pos);
    }
}
