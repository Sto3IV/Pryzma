package net.pryzma.gui;

/*
 * Ranni: sometimes I wonder if I should just quit and become a farmer.
 */

import java.math.BigInteger;
import java.net.URI;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.exceptions.InvalidCredentialsException;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.pryzma.player.CapeUtils;

public class GuiScreenCapePryzma extends GuiScreenPryzma {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    private final Screen parentScreen;
    private String message;
    private long messageHideTimeMs;
    private String linkUrl;
    private GuiButtonPryzma buttonCopyLink;

    public GuiScreenCapePryzma(Screen parentScreen) {
        super(Component.literal(I18n.get("pr.options.capeOF.title")));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int i = 0;
        i += 2;
        this.addRenderableWidget(new GuiButtonPryzma(
                210,
                this.width / 2 - 155,
                this.height / 6 + 24 * (i >> 1),
                150,
                20,
                I18n.get("pr.options.capeOF.openEditor")));

        this.addRenderableWidget(new GuiButtonPryzma(
                220,
                this.width / 2 - 155 + 160,
                this.height / 6 + 24 * (i >> 1),
                150,
                20,
                I18n.get("pr.options.capeOF.reloadCape")));

        i += 6;
        this.buttonCopyLink = new GuiButtonPryzma(
                230,
                this.width / 2 - 100,
                this.height / 6 + 24 * (i >> 1),
                200,
                20,
                I18n.get("pr.options.capeOF.copyEditorLink"));
        this.buttonCopyLink.visible = (this.linkUrl != null);
        this.addRenderableWidget(this.buttonCopyLink);

        i += 4;
        this.addRenderableWidget(new GuiButtonPryzma(
                200,
                this.width / 2 - 100,
                this.height / 6 + 24 * (i >> 1),
                200,
                20,
                I18n.get("gui.done")));
    }

    @Override
    protected void actionPerformed(AbstractWidget guiElement) {
        if (!guiElement.active || !(guiElement instanceof GuiButtonPryzma button)) {
            return;
        }

        if (button.id == 200) {
            this.minecraft.setScreen(this.parentScreen);
            return;
        }

        if (button.id == 210) {
            try {
                String userName = this.minecraft.getUser().getName();
                String userId = this.minecraft.getGameProfile().getId().toString().replace("-", "");
                String accessToken = this.minecraft.getUser().getAccessToken();

                Random r1 = new Random();
                Random r2 = new Random(System.identityHashCode(new Object()));
                BigInteger random1Bi = new BigInteger(128, r1);
                BigInteger random2Bi = new BigInteger(128, r2);
                BigInteger serverBi = random1Bi.xor(random2Bi);
                String serverId = serverBi.toString(16);

                this.minecraft.getMinecraftSessionService().joinServer(
                        this.minecraft.getGameProfile().getId(),
                        accessToken,
                        serverId);

                String urlStr = "https://optifine.net/capeChange?u=" + userId + "&n=" + userName + "&s=" + serverId;
                boolean opened = false;
                try {
                    Util.getPlatform().openUri(new URI(urlStr));
                    opened = true;
                } catch (Throwable t) {
                    LOGGER.warn("Failed to open OptiFine cape URL directly: {}", t.getMessage());
                }

                if (opened) {
                    this.showMessage(I18n.get("pr.message.capeOF.openEditor"), 10000L);
                } else {
                    this.showMessage(I18n.get("pr.message.capeOF.openEditorError"), 10000L);
                    this.setLinkUrl(urlStr);
                }
            } catch (InvalidCredentialsException e) {
                LOGGER.warn("Mojang authentication failed: {}: {}", e.getClass().getName(), e.getMessage());
                this.showMessage(I18n.get("pr.message.capeOF.error1") + " " + String.format(I18n.get("pr.message.capeOF.error2"), e.getMessage()), 10000L);
            } catch (Exception e) {
                LOGGER.warn("Error opening OptiFine cape link: {}: {}", e.getClass().getName(), e.getMessage());
                this.showMessage(String.format(I18n.get("pr.message.capeOF.error2"), e.getMessage()), 10000L);
            }
        }

        if (button.id == 220) {
            this.showMessage(I18n.get("pr.message.capeOF.reloadCape"), 15000L);
            if (this.minecraft.player != null) {
                CapeUtils.reloadCape(this.minecraft.player);
            }
        }

        if (button.id == 230 && this.linkUrl != null) {
            this.minecraft.keyboardHandler.setClipboard(this.linkUrl);
        }
    }

    private void showMessage(String msg, long timeMs) {
        this.message = msg;
        this.messageHideTimeMs = System.currentTimeMillis() + timeMs;
        this.setLinkUrl(null);
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
        if (this.buttonCopyLink != null) {
            this.buttonCopyLink.visible = (linkUrl != null);
        }
    }

    @Override
    public void render(GuiGraphics graphicsIn, int mouseX, int mouseY, float partialTicks) {
        super.render(graphicsIn, mouseX, mouseY, partialTicks);
        drawCenteredString(graphicsIn, this.font, this.title, this.width / 2, 20, 16777215);
        if (this.message != null) {
            drawCenteredString(graphicsIn, this.font, this.message, this.width / 2, this.height / 6 + 60, 16777215);
            if (System.currentTimeMillis() > this.messageHideTimeMs) {
                this.message = null;
                this.setLinkUrl(null);
            }
        }
    }
}
