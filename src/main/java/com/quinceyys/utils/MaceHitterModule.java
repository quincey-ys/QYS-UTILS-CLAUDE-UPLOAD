package com.quinceyys.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MaceHitterModule {
    private static final Path CONFIG = Path.of("config", "qys-mace-hitter.properties");

    private static KeyMapping toggleKey;
    private static KeyMapping slamKey;

    public static boolean enabled = false;
    public static double range = 3.2;
    public static int delayTicks = 0;
    public static double slamRange = 6.0;
    public static boolean rotate = true;
    public static boolean autoSwap = true;
    public static boolean autoJump = true;
    public static boolean fallingOnly = false;
    public static boolean shieldStun = true;
    public static boolean requireMace = false;
    public static boolean ignoreWalls = false;
    public static boolean playersOnly = true;
    public static boolean ignoreTeammates = true;

    private static int delayCounter;
    private static Entity currentTarget;
    private static boolean jumpHeldByMod;

    private MaceHitterModule() {}

    public static void initialize() {
        loadConfig();

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.qys_utils.mace_toggle", InputConstants.KEY_R, KeyMapping.Category.MISC
        ));
        slamKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.qys_utils.mace_slam", InputConstants.KEY_G, KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(MaceHitterModule::onEndTick);
    }

    public static void slamNow() {
        manualSlam(Minecraft.getInstance());
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            if (jumpHeldByMod) {
                Minecraft client = Minecraft.getInstance();
                if (client != null) client.options.keyJump.setDown(false);
                jumpHeldByMod = false;
            }
            currentTarget = null;
        }
        saveConfig();
    }

    private static void onEndTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            setEnabled(!enabled);
        }

        while (slamKey.consumeClick()) manualSlam(client);

        LocalPlayer player = client.player;
        if (!enabled || player == null || client.level == null || client.gameMode == null || !player.isAlive()) {
            if (player == null || client.level == null) releaseJump(client);
            return;
        }

        currentTarget = findTarget(client, player, range);
        if (currentTarget == null) {
            releaseJump(client);
            delayCounter = 0;
            return;
        }

        if (rotate) rotateTo(player, currentTarget);

        boolean blocking = isBlocking(currentTarget);
        boolean maceReady = ensureMace(player);
        if (requireMace && !maceReady) {
            releaseJump(client);
            return;
        }

        if (autoJump && player.onGround()) {
            client.options.keyJump.setDown(true);
            jumpHeldByMod = true;
        } else if (jumpHeldByMod && !player.onGround()) {
            client.options.keyJump.setDown(false);
            jumpHeldByMod = false;
        }

        boolean shouldAttack = (blocking && shieldStun)
                || (fallingOnly ? isFalling(player) : true);

        if (shouldAttack && delayCounter <= 0) {
            client.gameMode.attack(player, currentTarget);
            delayCounter = delayTicks;
        }
        if (delayCounter > 0) delayCounter--;
    }

    private static Entity findTarget(Minecraft client, LocalPlayer player, double targetRange) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == player || entity.isSpectator()) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (playersOnly && !(entity instanceof Player)) continue;
            if (entity instanceof Player other && ignoreTeammates && player.isAlliedTo(other)) continue;

            double distance = player.distanceTo(entity);
            if (distance > targetRange || distance >= bestDistance) continue;
            if (!ignoreWalls && !player.hasLineOfSight(entity)) continue;

            best = entity;
            bestDistance = distance;
        }
        return best;
    }

    private static boolean ensureMace(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof MaceItem) return true;
        if (!autoSwap) return false;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof MaceItem) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    private static boolean isBlocking(Entity target) {
        return target instanceof LivingEntity living && living.isUsingItem() && living.isBlocking();
    }

    private static boolean isFalling(LocalPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        return !player.onGround() && velocity.y < 0.0;
    }

    private static void rotateTo(LocalPlayer player, Entity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dy = target.getEyeY() - player.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0 / Math.PI));
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    private static void manualSlam(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null) return;

        Entity target = currentTarget;
        if (target == null || !target.isAlive() || player.distanceTo(target) > slamRange) {
            target = findTarget(client, player, slamRange);
        }
        if (target == null || player.distanceTo(target) > slamRange) return;

        if (rotate) rotateTo(player, target);
        client.gameMode.attack(player, target);
        currentTarget = target;
    }

    private static void releaseJump(Minecraft client) {
        if (jumpHeldByMod) {
            client.options.keyJump.setDown(false);
            jumpHeldByMod = false;
        }
    }

    public static void loadConfig() {
        Properties properties = new Properties();
        try {
            Files.createDirectories(CONFIG.getParent());
            if (Files.exists(CONFIG)) {
                try (InputStream input = Files.newInputStream(CONFIG)) {
                    properties.load(input);
                }
            }
            enabled = bool(properties, "enabled", false);
            range = decimal(properties, "range", 3.2, 1.0, 6.0);
            delayTicks = integer(properties, "delayTicks", 0, 0, 20);
            slamRange = decimal(properties, "slamRange", 6.0, 1.0, 8.0);
            rotate = bool(properties, "rotate", true);
            autoSwap = bool(properties, "autoSwap", true);
            autoJump = bool(properties, "autoJump", true);
            fallingOnly = bool(properties, "fallingOnly", false);
            shieldStun = bool(properties, "shieldStun", true);
            requireMace = bool(properties, "requireMace", false);
            ignoreWalls = bool(properties, "ignoreWalls", false);
            playersOnly = bool(properties, "playersOnly", true);
            ignoreTeammates = bool(properties, "ignoreTeammates", true);
            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/MaceHitter] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("range", Double.toString(range));
        properties.setProperty("delayTicks", Integer.toString(delayTicks));
        properties.setProperty("slamRange", Double.toString(slamRange));
        properties.setProperty("rotate", Boolean.toString(rotate));
        properties.setProperty("autoSwap", Boolean.toString(autoSwap));
        properties.setProperty("autoJump", Boolean.toString(autoJump));
        properties.setProperty("fallingOnly", Boolean.toString(fallingOnly));
        properties.setProperty("shieldStun", Boolean.toString(shieldStun));
        properties.setProperty("requireMace", Boolean.toString(requireMace));
        properties.setProperty("ignoreWalls", Boolean.toString(ignoreWalls));
        properties.setProperty("playersOnly", Boolean.toString(playersOnly));
        properties.setProperty("ignoreTeammates", Boolean.toString(ignoreTeammates));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                properties.store(output, "QYS-UTILS Mace Hitter settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/MaceHitter] Config save failed: " + exception.getMessage());
        }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private static int integer(Properties properties, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double decimal(Properties properties, String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
