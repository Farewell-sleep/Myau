package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.mixin.IAccessorRenderManager;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * CUTE VISUALS — skidded from the "Cute Visuals" Raven B4 script into OpenMyau.
 * Combined bed, heart, and dot visuals: heart/dot trails behind the player,
 * bed-break detection with particle burst, rainbow arc and sound effects.
 */
public class CuteVisuals extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // === CONSTANTS ===
    private static final int MAX_HEARTS = 50;
    private static final int MAX_DOTS = 100;
    private static final int MAX_BED_PARTICLES = 200;
    private static final int MAX_RAINBOWS = 5;
    private static final int MAX_WORLD_HEARTS = 200;

    private static final int TRAIL_HEART_SEGMENTS = 30;
    private static final int BED_HEART_SEGMENTS = 20;
    private static final int DOT_SEGMENTS = 8;
    private static final int DOT_FILL_SEGMENTS = 6;
    private static final int RAINBOW_SEGMENTS = 30;

    private static final double TWO_PI = Math.PI * 2.0;

    private static final double HEART_RADIUS = 1.5;
    private static final double HEART_SIZE = 0.15;
    private static final double HEART_FLOAT_SPEED = 1.0;
    private static final float HEART_LINE_WIDTH = 1.5f;

    private static final double DOT_SIZE = 0.04;
    private static final double DOT_SPREAD = 0.9;
    private static final double DOT_DRIFT_SPEED = 0.3;
    private static final float DOT_LINE_WIDTH = 2.0f;
    private static final int DOT_GLOW_LAYERS = 1;

    private static final double RAINBOW_SIZE = 3.0;
    private static final double RAINBOW_BAND_WIDTH = 0.15;
    private static final boolean RAINBOW_GLOW = true;

    private final Random random = new Random();

    // === MODULE SETTINGS ===
    public final BooleanProperty bedSound = new BooleanProperty("Bed Sound", true);
    public final BooleanProperty bedBurst = new BooleanProperty("Bed Burst", true);
    public final IntProperty bedBurstCount = new IntProperty("Bed Burst Count", 20, 5, 40);
    public final FloatProperty bedBurstSize = new FloatProperty("Bed Burst Size", 0.20F, 0.05F, 0.6F);
    public final FloatProperty bedBurstSpeed = new FloatProperty("Bed Burst Speed", 2.5F, 0.5F, 7.0F);
    public final IntProperty bedBurstLifetime = new IntProperty("Bed Burst Lifetime", 1500, 500, 3000);
    public final BooleanProperty rainbow = new BooleanProperty("Rainbow", true);
    public final FloatProperty rainbowLineWidth = new FloatProperty("Rainbow Line Width", 5.0F, 1.0F, 12.0F);
    public final IntProperty rainbowDuration = new IntProperty("Rainbow Duration", 3000, 1000, 6000);
    public final BooleanProperty onlyWhileMoving = new BooleanProperty("Only While Moving", true);
    public final IntProperty opacity = new IntProperty("Opacity", 85, 20, 100);
    public final BooleanProperty hearts = new BooleanProperty("Hearts", true);
    public final IntProperty heartsSpawnRate = new IntProperty("Hearts Spawn Rate", 200, 50, 500);
    public final IntProperty heartsLifetime = new IntProperty("Hearts Lifetime", 1500, 500, 4000);
    public final BooleanProperty dots = new BooleanProperty("Dots", true);
    public final IntProperty dotsSpawnRate = new IntProperty("Dots Spawn Rate", 100, 20, 200);
    public final IntProperty dotsLifetime = new IntProperty("Dots Lifetime", 1500, 500, 5000);
    public final BooleanProperty pulse = new BooleanProperty("Pulse", false);
    public final BooleanProperty worldHeart = new BooleanProperty("World Heart", false);
    public final IntProperty worldHeartAmount = new IntProperty("World Heart Amount", 160, 20, 200);
    public final FloatProperty worldHeartRange = new FloatProperty("World Heart Range", 40.0F, 16.0F, 96.0F);
    public final FloatProperty worldHeartSize = new FloatProperty("World Heart Size", 0.5F, 0.2F, 1.5F);

    // === HEART TRAIL STATE ===
    private final double[] heartX = new double[MAX_HEARTS];
    private final double[] heartY = new double[MAX_HEARTS];
    private final double[] heartZ = new double[MAX_HEARTS];
    private final long[] heartTime = new long[MAX_HEARTS];
    private final float[] heartRotY = new float[MAX_HEARTS];
    private final float[] heartRotZ = new float[MAX_HEARTS];
    private final float[] heartScale = new float[MAX_HEARTS];
    private final int[] heartType = new int[MAX_HEARTS];
    private final boolean[] heartActive = new boolean[MAX_HEARTS];
    private int activeHeartCount = 0;
    private long lastHeartSpawn = 0;

    // === DOT TRAIL STATE ===
    private final double[] dotX = new double[MAX_DOTS];
    private final double[] dotY = new double[MAX_DOTS];
    private final double[] dotZ = new double[MAX_DOTS];
    private final double[] dotDriftX = new double[MAX_DOTS];
    private final double[] dotDriftY = new double[MAX_DOTS];
    private final double[] dotDriftZ = new double[MAX_DOTS];
    private final long[] dotTime = new long[MAX_DOTS];
    private final float[] dotScale = new float[MAX_DOTS];
    private final int[] dotType = new int[MAX_DOTS];
    private final boolean[] dotActive = new boolean[MAX_DOTS];
    private int activeDotCount = 0;
    private long lastDotSpawn = 0;
    private double lastDotPosX = 0;
    private double lastDotPosY = 0;
    private double lastDotPosZ = 0;
    private boolean hasLastDotPos = false;

    // === BED BURST STATE ===
    private final double[] bedParticleX = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleY = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleZ = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVX = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVY = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVZ = new double[MAX_BED_PARTICLES];
    private final float[] bedParticleScale = new float[MAX_BED_PARTICLES];
    private final int[] bedParticleType = new int[MAX_BED_PARTICLES];
    private final long[] bedParticleTime = new long[MAX_BED_PARTICLES];
    private final boolean[] bedParticleActive = new boolean[MAX_BED_PARTICLES];
    private int activeBedParticleCount = 0;

    // === RAINBOW STATE ===
    private final double[] rainbowX = new double[MAX_RAINBOWS];
    private final double[] rainbowY = new double[MAX_RAINBOWS];
    private final double[] rainbowZ = new double[MAX_RAINBOWS];
    private final float[] rainbowYaw = new float[MAX_RAINBOWS];
    private final long[] rainbowTime = new long[MAX_RAINBOWS];
    private final boolean[] rainbowActive = new boolean[MAX_RAINBOWS];
    private int activeRainbowCount = 0;

    // === WORLD HEART STATE ===
    private final double[] worldHeartX = new double[MAX_WORLD_HEARTS];
    private final double[] worldHeartY = new double[MAX_WORLD_HEARTS];
    private final double[] worldHeartZ = new double[MAX_WORLD_HEARTS];
    private final double[] worldHeartScale = new double[MAX_WORLD_HEARTS];
    private final double[] worldHeartPhase = new double[MAX_WORLD_HEARTS];
    private final double[] worldHeartSpin = new double[MAX_WORLD_HEARTS];
    private final double[] worldHeartTilt = new double[MAX_WORLD_HEARTS];
    private final int[] worldHeartType = new int[MAX_WORLD_HEARTS];
    private final boolean[] worldHeartActive = new boolean[MAX_WORLD_HEARTS];
    private int activeWorldHeartCount = 0;

    // === BED BREAK DETECTION ===
    private boolean diggingBed = false;
    private double bedX = 0;
    private double bedY = 0;
    private double bedZ = 0;

    // === PRECOMPUTED GEOMETRY ===
    private final double[] trailHeartShapeX = new double[TRAIL_HEART_SEGMENTS + 1];
    private final double[] trailHeartShapeY = new double[TRAIL_HEART_SEGMENTS + 1];
    private final double[] bedHeartShapeX = new double[BED_HEART_SEGMENTS + 1];
    private final double[] bedHeartShapeY = new double[BED_HEART_SEGMENTS + 1];
    private final double[] dotCircleX = new double[DOT_SEGMENTS + 1];
    private final double[] dotCircleY = new double[DOT_SEGMENTS + 1];
    private final double[] dotFillX = new double[DOT_FILL_SEGMENTS + 1];
    private final double[] dotFillY = new double[DOT_FILL_SEGMENTS + 1];
    private final double[] starShapeX = new double[9];
    private final double[] starShapeY = new double[9];
    private final double[] rainbowArcCos = new double[RAINBOW_SEGMENTS + 1];
    private final double[] rainbowArcSin = new double[RAINBOW_SEGMENTS + 1];

    private final double[] rainbowRed = {0.85, 0.60, 0.50, 0.50, 1.00, 1.00, 1.00};
    private final double[] rainbowGreen = {0.50, 0.50, 0.75, 1.00, 0.90, 0.60, 0.40};
    private final double[] rainbowBlue = {1.00, 1.00, 1.00, 0.65, 0.50, 0.40, 0.50};

    private final double[] bedParticleRed = {1.00, 1.00, 1.00, 0.50, 0.50, 0.60, 0.85};
    private final double[] bedParticleGreen = {0.40, 0.60, 0.90, 1.00, 0.75, 0.50, 0.50};
    private final double[] bedParticleBlue = {0.50, 0.40, 0.50, 0.65, 1.00, 1.00, 1.00};

    public CuteVisuals() {
        super("CuteVisuals", false);
        this.initializeGeometry();
    }

    private void initializeGeometry() {
        for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
            double t = (double) i / TRAIL_HEART_SEGMENTS * TWO_PI;
            double sinT = Math.sin(t);
            trailHeartShapeX[i] = 16.0 * sinT * sinT * sinT;
            trailHeartShapeY[i] = 13.0 * Math.cos(t)
                    - 5.0 * Math.cos(2.0 * t)
                    - 2.0 * Math.cos(3.0 * t)
                    - Math.cos(4.0 * t);
        }

        for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
            double t = (double) i / BED_HEART_SEGMENTS * TWO_PI;
            double sinT = Math.sin(t);
            bedHeartShapeX[i] = 16.0 * sinT * sinT * sinT;
            bedHeartShapeY[i] = 13.0 * Math.cos(t)
                    - 5.0 * Math.cos(2.0 * t)
                    - 2.0 * Math.cos(3.0 * t)
                    - Math.cos(4.0 * t);
        }

        for (int i = 0; i <= DOT_SEGMENTS; i++) {
            double angle = (double) i / DOT_SEGMENTS * TWO_PI;
            dotCircleX[i] = Math.cos(angle);
            dotCircleY[i] = Math.sin(angle);
        }

        for (int i = 0; i <= DOT_FILL_SEGMENTS; i++) {
            double angle = (double) i / DOT_FILL_SEGMENTS * TWO_PI;
            dotFillX[i] = Math.cos(angle);
            dotFillY[i] = Math.sin(angle);
        }

        for (int i = 0; i <= 8; i++) {
            double angle = i * Math.PI / 4.0 - Math.PI / 2.0;
            double radius = (i % 2 == 0) ? 12.0 : 5.0;
            starShapeX[i] = Math.cos(angle) * radius;
            starShapeY[i] = Math.sin(angle) * radius;
        }
    }

    @Override
    public void onEnabled() {
        clearHearts();
        clearDots();
        clearBedParticles();
        clearRainbows();
        clearWorldHearts();

        lastHeartSpawn = 0;
        lastDotSpawn = 0;
        hasLastDotPos = false;
        diggingBed = false;
    }

    @Override
    public void onDisabled() {
        clearHearts();
        clearDots();
        clearBedParticles();
        clearRainbows();
        clearWorldHearts();
        diggingBed = false;
    }

    private void clearHearts() {
        for (int i = 0; i < MAX_HEARTS; i++) heartActive[i] = false;
        activeHeartCount = 0;
    }

    private void clearDots() {
        for (int i = 0; i < MAX_DOTS; i++) dotActive[i] = false;
        activeDotCount = 0;
    }

    private void clearBedParticles() {
        for (int i = 0; i < MAX_BED_PARTICLES; i++) bedParticleActive[i] = false;
        activeBedParticleCount = 0;
    }

    private void clearRainbows() {
        for (int i = 0; i < MAX_RAINBOWS; i++) rainbowActive[i] = false;
        activeRainbowCount = 0;
    }

    private void clearWorldHearts() {
        for (int i = 0; i < MAX_WORLD_HEARTS; i++) worldHeartActive[i] = false;
        activeWorldHeartCount = 0;
    }

    // === SLOT MANAGEMENT ===
    private int findFreeHeartSlot() {
        for (int i = 0; i < MAX_HEARTS; i++) {
            if (!heartActive[i]) return i;
        }

        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_HEARTS; i++) {
            if (heartTime[i] < oldest) {
                oldest = heartTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    private int findFreeDotSlot() {
        for (int i = 0; i < MAX_DOTS; i++) {
            if (!dotActive[i]) return i;
        }

        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_DOTS; i++) {
            if (dotTime[i] < oldest) {
                oldest = dotTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    private int findFreeBedParticleSlot() {
        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (!bedParticleActive[i]) return i;
        }

        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (bedParticleTime[i] < oldest) {
                oldest = bedParticleTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    private int findFreeRainbowSlot() {
        for (int i = 0; i < MAX_RAINBOWS; i++) {
            if (!rainbowActive[i]) return i;
        }

        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_RAINBOWS; i++) {
            if (rainbowTime[i] < oldest) {
                oldest = rainbowTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    // === TRAIL UPDATES ===
    @EventTarget
    public void onPreUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        double positionX = mc.thePlayer.posX;
        double positionY = mc.thePlayer.posY;
        double positionZ = mc.thePlayer.posZ;

        boolean heartsEnabled = this.hearts.getValue();
        boolean dotsEnabled = this.dots.getValue();

        if (!heartsEnabled && activeHeartCount > 0) clearHearts();
        if (!dotsEnabled && activeDotCount > 0) clearDots();

        boolean worldHeartsEnabled = this.worldHeart.getValue();
        if (!worldHeartsEnabled && activeWorldHeartCount > 0) clearWorldHearts();
        if (worldHeartsEnabled) ensureWorldHearts();

        boolean canSpawnDots = hasLastDotPos;
        if (!hasLastDotPos) {
            lastDotPosX = positionX;
            lastDotPosY = positionY;
            lastDotPosZ = positionZ;
            hasLastDotPos = true;
        }

        if (!heartsEnabled && !dotsEnabled) {
            lastDotPosX = positionX;
            lastDotPosY = positionY;
            lastDotPosZ = positionZ;
            return;
        }

        if (this.onlyWhileMoving.getValue() && !this.isMoving()) return;

        long now = System.currentTimeMillis();

        if (heartsEnabled) {
            long heartRate = this.heartsSpawnRate.getValue();
            if (now - lastHeartSpawn >= heartRate) {
                lastHeartSpawn = now;
                spawnHeart(positionX, positionY + 0.5, positionZ, now);
            }
        }

        if (dotsEnabled && canSpawnDots) {
            long dotRate = this.dotsSpawnRate.getValue();
            if (now - lastDotSpawn >= dotRate) {
                lastDotSpawn = now;
                spawnDot(positionX, positionY, positionZ, now);
            }
        }

        lastDotPosX = positionX;
        lastDotPosY = positionY;
        lastDotPosZ = positionZ;
    }

    private boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0F
                || mc.thePlayer.movementInput.moveStrafe != 0.0F;
    }

    private void spawnHeart(double x, double y, double z, long now) {
        int slot = findFreeHeartSlot();
        boolean wasActive = heartActive[slot];

        double angle = random.nextDouble() * TWO_PI;
        double distance = random.nextDouble() * HEART_RADIUS;

        heartX[slot] = x + Math.cos(angle) * distance;
        heartZ[slot] = z + Math.sin(angle) * distance;
        heartY[slot] = y + random.nextDouble() * 0.5;
        heartTime[slot] = now;
        heartRotY[slot] = (float) (random.nextDouble() * 360.0);
        heartRotZ[slot] = (float) (random.nextDouble() * 30.0 - 15.0);
        heartScale[slot] = (float) (HEART_SIZE * (0.6 + random.nextDouble() * 0.8));
        heartType[slot] = random.nextInt(3);
        heartActive[slot] = true;

        if (!wasActive) activeHeartCount++;
    }

    private void spawnDot(double x, double y, double z, long now) {
        int slot = findFreeDotSlot();
        boolean wasActive = dotActive[slot];

        dotX[slot] = lastDotPosX + (random.nextDouble() - 0.5) * DOT_SPREAD;
        dotY[slot] = y + 0.3 + random.nextDouble() * 1.2;
        dotZ[slot] = lastDotPosZ + (random.nextDouble() - 0.5) * DOT_SPREAD;

        dotDriftX[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;
        dotDriftY[slot] = (0.3 + random.nextDouble() * 0.7) * DOT_DRIFT_SPEED;
        dotDriftZ[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;

        dotTime[slot] = now;
        dotScale[slot] = (float) (DOT_SIZE * (0.5 + random.nextDouble()));
        dotType[slot] = random.nextInt(4);
        dotActive[slot] = true;

        if (!wasActive) activeDotCount++;
    }

    // === BED BREAK DETECTION ===
    @EventTarget
    public void onPacketSent(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.SEND) return;
        if (!(event.getPacket() instanceof C07PacketPlayerDigging)) return;

        C07PacketPlayerDigging diggingPacket = (C07PacketPlayerDigging) event.getPacket();
        if (diggingPacket.getStatus() == null || diggingPacket.getPosition() == null) return;

        Action status = diggingPacket.getStatus();

        if (status == Action.START_DESTROY_BLOCK) {
            BlockPos position = diggingPacket.getPosition();
            boolean isBed = mc.theWorld != null
                    && mc.theWorld.getBlockState(position).getBlock() instanceof BlockBed;

            if (!isBed) {
                diggingBed = false;
                return;
            }

            double x = position.getX() + 0.5;
            double y = position.getY() + 0.5;
            double z = position.getZ() + 0.5;

            if (mc.playerController.getCurrentGameType() == GameType.CREATIVE) {
                // Creative mode only sends START_DESTROY_BLOCK for instant breaks.
                diggingBed = false;
                spawnBedBreak(x, y, z);
            } else {
                diggingBed = true;
                bedX = x;
                bedY = y;
                bedZ = z;
            }
        } else if (status == Action.STOP_DESTROY_BLOCK) {
            if (diggingBed) {
                spawnBedBreak(bedX, bedY, bedZ);
                diggingBed = false;
            }
        } else if (status == Action.ABORT_DESTROY_BLOCK) {
            diggingBed = false;
        }
    }

    private void spawnBedBreak(double x, double y, double z) {
        long now = System.currentTimeMillis();

        if (this.rainbow.getValue()) {
            int slot = findFreeRainbowSlot();
            boolean wasActive = rainbowActive[slot];

            rainbowX[slot] = x;
            rainbowY[slot] = y;
            rainbowZ[slot] = z;
            rainbowTime[slot] = now;
            rainbowActive[slot] = true;

            if (mc.thePlayer != null) {
                rainbowYaw[slot] = (float) Math.toDegrees(
                        Math.atan2(mc.thePlayer.posX - x, mc.thePlayer.posZ - z)
                );
            } else {
                rainbowYaw[slot] = 0;
            }

            if (!wasActive) activeRainbowCount++;
        }

        if (this.bedBurst.getValue()) {
            double speed = this.bedBurstSpeed.getValue();
            double baseSize = this.bedBurstSize.getValue();
            int count = this.bedBurstCount.getValue();

            for (int i = 0; i < count; i++) {
                int slot = findFreeBedParticleSlot();
                boolean wasActive = bedParticleActive[slot];

                bedParticleX[slot] = x;
                bedParticleY[slot] = y;
                bedParticleZ[slot] = z;

                double theta = random.nextDouble() * TWO_PI;
                double phi = random.nextDouble() * Math.PI * 0.67 - Math.PI / 6.0;
                double particleSpeed = (0.8 + random.nextDouble() * 1.2) * speed;
                double cosPhi = Math.cos(phi);

                bedParticleVX[slot] = cosPhi * Math.cos(theta) * particleSpeed;
                bedParticleVY[slot] = Math.sin(phi) * particleSpeed + 1.0;
                bedParticleVZ[slot] = cosPhi * Math.sin(theta) * particleSpeed;

                int typeRoll = random.nextInt(5);
                bedParticleType[slot] = typeRoll < 2 ? 0 : typeRoll - 1;
                bedParticleScale[slot] = (float) (baseSize * (0.6 + random.nextDouble() * 0.8));
                bedParticleTime[slot] = now;
                bedParticleActive[slot] = true;

                if (!wasActive) activeBedParticleCount++;
            }
        }

        if (this.bedSound.getValue() && mc.thePlayer != null) {
            mc.thePlayer.playSound("random.orb", 1.0f, 1.5f);
            mc.thePlayer.playSound("random.levelup", 0.5f, 2.0f);
        }
    }

    // === WORLD RENDERING ===
    @EventTarget
    public void onRenderWorld(Render3DEvent event) {
        if (!this.isEnabled()) return;
        if (activeHeartCount <= 0
                && activeDotCount <= 0
                && activeBedParticleCount <= 0
                && activeRainbowCount <= 0
                && activeWorldHeartCount <= 0) return;

        if (mc.thePlayer == null) return;

        double cameraX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double cameraY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double cameraZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        long now = System.currentTimeMillis();

        renderTrail(cameraX, cameraY, cameraZ, now);
        renderWorldHearts(cameraX, cameraY, cameraZ, now);
        renderBedVisuals(cameraX, cameraY, cameraZ, now);
    }

    private void renderTrail(double cameraX, double cameraY, double cameraZ, long now) {
        boolean renderHearts = activeHeartCount > 0 && this.hearts.getValue();
        boolean renderDots = activeDotCount > 0 && this.dots.getValue();
        if (!renderHearts && !renderDots) return;

        double opacity = this.opacity.getValue() / 100.0;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        if (renderHearts) renderHearts(cameraX, cameraY, cameraZ, now, opacity);
        if (renderDots) renderDots(cameraX, cameraY, cameraZ, now, opacity);

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private void renderHearts(double cameraX, double cameraY, double cameraZ, long now, double opacity) {
        long lifetime = this.heartsLifetime.getValue();
        float effectiveLineWidth = Math.max(HEART_LINE_WIDTH * 0.6f, 1.0f);
        GL11.glLineWidth(effectiveLineWidth);

        for (int i = 0; i < MAX_HEARTS; i++) {
            if (!heartActive[i]) continue;

            long age = now - heartTime[i];
            if (age > lifetime) {
                heartActive[i] = false;
                activeHeartCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;
            double floatY = HEART_FLOAT_SPEED * progress * 1.5;

            double alpha;
            if (progress < 0.1) alpha = progress / 0.1;
            else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
            else alpha = 1.0;
            alpha *= opacity;

            double scaleAnimation;
            if (progress < 0.1) scaleAnimation = progress / 0.1;
            else if (progress > 0.8) scaleAnimation = (1.0 - progress) / 0.2;
            else scaleAnimation = 1.0;

            double drawX = heartX[i] + Math.sin(age * 0.002 + i * 1.7) * 0.1 - cameraX;
            double drawY = heartY[i] + floatY - cameraY;
            double drawZ = heartZ[i] + Math.cos(age * 0.0015 + i * 2.3) * 0.1 - cameraZ;

            double red;
            double green;
            double blue;
            int type = heartType[i];
            if (type == 0) {
                red = 1.0; green = 0.5; blue = 0.8;
            } else if (type == 1) {
                red = 1.0; green = 0.3; blue = 0.6;
            } else {
                red = 0.9; green = 0.4; blue = 0.9;
            }

            double size = heartScale[i] * scaleAnimation;
            double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));
            double spinAngle = (age * 0.1 + heartRotY[i]) % 360.0;

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate((float) billboardYaw, 0, 1, 0);
            GlStateManager.rotate((float) spinAngle, 0, 1, 0);
            GlStateManager.rotate(heartRotZ[i], 0, 0, 1);
            GL11.glLineWidth(effectiveLineWidth + 3.0f);
            drawTrailHeart(size, alpha, red, green, blue);
            GL11.glLineWidth(effectiveLineWidth);
            GlStateManager.popMatrix();
        }
    }

    private void drawTrailHeart(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;

        for (int layer = 3; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.08);
            double layerAlpha = layer == 0
                    ? baseAlpha * 0.9
                    : baseAlpha * (0.3 / layer);

            GlStateManager.color((float) red, (float) green, (float) blue, (float) layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
                GL11.glVertex3d(
                        trailHeartShapeX[i] * glowScale,
                        trailHeartShapeY[i] * glowScale,
                        0
                );
            }
            GL11.glEnd();
        }
    }

    private void renderDots(double cameraX, double cameraY, double cameraZ, long now, double opacity) {
        long lifetime = this.dotsLifetime.getValue();
        boolean pulseEnabled = this.pulse.getValue();
        GL11.glLineWidth(DOT_LINE_WIDTH);

        for (int i = 0; i < MAX_DOTS; i++) {
            if (!dotActive[i]) continue;

            long age = now - dotTime[i];
            if (age > lifetime) {
                dotActive[i] = false;
                activeDotCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;

            double fade;
            if (progress < 0.1) fade = progress / 0.1;
            else if (progress > 0.5) fade = (1.0 - progress) / 0.5;
            else fade = 1.0;

            double pulseFactor = 1.0;
            if (pulseEnabled) {
                double flickerSpeed = 3.0 + dotType[i] * 1.5;
                pulseFactor = 0.5 + 0.5 * Math.sin(age * 0.01 * flickerSpeed + i * 2.7);
            }

            double ageSeconds = age / 1000.0;
            double drawX = dotX[i]
                    + dotDriftX[i] * ageSeconds
                    + Math.sin(ageSeconds * 1.5 + i * 1.3) * 0.15
                    - cameraX;
            double drawY = dotY[i] + dotDriftY[i] * ageSeconds - cameraY;
            double drawZ = dotZ[i]
                    + dotDriftZ[i] * ageSeconds
                    + Math.cos(ageSeconds * 1.2 + i * 2.1) * 0.15
                    - cameraZ;

            double red;
            double green;
            double blue;
            int type = dotType[i];
            if (type == 0) {
                red = 1.0; green = 0.45; blue = 0.7;
            } else if (type == 1) {
                red = 1.0; green = 0.6; blue = 0.85;
            } else if (type == 2) {
                red = 1.0; green = 0.3; blue = 0.55;
            } else {
                red = 1.0; green = 0.75; blue = 0.95;
            }

            double alpha = fade * pulseFactor * opacity;
            if (alpha < 0.02) continue;

            double size = dotScale[i];
            double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate((float) billboardYaw, 0, 1, 0);
            GlStateManager.color((float) red, (float) green, (float) blue, (float) alpha);

            // DOT_GLOW_LAYERS is intentionally hardcoded to one: a filled core plus outline.
            if (DOT_GLOW_LAYERS == 1) {
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glVertex3d(0, 0, 0);
                for (int j = 0; j <= DOT_FILL_SEGMENTS; j++) {
                    GL11.glVertex3d(dotFillX[j] * size, dotFillY[j] * size, 0);
                }
                GL11.glEnd();

                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (int j = 0; j <= DOT_SEGMENTS; j++) {
                    GL11.glVertex3d(dotCircleX[j] * size, dotCircleY[j] * size, 0);
                }
                GL11.glEnd();
            }

            GlStateManager.popMatrix();
        }
    }

    // === BED RENDERING ===
    private void renderBedVisuals(double cameraX, double cameraY, double cameraZ, long now) {
        if (activeBedParticleCount <= 0 && activeRainbowCount <= 0) return;

        long bedLifetime = activeBedParticleCount > 0
                ? this.bedBurstLifetime.getValue()
                : 0;
        long rainbowDuration = activeRainbowCount > 0
                ? this.rainbowDuration.getValue()
                : 0;
        float rainbowLineWidth = this.rainbowLineWidth.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GlStateManager.disableCull();

        if (activeRainbowCount > 0) {
            renderRainbows(cameraX, cameraY, cameraZ, now, rainbowDuration, rainbowLineWidth);
        }

        if (activeBedParticleCount > 0) {
            renderBedParticles(cameraX, cameraY, cameraZ, now, bedLifetime, rainbowLineWidth);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private void renderRainbows(double cameraX, double cameraY, double cameraZ, long now, long duration, float lineWidth) {
        for (int rainbowIndex = 0; rainbowIndex < MAX_RAINBOWS; rainbowIndex++) {
            if (!rainbowActive[rainbowIndex]) continue;

            long age = now - rainbowTime[rainbowIndex];
            if (age > duration) {
                rainbowActive[rainbowIndex] = false;
                activeRainbowCount--;
                continue;
            }

            double progress = (double) age / (double) duration;

            double alpha;
            if (progress < 0.15) alpha = progress / 0.15;
            else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
            else alpha = 1.0;

            double arcProgress = progress < 0.2
                    ? Math.pow(progress / 0.2, 2.0)
                    : 1.0;

            for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
                double angle = (double) segment / RAINBOW_SEGMENTS * Math.PI * arcProgress;
                rainbowArcCos[segment] = Math.cos(angle);
                rainbowArcSin[segment] = Math.sin(angle);
            }

            double drawX = rainbowX[rainbowIndex] - cameraX;
            double drawY = rainbowY[rainbowIndex] - cameraY;
            double drawZ = rainbowZ[rainbowIndex] - cameraZ;

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate(rainbowYaw[rainbowIndex], 0, 1, 0);

            for (int band = 0; band < 7; band++) {
                double radius = RAINBOW_SIZE + (band - 3) * RAINBOW_BAND_WIDTH;
                if (radius < 0.1) continue;

                double red = rainbowRed[band];
                double green = rainbowGreen[band];
                double blue = rainbowBlue[band];

                if (RAINBOW_GLOW) {
                    drawRainbowArc(radius, red, green, blue, alpha * 0.15, lineWidth + 3.0f);
                    drawRainbowArc(radius, red, green, blue, alpha * 0.30, lineWidth + 1.5f);
                }
                drawRainbowArc(radius, red, green, blue, alpha * 0.85, lineWidth);
            }

            if (arcProgress > 0.5) {
                drawRainbowSparkles(now, arcProgress, alpha);
            }

            GlStateManager.popMatrix();
        }
    }

    private void drawRainbowArc(
            double radius,
            double red,
            double green,
            double blue,
            double alpha,
            float lineWidth
    ) {
        GL11.glLineWidth(lineWidth);
        GlStateManager.color((float) red, (float) green, (float) blue, (float) alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
            GL11.glVertex3d(
                    rainbowArcCos[segment] * radius,
                    rainbowArcSin[segment] * radius,
                    0
            );
        }
        GL11.glEnd();
    }

    private void drawRainbowSparkles(long now, double arcProgress, double alpha) {
        double sparkleAlpha = alpha * 0.7;
        double sparkleSize = 0.15;
        double rotation = now * 0.003;
        double cosRotation = Math.cos(rotation);
        double sinRotation = Math.sin(rotation);

        GlStateManager.color(1.0f, 1.0f, 0.8f, (float) sparkleAlpha);

        for (int end = 0; end < 2; end++) {
            double endAngle = end == 0 ? 0.0 : Math.PI * arcProgress;
            double endX = Math.cos(endAngle) * RAINBOW_SIZE;
            double endY = Math.sin(endAngle) * RAINBOW_SIZE;

            drawSparkleRay(endX, endY, cosRotation, sinRotation, sparkleSize);
            drawSparkleRay(endX, endY, -sinRotation, cosRotation, sparkleSize);
            drawSparkleRay(endX, endY, -cosRotation, -sinRotation, sparkleSize);
            drawSparkleRay(endX, endY, sinRotation, -cosRotation, sparkleSize);
        }
    }

    private void drawSparkleRay(double x, double y, double directionX, double directionY, double size) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(x, y, 0);
        GL11.glVertex3d(x + directionX * size, y + directionY * size, 0);
        GL11.glEnd();
    }

    private void renderBedParticles(double cameraX, double cameraY, double cameraZ, long now, long lifetime, float lineWidth) {
        GL11.glLineWidth(lineWidth);

        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (!bedParticleActive[i]) continue;

            long age = now - bedParticleTime[i];
            if (age > lifetime) {
                bedParticleActive[i] = false;
                activeBedParticleCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;
            double ageSeconds = age / 1000.0;

            double particleX = bedParticleX[i] + bedParticleVX[i] * ageSeconds;
            double particleY = bedParticleY[i] + bedParticleVY[i] * ageSeconds
                    - 1.5 * ageSeconds * ageSeconds;
            double particleZ = bedParticleZ[i] + bedParticleVZ[i] * ageSeconds;

            particleX += Math.sin(age * 0.002 + i * 1.7) * 0.05;
            particleZ += Math.cos(age * 0.0015 + i * 2.3) * 0.05;

            double alpha;
            if (progress < 0.1) alpha = progress / 0.1;
            else if (progress > 0.7) alpha = (1.0 - progress) / 0.3;
            else alpha = 1.0;

            double scaleAnimation;
            if (progress < 0.1) scaleAnimation = progress / 0.1;
            else if (progress > 0.7) scaleAnimation = (1.0 - progress) / 0.3;
            else scaleAnimation = 1.0;

            double drawX = particleX - cameraX;
            double drawY = particleY - cameraY;
            double drawZ = particleZ - cameraZ;

            int colorIndex = i % 7;
            double red = bedParticleRed[colorIndex];
            double green = bedParticleGreen[colorIndex];
            double blue = bedParticleBlue[colorIndex];

            double size = bedParticleScale[i] * scaleAnimation;
            double billboardYaw = Math.toDegrees(Math.atan2(drawX, drawZ));

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate((float) billboardYaw, 0, 1, 0);
            GlStateManager.rotate((float) (ageSeconds * 40.0 + i * 60.0), 0, 0, 1);

            int type = bedParticleType[i];
            if (type == 0) {
                drawBedHeart(size, alpha, red, green, blue);
            } else if (type == 1) {
                drawBedStar(size, alpha, red, green, blue);
            } else if (type == 2) {
                drawBedDot(size, alpha, red, green, blue);
            } else {
                drawBedDiamond(size, alpha, red, green, blue);
            }

            GlStateManager.popMatrix();
        }
    }

    private void drawBedHeart(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            double layerAlpha = layer == 0
                    ? baseAlpha * 0.9
                    : baseAlpha * (0.25 / layer);

            GlStateManager.color((float) red, (float) green, (float) blue, (float) layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
                GL11.glVertex3d(
                        bedHeartShapeX[i] * glowScale,
                        bedHeartShapeY[i] * glowScale,
                        0
                );
            }
            GL11.glEnd();
        }
    }

    private void drawBedStar(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            double layerAlpha = layer == 0
                    ? baseAlpha * 0.9
                    : baseAlpha * (0.25 / layer);

            GlStateManager.color((float) red, (float) green, (float) blue, (float) layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= 8; i++) {
                GL11.glVertex3d(starShapeX[i] * glowScale, starShapeY[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedDot(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 2.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.15);
            double layerAlpha = layer == 0
                    ? baseAlpha * 0.9
                    : baseAlpha * (0.25 / layer);

            GlStateManager.color((float) red, (float) green, (float) blue, (float) layerAlpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0, 0, 0);
            for (int i = 0; i <= DOT_SEGMENTS; i++) {
                GL11.glVertex3d(dotCircleX[i] * glowScale, dotCircleY[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedDiamond(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            double layerAlpha = layer == 0
                    ? baseAlpha * 0.9
                    : baseAlpha * (0.25 / layer);

            GlStateManager.color((float) red, (float) green, (float) blue, (float) layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex3d(0, 14.0 * glowScale, 0);
            GL11.glVertex3d(8.0 * glowScale, 0, 0);
            GL11.glVertex3d(0, -14.0 * glowScale, 0);
            GL11.glVertex3d(-8.0 * glowScale, 0, 0);
            GL11.glVertex3d(0, 14.0 * glowScale, 0);
            GL11.glEnd();
        }
    }
    // === WORLD HEARTS ===
    private void ensureWorldHearts() {
        if (mc.thePlayer == null) return;
        int target = Math.min(this.worldHeartAmount.getValue(), MAX_WORLD_HEARTS);
        if (activeWorldHeartCount > target) {
            activeWorldHeartCount = target;
        }
        while (activeWorldHeartCount < target) {
            spawnWorldHeart(activeWorldHeartCount);
            activeWorldHeartCount++;
        }
        double respawnDistance = this.worldHeartRange.getValue() + 24.0;
        for (int i = 0; i < activeWorldHeartCount; i++) {
            double dx = worldHeartX[i] - mc.thePlayer.posX;
            double dz = worldHeartZ[i] - mc.thePlayer.posZ;
            if (dx * dx + dz * dz > respawnDistance * respawnDistance) {
                spawnWorldHeart(i);
            }
        }
    }

    private void spawnWorldHeart(int i) {
        double angle = random.nextDouble() * TWO_PI;
        double range = this.worldHeartRange.getValue();
        double distance = range * Math.sqrt(random.nextDouble());
        worldHeartX[i] = mc.thePlayer.posX + Math.cos(angle) * distance;
        worldHeartZ[i] = mc.thePlayer.posZ + Math.sin(angle) * distance;
        worldHeartY[i] = mc.thePlayer.posY - 2.0 + random.nextDouble() * 18.0;
        worldHeartScale[i] = this.worldHeartSize.getValue() * (0.7 + random.nextDouble() * 0.6);
        worldHeartPhase[i] = random.nextDouble() * TWO_PI;
        worldHeartSpin[i] = random.nextDouble() * 360.0;
        worldHeartTilt[i] = random.nextDouble() * 20.0 - 10.0;
        worldHeartType[i] = random.nextInt(3);
        worldHeartActive[i] = true;
    }

    private void renderWorldHearts(double cameraX, double cameraY, double cameraZ, long now) {
        if (activeWorldHeartCount <= 0 || !this.worldHeart.getValue()) return;

        double opacity = this.opacity.getValue() / 100.0;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (int i = 0; i < activeWorldHeartCount; i++) {
            if (!worldHeartActive[i]) continue;

            double floatOffset = Math.sin(now * 0.0008 + worldHeartPhase[i]) * 0.4;
            double sway = Math.sin(now * 0.0005 + worldHeartPhase[i] * 1.7) * 0.3;
            double alphaPulse = 0.85 + 0.15 * Math.sin(now * 0.001 + worldHeartPhase[i] * 2.0);

            double drawX = worldHeartX[i] + sway - cameraX;
            double drawY = worldHeartY[i] + floatOffset - cameraY;
            double drawZ = worldHeartZ[i] + sway * 0.5 - cameraZ;

            if (drawX * drawX + drawY * drawY + drawZ * drawZ > 128.0 * 128.0) continue;

            double red;
            double green;
            double blue;
            int type = worldHeartType[i];
            if (type == 0) {
                red = 1.0; green = 0.5; blue = 0.8;
            } else if (type == 1) {
                red = 1.0; green = 0.3; blue = 0.6;
            } else {
                red = 0.9; green = 0.4; blue = 0.9;
            }

            double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));
            double spinAngle = (now * 0.03 + worldHeartSpin[i]) % 360.0;

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate((float) billboardYaw, 0, 1, 0);
            GlStateManager.rotate((float) spinAngle, 0, 1, 0);
            GlStateManager.rotate((float) worldHeartTilt[i], 0, 0, 1);

            double scale = worldHeartScale[i] / 16.0;
            double alpha = opacity * alphaPulse;
            for (int layer = 2; layer >= 0; layer--) {
                double glowScale = scale * (1.0 + layer * 0.1);
                double layerAlpha = layer == 0 ? alpha * 0.9 : alpha * (0.25 / layer);
                GlStateManager.color((float) red, (float) green, (float) blue, (float) layerAlpha);
                GL11.glLineWidth(layer == 0 ? 2.0f : 1.0f);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (int s = 0; s <= TRAIL_HEART_SEGMENTS; s++) {
                    GL11.glVertex3d(trailHeartShapeX[s] * glowScale, trailHeartShapeY[s] * glowScale, 0);
                }
                GL11.glEnd();
            }

            GlStateManager.popMatrix();
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

}
