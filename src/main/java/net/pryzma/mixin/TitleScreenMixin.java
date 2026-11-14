package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.gui.widget.ModsButton;
import net.pryzma.PryzmaConfig;

/**
 * REMOVE_REALMS. The Realms button never enters the screen, NeoForge's Mods button takes its row
 * and the bottom row gives back the 22 px NeoForge added for the Mods button: the menu keeps the
 * vanilla proportions. With the option off the screen is NeoForge's, unchanged.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    /** Set when this {@code init()} dropped the Realms button; only then is the layout compacted. */
    @Unique
    private boolean pryzma$realmsRemoved;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /** Singleplayer and Multiplayer pass through; the Realms button is returned without being added. */
    @WrapOperation(method = "createNormalMenuOptions(II)I", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener prFilterRealmsButton(TitleScreen screen, GuiEventListener widget, Operation<GuiEventListener> original) {
        if (PryzmaConfig.prRemoveRealms && widget instanceof AbstractWidget button
                && button.getMessage().getContents() instanceof TranslatableContents contents
                && "menu.online".equals(contents.getKey())) {
            this.pryzma$realmsRemoved = true;
            return widget;
        }
        return original.call(screen, widget);
    }

    /**
     * Not built at all: its field initializer starts {@code RealmsAvailability.get()}, a request to
     * the Realms service that logs "Couldn't connect to realms" when offline. The field stays null,
     * a state every {@link TitleScreen} path already handles.
     */
    @WrapOperation(method = "init()V", at = @At(value = "NEW",
            target = "()Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;"))
    private RealmsNotificationsScreen prSkipRealmsNotifications(Operation<RealmsNotificationsScreen> original) {
        return PryzmaConfig.prRemoveRealms ? null : original.call();
    }

    /** Covers a notifications screen built while the option was off: no tick, click or icon. */
    @Inject(method = "realmsNotificationsEnabled()Z", at = @At("HEAD"), cancellable = true)
    private void prDisableRealmsNotifications(CallbackInfoReturnable<Boolean> cir) {
        if (PryzmaConfig.prRemoveRealms) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Runs before {@code ScreenEvent.Init.Post}, so buttons other mods add there see the final
     * layout. Demo menus never drop Realms, so they are left alone.
     * In 26.3, NeoForge places ModsButton (20x20) in the 4-icon row alongside Friends, Language,
     * and Accessibility. When Realms is removed, the entire lower block (the 4 icon buttons and
     * Options/Quit) shifts up by rowHeight (24 px) to fill the vacant Realms slot cleanly.
     */
    @Inject(method = "init()V", at = @At("RETURN"))
    private void prAdjustTitleScreenLayout(CallbackInfo ci) {
        if (!this.pryzma$realmsRemoved) {
            return;
        }
        this.pryzma$realmsRemoved = false;
        int rowHeight = 24;

        AbstractWidget multiplayerButton = null;
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget
                    && widget.getMessage().getContents() instanceof TranslatableContents contents
                    && "menu.multiplayer".equals(contents.getKey())) {
                multiplayerButton = widget;
                break;
            }
        }
        if (multiplayerButton == null) {
            return;
        }

        int multiplayerY = multiplayerButton.getY();
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && !(widget instanceof PlainTextButton)) {
                if (widget.getY() > multiplayerY && widget.getY() < this.height - 20) {
                    widget.setY(widget.getY() - rowHeight);
                }
            }
        }
    }
}
