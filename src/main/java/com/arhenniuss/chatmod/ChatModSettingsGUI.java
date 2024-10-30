package com.arhenniuss.chatmod;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiSlider;

import java.io.IOException;

public class ChatModSettingsGUI extends GuiScreen {

    private final MuteScreenshotMod mod;
    private GuiButton modEnabledButton;
    private GuiButton spamCheckEnabledButton;
    private GuiButton slangsCheckEnabledButton;

    public ChatModSettingsGUI(MuteScreenshotMod mod) {
        this.mod = mod;
        System.out.println("ChatMod GUI constructor called"); // Debug log
    }

    @Override
    public void initGui() {
        System.out.println("ChatMod GUI initGui called"); // Debug log
        this.buttonList.clear();

        int buttonWidth = 150;
        int buttonHeight = 20;
        int padding = 5;
        int startY = height / 4;

        // Mod Enabled button
        this.modEnabledButton = new GuiButton(0, width / 2 - buttonWidth / 2, startY, buttonWidth, buttonHeight, 
            "Mod: " + (mod.isModEnabled() ? "Enabled" : "Disabled"));
        this.buttonList.add(modEnabledButton);

        // Spam Check button
        this.spamCheckEnabledButton = new GuiButton(1, width / 2 - buttonWidth / 2, startY + buttonHeight + padding, buttonWidth, buttonHeight, 
            "Spam Check: " + (mod.isSpamCheckEnabled() ? "Enabled" : "Disabled"));
        this.buttonList.add(spamCheckEnabledButton);

        // Slangs Check button
        this.slangsCheckEnabledButton = new GuiButton(2, width / 2 - buttonWidth / 2, startY + (buttonHeight + padding) * 2, buttonWidth, buttonHeight, 
            "Slangs Check: " + (mod.isSlangsCheckEnabled() ? "Enabled" : "Disabled"));
        this.buttonList.add(slangsCheckEnabledButton);

        // Done button
        this.buttonList.add(new GuiButton(4, width / 2 - buttonWidth / 2, startY + (buttonHeight + padding) * 4, buttonWidth, buttonHeight, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        System.out.println("ChatMod GUI button clicked: " + button.id); // Debug log
        switch (button.id) {
            case 0: // Mod Enabled
                mod.setModEnabled(!mod.isModEnabled());
                modEnabledButton.displayString = "Mod: " + (mod.isModEnabled() ? "Enabled" : "Disabled");
                break;
            case 1: // Spam Check
                mod.setSpamCheckEnabled(!mod.isSpamCheckEnabled());
                spamCheckEnabledButton.displayString = "Spam Check: " + (mod.isSpamCheckEnabled() ? "Enabled" : "Disabled");
                break;
            case 2: // Slangs Check
                mod.setSlangsCheckEnabled(!mod.isSlangsCheckEnabled());
                slangsCheckEnabledButton.displayString = "Slangs Check: " + (mod.isSlangsCheckEnabled() ? "Enabled" : "Disabled");
                break;
            case 4: // Done
                this.mc.displayGuiScreen(null);
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        System.out.println("ChatMod GUI drawScreen called"); // Add this line
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, "ChatMod Settings", this.width / 2, 20, 16777215);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }	

//    @Override
//    public boolean doesGuiPauseGame() {
//        return false;
//    }
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        System.out.println(this.getClass().getSimpleName() + " handling mouse input");
    }
}