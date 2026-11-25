package com.amela.client;

import com.amela.client.gui.AmelaScreen;
import com.amela.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class MainClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();

    private static final double KA_RANGE = 4.0;
    private static final float KA_STEP = 45.0f; // макс. доворот за тик, град

    private static KeyBinding menuKey;
    private static boolean lastFullbright = false;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("amela-client", "general"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.amela-client.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                client.setScreen(new AmelaScreen());
            }
            tickAutoSprint(client);
            tickFullbright(client);
            tickKillAura(client);
        });
    }

    private static void tickAutoSprint(MinecraftClient client) {
        if (!MODULES.isAutoSprint()) return;
        ClientPlayerEntity player = client.player;
        if (player == null || client.currentScreen != null) return;

        if (client.options.forwardKey.isPressed()
                && !player.horizontalCollision
                && !player.isSneaking()
                && player.getHungerManager().getFoodLevel() > 6.0f) {
            player.setSprinting(true);
        }
    }

    private static void tickFullbright(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        boolean on = MODULES.isFullbright();
        if (player == null) { lastFullbright = on; return; }

        if (on) {
            StatusEffectInstance nv = player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 320) {
                // без частиц и без иконки в HUD
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
            }
        } else if (lastFullbright) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        lastFullbright = on;
    }

    private static void tickKillAura(MinecraftClient client) {
        if (!MODULES.isKillAura()) return;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.currentScreen != null) return;

        LivingEntity target = null;
        double best = KA_RANGE * KA_RANGE;
        for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(KA_RANGE), MainClient::isValidTarget)) {
            double d = player.squaredDistanceTo(e);
            if (d < best) { best = d; target = e; }
        }
        if (target == null) return;

        double dx = target.getX() - player.getX();
        double dy = (target.getY() + target.getHeight() * 0.5) - player.getEyeY();
        double dz = target.getZ() - player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        float newYaw = player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - player.getYaw()), -KA_STEP, KA_STEP);
        float newPitch = MathHelper.clamp(player.getPitch()
                + MathHelper.clamp(MathHelper.wrapDegrees(pitch - player.getPitch()), -KA_STEP, KA_STEP), -90.0f, 90.0f);
        player.setYaw(newYaw);
        player.setPitch(newPitch);
        player.headYaw = newYaw;

        // бьём только когда уже смотрим на цель и кулдаун полный
        if (player.getAttackCooldownProgress(0.0f) >= 1.0f
                && Math.abs(MathHelper.wrapDegrees(yaw - newYaw)) < 30.0f) {
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
        }
    }

    private static boolean isValidTarget(LivingEntity e) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (e == c.player || !e.isAlive() || !e.isAttackable() || e.isInvulnerable()) return false;
        if (e instanceof PlayerEntity p && (p.isSpectator() || p.isCreative())) return false;
        return true;
    }
}
