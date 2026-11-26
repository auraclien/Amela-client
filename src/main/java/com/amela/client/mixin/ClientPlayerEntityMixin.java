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
    @Unique private float amela$yaw, amela$pitch;
    @Unique private float amela$pYaw, amela$pPitch;
    @Unique private boolean amela$swapped;

    // физика тика считается от серверного yaw → симуляция AC совпадает
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

    // камеру возвращаем; голову/тело НЕ трогаем — они остаются на прицеле (визуальный таргет)
    @Inject(method = "tick", at = @At("RETURN"))
    private void amela$post(CallbackInfo ci) {
        if (!amela$swapped) return;
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        self.setYaw(amela$yaw); self.setPitch(amela$pitch);
        self.lastYaw = amela$pYaw; self.lastPitch = amela$pPitch;
        amela$swapped = false;
    }
}
