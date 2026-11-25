package com.amela.client.gui;

import com.amela.client.MainClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;

public class AmelaScreen extends Screen {
    private static final String[] TABS = {"Combat", "Render", "Player", "Misc"};

    private static final int PANEL_W = 360, PANEL_H = 220;
    private static final int HEADER_H = 28, TAB_H = 22, ROW_H = 24;
    private static final int TAB_W = PANEL_W / TABS.length;

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

    private record Row(String name, BooleanSupplier state, Runnable toggle) {}

    private int activeTab = 0;
    private int px, py;

    public AmelaScreen() {
        super(Text.literal("Amela client"));
    }

    private List<Row> rows(int tab) {
        return switch (tab) {
            case 0 -> List.of(new Row("KillAura", MainClient.MODULES::isKillAura, MainClient.MODULES::toggleKillAura));
            case 1 -> List.of(new Row("Fullbright", MainClient.MODULES::isFullbright, MainClient.MODULES::toggleFullbright));
            case 2 -> List.of(new Row("AutoSprint", MainClient.MODULES::isAutoSprint, MainClient.MODULES::toggleAutoSprint));
            default -> List.of();
        };
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, C_OVERLAY);

        fillRound(ctx, px - 1, py - 1, PANEL_W + 2, PANEL_H + 2, 7, C_BORDER);
        fillRound(ctx, px, py, PANEL_W, PANEL_H, 6, C_PANEL);

        // шапка
        fillRound(ctx, px, py, PANEL_W, HEADER_H, 6, C_HEADER);
        ctx.fill(px, py + HEADER_H - 6, px + PANEL_W, py + HEADER_H, C_HEADER);
        ctx.fill(px + 6, py, px + PANEL_W - 6, py + 2, C_ACCENT);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Amela client"), px + 10, py + 10, C_ACCENT);
        Text ver = Text.literal("v1.0.0");
        ctx.drawTextWithShadow(this.textRenderer, ver,
                px + PANEL_W - 10 - this.textRenderer.getWidth(ver), py + 10, C_GRAY);

        // вкладки-чипы
        int tabsY = py + HEADER_H;
        for (int i = 0; i < TABS.length; i++) {
            int tx = px + i * TAB_W;
            boolean active = i == activeTab;
            boolean hover = inRect(mouseX, mouseY, tx, tabsY, TAB_W, TAB_H);
            fillRound(ctx, tx + 2, tabsY + 2, TAB_W - 4, TAB_H - 4, 4,
                    active ? C_TAB_ON : hover ? C_TAB_HOV : C_TAB);
            if (active) ctx.fill(tx + 6, tabsY + TAB_H - 5, tx + TAB_W - 6, tabsY + TAB_H - 3, C_ACCENT);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(TABS[i]),
                    tx + TAB_W / 2, tabsY + (TAB_H - 9) / 2, active ? C_TEXT : C_GRAY);
        }
        ctx.drawHorizontalLine(px + 6, px + PANEL_W - 7, tabsY + TAB_H, C_BORDER);

        // модули
        List<Row> list = rows(activeTab);
        if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Пока пусто"),
                    px + PANEL_W / 2, py + HEADER_H + TAB_H + 24, C_GRAY);
        }
        for (int i = 0; i < list.size(); i++) {
            Row row = list.get(i);
            boolean on = row.state().getAsBoolean();
            int bx = px + 10, by = py + HEADER_H + TAB_H + 10 + i * (ROW_H + 6), bw = PANEL_W - 20;
            boolean hover = inRect(mouseX, mouseY, bx, by, bw, ROW_H);
            fillRound(ctx, bx, by, bw, ROW_H, 5, hover ? C_TAB_HOV : C_TAB);
            ctx.fill(bx + 4, by + 6, bx + 6, by + ROW_H - 6, on ? C_ON : C_OFF);
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(row.name()), bx + 12, by + (ROW_H - 9) / 2, C_TEXT);
            Text state = Text.literal(on ? "[ON]" : "[OFF]");
            ctx.drawTextWithShadow(this.textRenderer, state,
                    bx + bw - 10 - this.textRenderer.getWidth(state), by + (ROW_H - 9) / 2, on ? C_ON : C_OFF);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("RShift / ESC — закрыть"),
                px + PANEL_W / 2, py + PANEL_H - 16, C_GRAY);
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
            List<Row> list = rows(activeTab);
            for (int i = 0; i < list.size(); i++) {
                int by = py + HEADER_H + TAB_H + 10 + i * (ROW_H + 6);
                if (inRect(mx, my, px + 10, by, PANEL_W - 20, ROW_H)) {
                    list.get(i).toggle().run();
                    return true;
                }
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

    private static void fillRound(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        int baseA = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double dx = Math.sqrt(r * r - dy * dy);
            int full = (int) dx;
            int edgeA = (int) (baseA * (dx - full));
            int edge = (edgeA << 24) | rgb;
            int yt = y + i, yb = y + h - 1 - i;
            ctx.fill(x + r - full, yt, x + r, yt + 1, color);
            ctx.fill(x + r - full, yb, x + r, yb + 1, color);
            ctx.fill(x + w - r, yt, x + w - r + full, yt + 1, color);
            ctx.fill(x + w - r, yb, x + w - r + full, yb + 1, color);
            if (edgeA > 0) {
                ctx.fill(x + r - full - 1, yt, x + r - full, yt + 1, edge);
                ctx.fill(x + r - full - 1, yb, x + r - full, yb + 1, edge);
                ctx.fill(x + w - r + full, yt, x + w - r + full + 1, yt + 1, edge);
                ctx.fill(x + w - r + full, yb, x + w - r + full + 1, yb + 1, edge);
            }
        }
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
