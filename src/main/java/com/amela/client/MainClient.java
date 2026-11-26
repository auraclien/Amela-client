package com.amela.client;

import com.amela.client.gui.AmelaScreen;
import com.amela.client.module.ModuleManager;
import com.amela.client.module.SilentRotation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public class MainClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();

    // ===== Polar профиль =====
    private static final double ATTACK_RANGE = 3.0;   // строгий reach
    private static final double KEEP_RANGE = 3.6;
    private static final float ALIGN_DEG = 4.0f;      // точное наведение перед ударом

    private static LivingEntity target;
    private static boolean aiming;
    private static float sYaw, sPitch;
    private static float rotSpeed;                    // текущая скорость доворота (разгон/торможение)
    private static long lastAttackMs, lastClickMs;
    private static int attackCount;

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

        ClientTickEvents.START_CLIENT_TICK.register(MainClient::updateAim);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) client.setScreen(new AmelaScreen());
            tickAutoSprint(client);
            tickFullbright(client);
            tickAttack(client);
        });
    }

    private static void updateAim(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) { resetAim(); return; }

        LivingEntity t = null;
        if (MODULES.isKillAura() && client.currentScreen == null && isWeapon(player.getMainHandStack())) {
            t = findTarget(player);
        }
        target = t;

        if (t == null) {
            if (aiming) {
                float dy = MathHelper.wrapDegrees(player.getYaw() - sYaw);
                float dp = player.getPitch() - sPitch;
                if (Math.abs(dy) < 0.5f && Math.abs(dp) < 0.5f) resetAim();
                else {
                    float len = (float) Math.hypot(dy, dp);
                    float scale = Math.min(len, rand(15.6f, 48.0f)) / len;
                    sYaw += dy * scale;
                    sPitch = MathHelper.clamp(sPitch + dp * scale, -89.0f, 90.0f);
                    SilentRotation.set(sYaw, sPitch);
                }
            }
            return;
        }

        if (!aiming) { sYaw = player.getYaw(); sPitch = player.getPitch(); rotSpeed = 0; aiming = true; }

        Vec3d eye = player.getEyePos();
        Box box = t.getBoundingBox();
        Vec3d aim = box.raycast(eye, box.getCenter()).orElse(box.getCenter());
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        float yawDelta = MathHelper.wrapDegrees(wantYaw - sYaw);
        float pitchDelta = wantPitch - sPitch;
        float len = (float) Math.hypot(yawDelta, pitchDelta);
        if (len > 1.0E-4f) {
            // «живая» скорость: разгон на дистанции, торможение у цели, без константы
            float desired = Math.min(rand(26.0f, 40.0f), len * 0.35f + 4.0f);
            rotSpeed += (desired - rotSpeed) * 0.4f;
            rotSpeed *= rand(0.85f, 1.15f);
            float step = Math.min(len, rotSpeed);
            if (len > 20.0f && ThreadLocalRandom.current().nextInt(14) == 0) step = 0; // микро-пауза

            float scale = step / len;
            float dY = yawDelta * scale, dP = pitchDelta * scale;

            // GCD под сенсу — углы кратны шагу мыши (Polar это проверяет)
            double sens = client.options.getMouseSensitivity().getValue();
            double f = sens * 0.6 + 0.2;
            double gcd = f * f * f * 8.0 * 0.15;
            dY = (float) (Math.round(dY / gcd) * gcd);
            dP = (float) (Math.round(dP / gcd) * gcd);

            // джиттер в целых GCD у цели
            if (len < 6.0f) {
                dY += gcd * ThreadLocalRandom.current().nextInt(-1, 2);
                dP += gcd * ThreadLocalRandom.current().nextInt(-1, 2);
            }

            sYaw += dY;
            sPitch = MathHelper.clamp(sPitch + dP, -89.0f, 90.0f);
        }
        SilentRotation.set(sYaw, sPitch);
    }

    private static void tickAttack(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || target == null || !aiming) return;
        if (client.currentScreen != null || !isWeapon(player.getMainHandStack())) return;
        if (player.isUsingItem()) return;

        Vec3d eye = player.getEyePos();
        Box box = target.getBoundingBox();

        double cx = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double cy = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double cz = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        if (eye.squaredDistanceTo(cx, cy, cz) > ATTACK_RANGE * ATTACK_RANGE) return;

        // наведение должно быть точным
        Vec3d look = lookVec(sYaw, sPitch);
        Vec3d aim = box.raycast(eye, box.getCenter()).orElse(box.getCenter());
        float needYaw = (float) Math.toDegrees(Math.atan2(aim.z - eye.z, aim.x - eye.x)) - 90.0f;
        if (Math.abs(MathHelper.wrapDegrees(needYaw - sYaw)) > ALIGN_DEG) return;
        // луч серверной ротации обязан попасть в реальный хитбокс (без expand)
        if (!box.contains(eye) && box.raycast(eye, eye.add(look.multiply(ATTACK_RANGE))).isEmpty()) return;

        HitResult hit = player.getEntityWorld().raycast(new RaycastContext(
                eye, aim, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) return;

        long now = System.currentTimeMillis();
        double speed = player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
        if (!Double.isFinite(speed) || speed < 0.001) speed = 4.0;

        long delay = 400 + ThreadLocalRandom.current().nextInt(0, 120); // 400–520 мс
        boolean ready = now - lastAttackMs >= delay
                && now - lastClickMs >= (10 + (attackCount % 2)) * 50L
                && now - lastClickMs >= (long) (1000.0 / speed)
                && player.getAttackCooldownProgress(0.0f) >= 1.0f;

        float requiredFall = rand(0.05f, 0.3f); // рандомный крит, без цикла-паттерна
        boolean crit = !player.isOnGround()
                && player.getVelocity().y < -0.001
                && player.fallDistance > requiredFall
                && !player.isClimbing() && !player.isTouchingWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS) && !player.hasVehicle();

        if (ready && crit) {
            if (player.isSprinting()) player.setSprinting(false);
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            lastAttackMs = now; lastClickMs = now; attackCount++;
        } else if (!ready && player.getAttackCooldownProgress(0.0f) > 0.7f
                && player.isOnGround() && !player.isTouchingWater()
                && !player.isClimbing() && !player.hasVehicle()) {
            player.jump();
        }
    }

    private static LivingEntity findTarget(ClientPlayerEntity player) {
        if (target != null && isValid(target) && player.squaredDistanceTo(target) <= KEEP_RANGE * KEEP_RANGE) return target;
        LivingEntity best = null; double bd = ATTACK_RANGE * ATTACK_RANGE;
        for (LivingEntity e : player.getEntityWorld().getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(ATTACK_RANGE), MainClient::isValid)) {
            double d = player.squaredDistanceTo(e);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static boolean isValid(LivingEntity e) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (e == c.player || e instanceof ArmorStandEntity) return false;
        if (!e.isAlive() || e.getHealth() <= 0 || !e.isAttackable() || e.isInvulnerable()) return false;
        if (e instanceof PlayerEntity p && (p.isSpectator() || p.isCreative())) return false;
        return true;
    }

    private static boolean isWeapon(ItemStack hand) {
        return hand.isIn(ItemTags.SWORDS) || hand.isIn(ItemTags.AXES) || hand.isOf(Items.MACE);
    }

    private static Vec3d lookVec(float yaw, float pitch) {
        float f = pitch * 0.017453292f, g = -yaw * 0.017453292f;
        float h = MathHelper.cos(g), i = MathHelper.sin(g);
        float j = MathHelper.cos(f), k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    private static void resetAim() { aiming = false; target = null; rotSpeed = 0; SilentRotation.disable(); }
    private static float rand(float min, float max) { return MathHelper.lerp(ThreadLocalRandom.current().nextFloat(), min, max); }

    private static void tickAutoSprint(MinecraftClient client) {
        if (!MODULES.isAutoSprint()) return;
        ClientPlayerEntity p = client.player;
        if (p == null || client.currentScreen != null) return;
        if (client.options.forwardKey.isPressed() && !p.horizontalCollision && !p.isSneaking()
                && p.getHungerManager().getFoodLevel() > 6.0f) p.setSprinting(true);
    }

    private static void tickFullbright(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        boolean on = MODULES.isFullbright();
        if (p == null) { lastFullbright = on; return; }
        if (on) {
            StatusEffectInstance nv = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 320)
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
        } else if (lastFullbright) p.removeStatusEffect(StatusEffects.NIGHT_VISION);
        lastFullbright = on;
    }
}
