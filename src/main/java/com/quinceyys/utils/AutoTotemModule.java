package com.quinceyys.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AutoTotemModule {
    public static final Path CONFIG = Path.of("config", "autism-autototem.properties");

    public static KeyMapping toggleKey;
    public static KeyMapping configKey;

    public static boolean enabled = true;
    public static boolean healthMode = true;
    public static boolean safety = true;
    public static boolean switchBack = true;
    public static boolean predictFall = true;
    public static boolean missingArmor = true;
    public static boolean sendDirectly = false;
    public static boolean ignoreElytra = false;
    public static int healthThreshold = 14;
    public static int safeHealthThreshold = 10;
    public static int switchDelayMs = 0;
    public static int switchBackDelayMs = 40;
    public static int reactionTicks = 1;
    public static int holeResistance = 600;
    public static String switchMode = "Smart";

    private static long lastSwitchAt;
    private static long safeSince = -1L;
    private static int reactionUntil;
    private static int ticks;
    private static int displacedSlot = -1;
    private static boolean swappedForSafety;

    private AutoTotemModule() {}

    public static void initialize() {
        loadConfig();

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.qys_utils.autototem_toggle", InputConstants.KEY_T, KeyMapping.Category.MISC
        ));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.qys_utils.open_config", InputConstants.KEY_O, KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(AutoTotemModule::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            saveConfig();
        }
        while (configKey.consumeClick()) {
            client.gui.setScreen(new QysUtilsConfigScreen(client.gui.screen()));
        }

        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            reactionUntil = 0;
            displacedSlot = -1;
            swappedForSafety = false;
            safeSince = -1L;
            return;
        }
        if (!enabled || client.gameMode == null) return;
        if (ignoreElytra && player.isFallFlying()) return;

        ticks++;
        long now = System.currentTimeMillis();

        if (!shouldUseTotem(player)) {
            reactionUntil = 0;
            if (swappedForSafety && switchBack && displacedSlot >= 0) {
                boolean safe;
                if (safety) {
                    float hp = player.getHealth() + player.getAbsorptionAmount();
                    if (hp <= safeHealthThreshold) {
                        safe = false;
                        safeSince = -1L;
                    } else {
                        if (safeSince == -1L) safeSince = now;
                        safe = now - safeSince >= switchBackDelayMs && !isInHole(client);
                    }
                } else {
                    safe = true;
                }

                if (safe) {
                    swapBack(client, displacedSlot);
                    swappedForSafety = false;
                    displacedSlot = -1;
                    safeSince = -1L;
                }
            }
            return;
        }

        safeSince = -1L;
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.TOTEM_OF_UNDYING)) return;
        if (switchMode.equalsIgnoreCase("PickUp") && !offhand.isEmpty()) return;

        boolean instant = switchMode.equalsIgnoreCase("Switch") || sendDirectly;
        if (!instant) {
            if (reactionUntil == 0) reactionUntil = ticks + reactionTicks;
            if (ticks < reactionUntil) return;
            if (now - lastSwitchAt < switchDelayMs) return;
        }

        int slot = findTotemSlot(player);
        if (slot >= 0 && swapIntoOffhand(client, slot)) {
            lastSwitchAt = now;
            swappedForSafety = true;
        }
    }

    private static boolean shouldUseTotem(LocalPlayer player) {
        if (healthMode && player.getHealth() + player.getAbsorptionAmount() <= healthThreshold) return true;
        if (predictFall && isFallRisk(player)) return true;
        return missingArmor && hasMissingArmor(player);
    }

    private static boolean isFallRisk(LocalPlayer player) {
        if (player.onGround() || player.isFallFlying() || player.isInWater()
                || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.hasEffect(MobEffects.LEVITATION)) return false;

        double projected = player.fallDistance - player.getDeltaMovement().y;
        int damage = Math.max(0, (int) Math.ceil(projected - 3.0F));
        return damage > 0
                && player.getHealth() + player.getAbsorptionAmount() - damage <= healthThreshold;
    }

    private static boolean hasMissingArmor(LocalPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static boolean isInHole(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return false;

        BlockPos pos = player.blockPosition();
        Direction[] horizontal = {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        for (Direction direction : horizontal) {
            if (client.level.getBlockState(pos.relative(direction)).getBlock().getExplosionResistance() < holeResistance) {
                return false;
            }
            if (client.level.getBlockState(pos.relative(direction).above()).getBlock().getExplosionResistance() < holeResistance) {
                return false;
            }
        }
        return true;
    }

    private static int findTotemSlot(LocalPlayer player) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private static boolean swapIntoOffhand(Minecraft client, int inventoryIndex) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return false;

        int slotId = inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
        client.gameMode.handleContainerInput(
                player.inventoryMenu.containerId, slotId, 40, ContainerInput.SWAP, player
        );
        displacedSlot = inventoryIndex;
        return true;
    }

    private static void swapBack(Minecraft client, int inventoryIndex) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return;

        int slotId = inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
        client.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId, slotId, 40, ClickType.SWAP, player
        );
    }

    public static void loadConfig() {
        try {
            Properties p = new Properties();
            Files.createDirectories(CONFIG.getParent());
            if (Files.exists(CONFIG)) {
                try (InputStream in = Files.newInputStream(CONFIG)) {
                    p.load(in);
                }
            }

            enabled = bool(p, "enabled", true);
            healthMode = bool(p, "healthMode", true);
            safety = bool(p, "safety", true);
            switchBack = bool(p, "switchBack", true);
            predictFall = bool(p, "predictFallDamage", true);
            missingArmor = bool(p, "missingArmor", true);
            sendDirectly = bool(p, "sendDirectly", false);
            ignoreElytra = bool(p, "ignoreElytra", false);
            healthThreshold = integer(p, "healthThreshold", 14, 0, 20);
            safeHealthThreshold = integer(p, "safeHealthThreshold", 10, 0, 20);
            switchDelayMs = integer(p, "switchDelayMs", 0, 0, 5000);
            switchBackDelayMs = integer(p, "switchBackDelayMs", 40, 0, 5000);
            reactionTicks = integer(p, "reactionTicks", 1, 0, 20);
            holeResistance = integer(p, "holeResistance", 600, 0, 3600000);
            switchMode = p.getProperty("switchMode", "Smart");
            if (!switchMode.equalsIgnoreCase("Smart")
                    && !switchMode.equalsIgnoreCase("Switch")
                    && !switchMode.equalsIgnoreCase("PickUp")) {
                switchMode = "Smart";
            }
            saveConfig();
        } catch (IOException e) {
            System.err.println("[QYS-UTILS/AutoTotem] Config load failed: " + e.getMessage());
        }
    }

    public static void saveConfig() {
        try {
            Properties p = new Properties();
            p.setProperty("enabled", Boolean.toString(enabled));
            p.setProperty("switchMode", switchMode);
            p.setProperty("switchDelayMs", Integer.toString(switchDelayMs));
            p.setProperty("switchBackDelayMs", Integer.toString(switchBackDelayMs));
            p.setProperty("healthMode", Boolean.toString(healthMode));
            p.setProperty("safety", Boolean.toString(safety));
            p.setProperty("switchBack", Boolean.toString(switchBack));
            p.setProperty("predictFallDamage", Boolean.toString(predictFall));
            p.setProperty("missingArmor", Boolean.toString(missingArmor));
            p.setProperty("sendDirectly", Boolean.toString(sendDirectly));
            p.setProperty("ignoreElytra", Boolean.toString(ignoreElytra));
            p.setProperty("healthThreshold", Integer.toString(healthThreshold));
            p.setProperty("safeHealthThreshold", Integer.toString(safeHealthThreshold));
            p.setProperty("reactionTicks", Integer.toString(reactionTicks));
            p.setProperty("holeResistance", Integer.toString(holeResistance));
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "QYS-UTILS AutoTotem settings");
            }
        } catch (IOException e) {
            System.err.println("[QYS-UTILS/AutoTotem] Config save failed: " + e.getMessage());
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(fallback)));
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    Integer.parseInt(p.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
