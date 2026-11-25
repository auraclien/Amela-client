package com.amela.client.module;

public class ModuleManager {
    private boolean autoSprint = false;

    public boolean isAutoSprint() {
        return autoSprint;
    }

    public void toggleAutoSprint() {
        autoSprint = !autoSprint;
    }
}
