package com.quinceyys.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Continuously places and breaks only crystals placed by the local player.
 * Hold RMB on obsidian/bedrock with an End Crystal selected to repeat the cycle.
 * Releasing RMB stops further placements; the current owned crystal is still broken.
 */
public final class CrystalMacroModule {
    private static final Path CONFIG = Path.of("config", "qys-crystal-macro.properties");
    private static KeyMapping toggleKey;

    public static boolean enabled = false;
    public static int windowTicks = 10;
    public static double attackRange = 3.0;
    public static boolean swing = true;

    private static final List<Pending> PENDING = new ArrayList<>();
    private static int tickCounter;
    private static int lastPlacementAttemptTick = Integer.MIN_VALUE;

    private CrystalMacroModule() {}

    public static void initialize() {
        loadConfig();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.qys_utils.crystal_toggle", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC
        ));
        UseBlockCallback.EVENT.register(CrystalMacroModule::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(CrystalMacroModule::onEndTick);
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!enabled || player != Minecraft.getInstance().player) return InteractionResult.PASS;
        if (!player.getItemInHand(hand).is(Items.END_CRYSTAL)) return InteractionResult.PASS;

        BlockState clicked = level.getBlockState(hit.getBlockPos());
        if (!clicked.is(Blocks.OBSIDIAN) && !clicked.is(Blocks.BEDROCK)) return InteractionResult.PASS;

        BlockPos crystalPos = hit.getBlockPos().above();
        for (Pending existing : PENDING) {
            if (existing.pos.equals(crystalPos)) {
                existing.expiresAt = tickCounter + windowTicks;
                return InteractionResult.PASS;
            }
        }
        PENDING.add(new Pending(crystalPos, tickCounter + windowTicks));
        return InteractionResult.PASS;
    }

    private static void onEndTick(Minecraft client) {
        tickCounter++;
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            saveConfig();
            if (!enabled) {
                PENDING.clear();
                lastPlacementAttemptTick = Integer.MIN_VALUE;
            }
        }

        LocalPlayer player = client.player;
        if (!enabled || player == null || client.level == null || client.gameMode == null) {
            PENDING.clear();
            return;
        }

        PENDING.removeIf(pending -> tickCounter > pending.expiresAt);

        // Break only the crystal at a position recorded by our own placement callback.
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending pending = PENDING.get(i);
            EndCrystal crystal = findCrystalAt(client, pending.pos);
            if (crystal == null || player.distanceTo(crystal) > attackRange) continue;

            client.gameMode.attack(player, crystal);
            PENDING.remove(i);
        }

        // RMB is the repeat control: place again only after the previous owned crystal
        // has been found and attacked. This avoids placing into an occupied crystal space.
        if (client.options.keyUse.isDown() && PENDING.isEmpty()) {
            tryPlaceNextCrystal(client, player);
        }
    }

    private static void tryPlaceNextCrystal(Minecraft client, LocalPlayer player) {
        if (tickCounter - lastPlacementAttemptTick < 2) return;
        if (!(client.hitResult instanceof BlockHitResult hit)) return;

        BlockState clicked = client.level.getBlockState(hit.getBlockPos());
        if (!clicked.is(Blocks.OBSIDIAN) && !clicked.is(Blocks.BEDROCK)) return;

        InteractionHand hand;
        if (player.getMainHandItem().is(Items.END_CRYSTAL)) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            return;
        }

        BlockPos crystalPos = hit.getBlockPos().above();
        if (findCrystalAt(client, crystalPos) != null) return;

        lastPlacementAttemptTick = tickCounter;
        client.gameMode.useItemOn(player, hand, hit);
    }

    private static EndCrystal findCrystalAt(Minecraft client, BlockPos pos) {
        AABB search = new AABB(pos).inflate(1.0);
        for (EndCrystal crystal : client.level.getEntitiesOfClass(EndCrystal.class, search)) {
            if (crystal.isAlive() && crystal.blockPosition().equals(pos)) return crystal;
        }
        return null;
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
            windowTicks = integer(properties, "windowTicks", 10, 1, 40);
            attackRange = decimal(properties, "attackRange", 3.0, 1.0, 6.0);
            swing = bool(properties, "swing", true);
            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/CrystalMacro] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("windowTicks", Integer.toString(windowTicks));
        properties.setProperty("attackRange", Double.toString(attackRange));
        properties.setProperty("swing", Boolean.toString(swing));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                properties.store(output, "QYS-UTILS Crystal Macro settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/CrystalMacro] Config save failed: " + exception.getMessage());
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

    private static final class Pending {
        final BlockPos pos;
        int expiresAt;

        Pending(BlockPos pos, int expiresAt) {
            this.pos = pos;
            this.expiresAt = expiresAt;
        }
    }
}
