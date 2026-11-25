package com.amela.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class AmelaScreen extends Screen {
    private String currentTab = "Player";
    private final String[] tabs = {"Combat", "Render", "Player", "Misc"};
    private final int buttonWidth = 80;
    private final int buttonHeight = 25;
    private final int spacing = 5;

    protected AmelaScreen() {
        super(Text.literal("Amela Client"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Фон
        context.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);

        // Заголовок
        context.drawCenteredTextWithShadow(this.textRenderer, "Amela Client", this.width / 2, 30, 0xFFFFFF);

        // Вкладки
        int startX = (this.width - (tabs.length * (buttonWidth + spacing))) / 2;
        int y = 60;

        for (int i = 0; i < tabs.length; i++) {
            int x = startX + i * (buttonWidth + spacing);
            boolean hovered = mouseX >= x && mouseX <= x + buttonWidth && mouseY >= y && mouseY <= y + buttonHeight;
            boolean active = tabs[i].equals(currentTab);

            int color = active ? 0xFF00AAFF : (hovered ? 0xFF555555 : 0xFF333333);
            
            context.fill(x, y, x + buttonWidth, y + buttonHeight, color);
            context.drawCenteredTextWithShadow(this.textRenderer, tabs[i], x + buttonWidth / 2, y + (buttonHeight / 2) - 4, 0xFFFFFF);
        }

        // Контент
        context.drawCenteredTextWithShadow(this.textRenderer, "Modules: " + currentTab, this.width / 2, 120, 0xAAAAAA);
        
        if ("Player".equals(currentTab)) {
            int btnX = this.width / 2 - 60;
            int btnY = 150;
            boolean sprintActive = ModuleManager.autoSprintEnabled;
            int sprintColor = sprintActive ? 0xFF00FF00 : 0xFFFF0000;
            
            context.fill(btnX, btnY, btnX + 120, btnY + 25, sprintColor);
            context.drawCenteredTextWithShadow(this.textRenderer, 
                sprintActive ? "AutoSprint [ON]" : "AutoSprint [OFF]", 
                btnX + 60, btnY + 7, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startX = (this.width - (tabs.length * (buttonWidth + spacing))) / 2;
            int y = 60;
            // Клик по табам
            for (int i = 0; i < tabs.length; i++) {
                int x = startX + i * (buttonWidth + spacing);
                if (mouseX >= x && mouseX <= x + buttonWidth && mouseY >= y && mouseY <= y + buttonHeight) {
                    currentTab = tabs[i];
                    return true;
                }
            }
            
            // Клик по AutoSprint
            if ("Player".equals(currentTab)) {
                int btnX = this.width / 2 - 60;
                int btnY = 150;
                if (mouseX >= btnX && mouseX <= btnX + 120 && mouseY >= btnY && mouseY <= btnY + 25) {
                    ModuleManager.autoSprintEnabled = !ModuleManager.autoSprintEnabled;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
