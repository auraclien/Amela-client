package com.amela.client.module;

// серверная ротация: камера игрока не меняется, сервер видит эти углы
public final class SilentRotation {
    private static boolean active;
    private static float yaw, pitch;

    private SilentRotation() {}

    public static boolean isActive() { return active; }
    public static float getYaw() { return yaw; }
    public static float getPitch() { return pitch; }

    public static void set(float y, float p) {
        active = true;
        yaw = y;
        pitch = p;
    }

    public static void disable() { active = false; }
}
