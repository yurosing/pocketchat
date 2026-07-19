package com.pmchat.mixin;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmMedia;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NEW (1.7.9): делает полоску аудиоплеера кликабельной поверх ванильного чата
 * (опция «Полоска плеера при вводе»). Без этого HUD-полоса рисовалась над
 * ChatScreen, но клики по её кнопкам/перемотке не доходили до плеера.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void pmchat$mediaBarClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!PmChatClient.getConfig().mediaBarWhileTyping) return;
        PmMedia media = PmMedia.get();
        if (media.hasActive() && media.isMinimized()
                && media.handleMiniClick((int) click.x(), (int) click.y())) {
            cir.setReturnValue(true);
        }
    }
}
