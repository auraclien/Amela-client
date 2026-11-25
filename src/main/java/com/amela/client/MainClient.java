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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class MainClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();

    private static final double KA_RANGE = 3.0;   // серверный reach, не 4+
    private static final float KA_STEP = 35.0f;   // плавный доворот, град/тик
    private static final float KA_ALIGN = 12.0f;  // допуск наведения для удара

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

        // только оружие в основной руке: меч / топор / булава
        ItemStack hand = player.getMainHandStack();
        if (!(hand.isIn(ItemTags.SWORDS) || hand.isIn(ItemTags.AXES) || hand.isOf(Items.MACE))) return;

        LivingEntity target = null;
        double best = KA_RANGE * KA_RANGE;
        for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(KA_RANGE), MainClient::isValidTarget)) {
            double d = player.squaredDistanceTo(e);
            if (d < best) { best = d; target = e; }
        }
        if (target == null) return;

        // плавное наведение
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

        // удар только с полным кулдауном, точным наведением и без стен
        if (player.getAttackCooldownProgress(0.0f) < 1.0f) return;
        if (Math.abs(MathHelper.wrapDegrees(yaw - newYaw)) > KA_ALIGN) return;
        if (!player.canSee(target)) return;

        if (canCrit(player)) {
            player.setSprinting(false); // спринт-удар не критует
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
        } else if (player.isOnGround() && !player.isTouchingWater() && !player.isClimbing() && !player.hasVehicle()) {
            player.jump(); // прыжок под крит, удар на падении
        }
    }

    // ванильные условия крита
    private static boolean canCrit(ClientPlayerEntity p) {
        return !p.isOnGround()
                && p.fallDistance > 0.0f
                && p.getVelocity().y < 0.0
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle();
    }

    private static boolean isValidTarget(LivingEntity e) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (e == c.player || e instanceof ArmorStandEntity) return false;
        if (!e.isAlive() || !e.isAttackable() || e.isInvulnerable()) return false;
        if (e instanceof PlayerEntity p && (p.isSpectator() || p.isCreative())) return false;
        return true;
    }
}
