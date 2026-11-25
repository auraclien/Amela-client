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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.security.SecureRandom;

public class MainClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();

    private static final double KA_RANGE = 3.0;   // серверный reach
    private static final float KA_ALIGN = 10.0f;  // допуск наведения для удара
    private static final SecureRandom KA_RANDOM = new SecureRandom();

    // silent aim: ротация, которую видит сервер (камера не трогается)
    private static float sYaw, sPitch;
    private static int sTargetId = -1;
    private static boolean sSynced = true; // серверная ротация = камера

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
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.currentScreen != null || !MODULES.isKillAura()) {
            resyncLook(client, player);
            return;
        }

        // только оружие в основной руке: меч / топор / булава
        ItemStack hand = player.getMainHandStack();
        if (!(hand.isIn(ItemTags.SWORDS) || hand.isIn(ItemTags.AXES) || hand.isOf(Items.MACE))) {
            resyncLook(client, player);
            return;
        }

        LivingEntity target = null;
        double best = KA_RANGE * KA_RANGE;
        for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(KA_RANGE), MainClient::isValidTarget)) {
            double d = player.squaredDistanceTo(e);
            if (d < best) { best = d; target = e; }
        }
        if (target == null) {
            resyncLook(client, player);
            return;
        }

        // старт silent-ротации с текущей камеры при смене цели
        if (sTargetId != target.getId()) {
            sTargetId = target.getId();
            sYaw = player.getYaw();
            sPitch = player.getPitch();
        }

        // цель
        double dx = target.getX() - player.getX();
        double dy = (target.getY() + target.getHeight() * 0.5) - player.getEyeY();
        double dz = target.getZ() - player.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // сглаженная silent-ротация (Spooky-стиль: случайная скорость)
        float yawDelta = MathHelper.wrapDegrees(aimYaw - sYaw);
        float pitchDelta = aimPitch - sPitch;
        float len = (float) Math.hypot(yawDelta, pitchDelta);
        if (len > 1.0E-4f) {
            float maxStep = kaRand(15.6f, 24.0f) * kaRand(1.0f, 2.0f);
            float scale = Math.min(len, maxStep) / len;
            sYaw += yawDelta * scale;
            sPitch = MathHelper.clamp(sPitch + pitchDelta * scale, -89.0f, 90.0f);
        }

        // сервер видит silent-ротацию, камера игрока не меняется
        sendLook(client, player, sYaw, sPitch);
        sSynced = false;

        // удар: полный кулдаун + наведение silent-ротации + прямая видимость
        if (player.getAttackCooldownProgress(0.0f) < 1.0f) return;
        if (Math.abs(MathHelper.wrapDegrees(aimYaw - sYaw)) > KA_ALIGN) return;
        if (Math.abs(aimPitch - sPitch) > KA_ALIGN) return;
        if (!hasLineOfSight(player, target)) return;

        if (canCrit(player)) {
            player.setSprinting(false); // спринт-удар не критует
            sendLook(client, player, sYaw, sPitch); // пакет поворота строго перед атакой
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
        } else if (player.isOnGround() && !player.isTouchingWater() && !player.isClimbing() && !player.hasVehicle()) {
            player.jump(); // прыжок под крит, удар на падении
        }
    }

    // вернуть серверную ротацию к камере, когда аура не работает
    private static void resyncLook(MinecraftClient client, ClientPlayerEntity player) {
        if (sSynced || player == null || client.getNetworkHandler() == null) return;
        sendLook(client, player, player.getYaw(), player.getPitch());
        sSynced = true;
        sTargetId = -1;
    }

    private static void sendLook(MinecraftClient client, ClientPlayerEntity player, float yaw, float pitch) {
        if (client.getNetworkHandler() == null) return;
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
    }

    // проверка стен без учёта камеры
    private static boolean hasLineOfSight(ClientPlayerEntity player, LivingEntity target) {
        Vec3d from = player.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter();
        HitResult hit = player.getEntityWorld().raycast(new RaycastContext(
                from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static float kaRand(float min, float max) {
        return MathHelper.lerp(KA_RANDOM.nextFloat(), min, max);
    }

    // ванильные условия крита
    private static boolean canCrit(ClientPlayerEntity p) {
        return !p.isOnGround()
                && p.fallDistance > 0.0
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
