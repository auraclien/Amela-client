package com.amela.client.mixin;

import com.amela.client.module.SilentRotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
    @Shadow @Final protected ClientConnection connection;

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void amela$rewrite(Packet<?> packet, CallbackInfo ci) {
        if (!SilentRotation.isActive()) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        if (packet instanceof PlayerMoveC2SPacket move) {
            float yaw = SilentRotation.getYaw();
            float pitch = SilentRotation.getPitch();
            PlayerMoveC2SPacket out;
            if (move.changesPosition()) {
                out = new PlayerMoveC2SPacket.Full(
                        move.getX(player.getX()), move.getY(player.getY()), move.getZ(player.getZ()),
                        yaw, pitch, move.isOnGround(), move.horizontalCollision());
            } else {
                out = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, move.isOnGround(), move.horizontalCollision());
            }
            ci.cancel();
            SilentRotation.markSent(yaw, pitch);
            this.connection.send(out);
            return;
        }

        // порт AuraMovementCorrection: поворачиваем input, чтобы симуляция сервера
        // по serverYaw дала то же движение, что реально идёт по cameraYaw
        if (packet instanceof PlayerInputC2SPacket inputPacket) {
            PlayerInput in = inputPacket.input();
            float delta = MathHelper.wrapDegrees(player.getYaw() - SilentRotation.getYaw());
            if (Math.abs(delta) < 0.01f) return;

            float forward = axis(in.forward(), in.backward());
            float strafe = axis(in.left(), in.right());
            float rad = delta * 0.017453292f;
            float cos = MathHelper.cos(rad);
            float sin = MathHelper.sin(rad);
            int newStrafe = Math.round(strafe * cos - forward * sin);
            int newForward = Math.round(forward * cos + strafe * sin);
            boolean sprint = in.sprint() && newForward > 0;

            ci.cancel();
            this.connection.send(new PlayerInputC2SPacket(new PlayerInput(
                    newForward > 0, newForward < 0, newStrafe > 0, newStrafe < 0,
                    in.jump(), in.sneak(), sprint)));
        }
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0f;
        return positive ? 1.0f : -1.0f;
    }
}
