package com.worldcup.prediction.integration.football;

public record SyncResult(boolean skipped, String message) {

    public static SyncResult success(String message) {
        return new SyncResult(false, message);
    }

    public static SyncResult skipped(String reason) {
        return new SyncResult(true, "Skipped: " + reason);
    }
}
