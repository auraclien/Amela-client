package com.amela.client.module;

public final class SilentRotation {
    private static boolean active;
    private static float yaw, pitch;
    private static float sentYaw, sentPitch; // последняя ротация, реально ушедшая серверу

    private SilentRotation() {}

    public static boolean isActive() { return active; }
    public static float getYaw() { return yaw; }
    public static float getPitch() { return pitch; }
    public static float getSentYaw() { return sentYaw; }
    public static float getSentPitch() { return sentPitch; }

    public static void set(float y, float p) {
        active = true;
        yaw = y;
        pitch = p;
    }

    public static void markSent(float y, float p) {
        sentYaw = y;
        sentPitch = p;
    }

    public static void disable() { active = false; }
}
