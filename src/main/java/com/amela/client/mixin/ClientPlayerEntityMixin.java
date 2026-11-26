package com.amela.client.mixin;

import com.amela.client.module.SilentRotation;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Unique private float amela$yaw, amela$pitch, amela$pYaw, amela$pPitch;
    @Unique private boolean amela$swapped;

    @Inject(method = "tick", at = @At("HEAD"))
    private void amela$pre(CallbackInfo ci) {
        if (!SilentRotation.isActive()) return;
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        amela$yaw = self.getYaw(); amela$pitch = self.getPitch();
        amela$pYaw = self.lastYaw; amela$pPitch = self.lastPitch;
        self.setYaw(SilentRotation.getYaw());
        self.setPitch(SilentRotation.getPitch());
        amela$swapped = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void amela$post(CallbackInfo ci) {
        if (!amela$swapped) return;
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        float sy = SilentRotation.getYaw();

        // камера свободна
        self.setYaw(amela$yaw); self.setPitch(amela$pitch);
        self.lastYaw = amela$pYaw; self.lastPitch = amela$pPitch;

        // тело/голова и их last-поля — одним значением, без интерполяционного рывка
        self.headYaw = sy; self.lastHeadYaw = sy;
        self.bodyYaw = sy; self.lastBodyYaw = sy;

        amela$swapped = false;
    }
}
