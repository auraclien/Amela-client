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
    @Unique private float amela$yaw, amela$pitch, amela$head, amela$body;
    @Unique private float amela$pYaw, amela$pPitch, amela$pHead, amela$pBody;
    @Unique private boolean amela$swapped;

    // на время тика считаем физику от серверного yaw → симуляция Polar совпадает 1:1
    @Inject(method = "tick", at = @At("HEAD"))
    private void amela$pre(CallbackInfo ci) {
        if (!SilentRotation.isActive()) return;
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        amela$yaw = self.getYaw(); amela$pitch = self.getPitch();
        amela$head = self.headYaw; amela$body = self.bodyYaw;
        amela$pYaw = self.lastYaw; amela$pPitch = self.lastPitch;
        amela$pHead = self.lastHeadYaw; amela$pBody = self.lastBodyYaw;

        float y = SilentRotation.getYaw(), p = SilentRotation.getPitch();
        self.setYaw(y); self.setPitch(p);
        self.headYaw = y; self.bodyYaw = y;
        amela$swapped = true;
    }

    // возвращаем камеру и last-поля, чтобы рендер не дёргался
    @Inject(method = "tick", at = @At("RETURN"))
    private void amela$post(CallbackInfo ci) {
        if (!amela$swapped) return;
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        self.setYaw(amela$yaw); self.setPitch(amela$pitch);
        self.headYaw = amela$head; self.bodyYaw = amela$body;
        self.lastYaw = amela$pYaw; self.lastPitch = amela$pPitch;
        self.lastHeadYaw = amela$pHead; self.lastBodyYaw = amela$pBody;
        amela$swapped = false;
    }
}
