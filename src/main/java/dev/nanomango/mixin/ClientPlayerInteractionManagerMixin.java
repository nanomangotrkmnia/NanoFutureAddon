package dev.nanomango.mixin;

import dev.nanomango.modules.KeepMining;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void onCancelBlockBreaking(CallbackInfo info) {
        Modules modules = Modules.get();
        if (modules == null) return;

        KeepMining keepMining = modules.get(KeepMining.class);
        if (keepMining != null && keepMining.shouldKeepBreaking()) info.cancel();
    }
}
