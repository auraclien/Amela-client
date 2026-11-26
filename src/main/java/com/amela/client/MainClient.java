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

    private static final double KA_RANGE = 3.0;
    private static final SecureRandom KA_RANDOM = new SecureRandom();
    private static final float AURA_ATTACK_DELAY_MS = 400.0f;

    // Silent aim состояние
    private static float sYaw, sPitch;
    private static int sTargetId = -1;
    private static StopWatch attackTimer = new StopWatch();
    private static ClickGate clickGate = new ClickGate();
    private static int attackCount = 0;

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
            return;
        }

        // только оружие
        ItemStack hand = player.getMainHandStack();
        if (!(hand.isIn(ItemTags.SWORDS) || hand.isIn(ItemTags.AXES) || hand.isOf(Items.MACE))) {
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
            return;
        }

        // расчёт цели для silent aim
        double dx = target.getX() - player.getX();
        double dy = (target.getY() + target.getHeight() * 0.5) - player.getEyeY();
        double dz = target.getZ() - player.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // сглаженная silent-ротация (Spooky-стиль)
        if (sTargetId != target.getId()) {
            sTargetId = target.getId();
            sYaw = player.getYaw();
            sPitch = player.getPitch();
        }

        float yawDelta = MathHelper.wrapDegrees(aimYaw - sYaw);
        float pitchDelta = aimPitch - sPitch;
        float len = (float) Math.hypot(yawDelta, pitchDelta);
        if (len > 1.0E-4f) {
            float maxStep = kaRand(15.6f, 24.0f) * kaRand(1.0f, 2.0f);
            float scale = Math.min(len, maxStep) / len;
            sYaw += yawDelta * scale;
            sPitch = MathHelper.clamp(sPitch + pitchDelta * scale, -89.0f, 90.0f);
        }

        // проверка условий атаки
        if (player.getAttackCooldownProgress(0.0f) < 1.0f) return;
        if (Math.abs(MathHelper.wrapDegrees(aimYaw - sYaw)) > 10.0f) return;
        if (Math.abs(aimPitch - sPitch) > 10.0f) return;
        if (!hasLineOfSight(player, target)) return;

        // тайминг
        if (!attackTimer.hasElapsed(AURA_ATTACK_DELAY_MS)) return;

        // проверка через ClickGate
        if (!clickGate.canAttack(attackCount, 0.82f, false)) return;

        // крит
        boolean canCritNow = canCrit(player);

        // ОТПРАВКА ПАКЕТА ПОВОРОТА СЕРВЕРУ (камера не трогается!)
        sendSilentLook(client, player, sYaw, sPitch);

        // атака
        if (canCritNow) {
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            attackTimer.reset();
            attackCount++;
            clickGate.registerClick(attackCount);
        }
    }

    private static void sendSilentLook(MinecraftClient client, ClientPlayerEntity player, float yaw, float pitch) {
        if (client.getNetworkHandler() == null) return;
        // отправляем пакет поворота на сервер, но не меняем локальную камеру
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
    }

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

    // Внутренние классы
    private static class StopWatch {
        private long last = System.currentTimeMillis();

        public void reset() {
            last = System.currentTimeMillis();
        }

        public long elapsed() {
            return System.currentTimeMillis() - last;
        }

        public boolean hasElapsed(double delayMs) {
            return elapsed() >= delayMs;
        }
    }

    private static class ClickGate {
        private static final int[] defaultTicks = {10, 11};
        private long lastClickTime = System.currentTimeMillis();

        public boolean canAttack(int attackCount, float minimumCooldown, boolean tpsSync) {
            long elapsedMs = System.currentTimeMillis() - lastClickTime;
            float cooldown = MathHelper.clamp(minimumCooldown, 0.0f, 1.0f);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return false;

            double attackSpeed = Math.max(0.001, client.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ATTACK_SPEED));
            if (!Double.isFinite(attackSpeed)) attackSpeed = 4.0;

            long requiredMs = (long) (1000.0 / attackSpeed * cooldown * (20.0 / (tpsSync ? 20.0f : 20.0f)));

            if (elapsedMs >= requiredMs) {
                int delayTicks = defaultTicks[attackCount % defaultTicks.length];
                float tickDelay = delayTicks * (20.0f / (tpsSync ? 20.0f : 20.0f));
                float cooldownProgress = client.player.getAttackCooldownProgress(tickDelay);
                return cooldownProgress >= cooldown;
            }
            return false;
        }

        public void registerClick(int count) {
            lastClickTime = System.currentTimeMillis();
        }
    }
}
