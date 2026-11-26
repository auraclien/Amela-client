package com.amela.client.mixin;

import com.amela.client.module.SilentRotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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
    private void amela$silentRotation(Packet<?> packet, CallbackInfo ci) {
        if (!SilentRotation.isActive() || !(packet instanceof PlayerMoveC2SPacket move)) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

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
    }
}
