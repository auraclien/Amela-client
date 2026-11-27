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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class MainClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();

    private static final double ATTACK_RANGE = 3.0;
    private static final double RAY_RANGE = ATTACK_RANGE - 0.25;
    private static final double KEEP_RANGE = 3.6;

    private static LivingEntity target;
    private static boolean aiming;
    private static float sYaw, sPitch, rotSpeed;
    private static long lastAttackMs, lastClickMs;
    private static int attackCount;

    private static KeyBinding menuKey;
    private static boolean lastFullbright = false;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("amela-client", "general"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.amela-client.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        ClientTickEvents.START_CLIENT_TICK.register(MainClient::preTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) client.setScreen(new AmelaScreen());
            tickAutoSprint(client);
            tickFullbright(client);
            tickAttack(client);
        });
    }

    private static double gcd(MinecraftClient client) {
        double s = client.options.getMouseSensitivity().getValue();
        return Math.pow(s * 0.6 + 0.2, 3.0) * 1.2;
    }

    private static void preTick(MinecraftClient client) {
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
            float desired = Math.min(rand(30.0f, 45.0f), len * 0.5f + 6.0f);
            rotSpeed += (desired - rotSpeed) * 0.45f;
            rotSpeed *= rand(0.85f, 1.15f);
            float step = Math.min(len, rotSpeed);
            if (len > 20.0f && ThreadLocalRandom.current().nextInt(14) == 0) step = 0;
            float scale = step / len;
            float g = (float) gcd(client);
            float dY = (float) (Math.round(yawDelta * scale / g) * g);
            float dP = (float) (Math.round(pitchDelta * scale / g) * g);
            if (len < 6.0f) {
                dY += g * ThreadLocalRandom.current().nextInt(-1, 2);
                dP += g * ThreadLocalRandom.current().nextInt(-1, 2);
            }
            sYaw += dY;
            sPitch = MathHelper.clamp(sPitch + dP, -89.0f, 90.0f);
        }
        SilentRotation.set(sYaw, sPitch);

        // прыжок: ванильный player.jump(), ванилла сама обновит input
        float cd = player.getAttackCooldownProgress(0.0f);
        if (player.isOnGround() && !player.isTouchingWater() && !player.isClimbing()
                && !player.hasVehicle() && player.squaredDistanceTo(t) <= ATTACK_RANGE * ATTACK_RANGE) {
            float ticksLeft = (1.0f - cd) * 20.0f / (float) attackSpeed(player);
            if (ticksLeft <= 7.0f) player.jump();
        }
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

        Vec3d dir = lookVec(SilentRotation.getSentYaw(), SilentRotation.getSentPitch());
        Vec3d end = eye.add(dir.multiply(RAY_RANGE));
        Optional<Vec3d> hit = box.contains(eye) ? Optional.of(eye) : box.raycast(eye, end);
        if (hit.isEmpty()) return;

        BlockHitResult blockHit = player.getEntityWorld().raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            if (eye.squaredDistanceTo(blockHit.getPos()) + 1.0E-4 >= eye.squaredDistanceTo(hit.get())) return;
        }

        long now = System.currentTimeMillis();
        boolean ready = now - lastAttackMs >= 400 + ThreadLocalRandom.current().nextInt(0, 120)
                && now - lastClickMs >= (10 + (attackCount % 2)) * 50L
                && now - lastClickMs >= (long) (1000.0 / attackSpeed(player))
                && player.getAttackCooldownProgress(0.0f) >= 1.0f;
        if (!ready) return;

        // ванильный крит: падение + velocity вниз
        boolean crit = player.fallDistance > 0.0f
                && !player.isOnGround()
                && player.getVelocity().y < 0.0
                && !player.isSprinting()
                && !player.isClimbing() && !player.isTouchingWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS) && !player.hasVehicle();
        if (!crit) return;

        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        lastAttackMs = now; lastClickMs = now; attackCount++;
    }

    private static double attackSpeed(ClientPlayerEntity p) {
        double s = p.getAttributeValue(EntityAttributes.ATTACK_SPEED);
        return (!Double.isFinite(s) || s < 0.001) ? 4.0 : s;
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
