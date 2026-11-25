package com.amela.client.module;

public class ModuleManager {
    private boolean autoSprint = false;
    private boolean fullbright = false;
    private boolean killAura = false;

    public boolean isAutoSprint() { return autoSprint; }
    public void toggleAutoSprint() { autoSprint = !autoSprint; }

    public boolean isFullbright() { return fullbright; }
    public void toggleFullbright() { fullbright = !fullbright; }

    public boolean isKillAura() { return killAura; }
    public void toggleKillAura() { killAura = !killAura; }
}
