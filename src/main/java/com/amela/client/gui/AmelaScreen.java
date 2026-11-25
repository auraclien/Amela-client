package com.amela.client.gui;

import com.amela.client.MainClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AmelaScreen extends Screen {
    private static final String[] TABS = {"Combat", "Render", "Player", "Misc"};
    private static final int PLAYER_TAB = 2;

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 220;
    private static final int HEADER_H = 28;
    private static final int TAB_H = 22;
    private static final int TAB_W = PANEL_W / TABS.length;
    private static final int ROW_H = 24;

    private static final int C_OVERLAY = 0xB0080810;
    private static final int C_PANEL   = 0xF014141C;
    private static final int C_BORDER  = 0xFF2E2E40;
    private static final int C_HEADER  = 0xFF1B1B26;
    private static final int C_ACCENT  = 0xFF9D5CFF;
    private static final int C_TAB     = 0xFF1E1E2A;
    private static final int C_TAB_HOV = 0xFF272736;
    private static final int C_TAB_ON  = 0xFF2B2144;
    private static final int C_TEXT    = 0xFFE8E8F2;
    private static final int C_GRAY    = 0xFF8A8A99;
    private static final int C_ON      = 0xFF4ADE80;
    private static final int C_OFF     = 0xFFF87171;

    private int activeTab = 0;
    private int px, py;

    public AmelaScreen() {
        super(Text.literal("Amela client"));
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, C_OVERLAY);

        // панель с рамкой
        ctx.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, C_BORDER);
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, C_PANEL);

        // шапка
        ctx.fill(px, py, px + PANEL_W, py + HEADER_H, C_HEADER);
        ctx.fill(px, py, px + PANEL_W, py + 2, C_ACCENT);
        ctx.drawTextWithShadow(this.textRenderer, "Amela client", px + 10, py + 10, C_ACCENT);
        String ver = "v1.0.0";
        ctx.drawTextWithShadow(this.textRenderer, ver,
                px + PANEL_W - 10 - this.textRenderer.getWidth(ver), py + 10, C_GRAY);

        // вкладки
        int tabsY = py + HEADER_H;
        for (int i = 0; i < TABS.length; i++) {
            int tx = px + i * TAB_W;
            boolean active = i == activeTab;
            boolean hover = inRect(mouseX, mouseY, tx, tabsY, TAB_W, TAB_H);
            ctx.fill(tx, tabsY, tx + TAB_W, tabsY + TAB_H,
                    active ? C_TAB_ON : hover ? C_TAB_HOV : C_TAB);
            if (active) ctx.fill(tx, tabsY + TAB_H - 2, tx + TAB_W, tabsY + TAB_H, C_ACCENT);
            ctx.drawCenteredTextWithShadow(this.textRenderer, TABS[i],
                    tx + TAB_W / 2, tabsY + (TAB_H - 8) / 2, active ? C_TEXT : C_GRAY);
        }
        ctx.drawHorizontalLine(px, px + PANEL_W - 1, tabsY + TAB_H, C_BORDER);

        // контент
        if (activeTab == PLAYER_TAB) {
            boolean on = MainClient.MODULES.isAutoSprint();
            int bx = moduleX(), by = moduleY(), bw = moduleW();
            boolean hover = inRect(mouseX, mouseY, bx, by, bw, ROW_H);
            ctx.fill(bx, by, bx + bw, by + ROW_H, hover ? C_TAB_HOV : C_TAB);
            ctx.fill(bx, by, bx + 2, by + ROW_H, on ? C_ON : C_OFF);
            ctx.drawTextWithShadow(this.textRenderer, "AutoSprint",
                    bx + 10, by + (ROW_H - 8) / 2, C_TEXT);
            String state = on ? "[ON]" : "[OFF]";
            ctx.drawTextWithShadow(this.textRenderer, state,
                    bx + bw - 10 - this.textRenderer.getWidth(state),
                    by + (ROW_H - 8) / 2, on ? C_ON : C_OFF);
        } else {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Пока пусто",
                    px + PANEL_W / 2, moduleY() + 20, C_GRAY);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer, "RShift / ESC — закрыть",
                px + PANEL_W / 2, py + PANEL_H - 14, C_GRAY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double mx = click.x(), my = click.y();
            int tabsY = py + HEADER_H;
            for (int i = 0; i < TABS.length; i++) {
                if (inRect(mx, my, px + i * TAB_W, tabsY, TAB_W, TAB_H)) {
                    activeTab = i;
                    return true;
                }
            }
            if (activeTab == PLAYER_TAB && inRect(mx, my, moduleX(), moduleY(), moduleW(), ROW_H)) {
                MainClient.MODULES.toggleAutoSprint();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int moduleX() { return px + 10; }
    private int moduleY() { return py + HEADER_H + TAB_H + 10; }
    private int moduleW() { return PANEL_W - 20; }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
