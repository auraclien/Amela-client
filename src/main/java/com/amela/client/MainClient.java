package com.amela.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MainClient implements ClientModInitializer {
    public static KeyBinding menuKey;
    public static boolean isMenuOpen = false;

    @Override
    public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.amela.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.amela.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (menuKey.wasPressed()) {
                isMenuOpen = !isMenuOpen;
                if (isMenuOpen) {
                    client.setScreen(new AmelaScreen());
                } else if (client.currentScreen instanceof AmelaScreen) {
                    client.setScreen(null);
                }
            }

            // Логика AutoSprint
            if (ModuleManager.autoSprintEnabled && client.player != null) {
                if (client.options.forwardKey.isPressed()) {
                    client.player.setSprinting(true);
                }
            }
        });
    }
}
