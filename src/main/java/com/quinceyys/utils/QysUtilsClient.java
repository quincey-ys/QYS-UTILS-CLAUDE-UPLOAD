package com.quinceyys.utils;

import net.fabricmc.api.ClientModInitializer;

public final class QysUtilsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutoTotemModule.initialize();
        MaceHitterModule.initialize();
        System.out.println("[QYS-UTILS] AutoTotem and Mace Hitter initialized.");
    }
}
