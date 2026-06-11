package com.worldcup.prediction.domain.enums;

public enum SchedulerJobStatus {
    IN_PROGRESS, SUCCESS, SKIPPED, FAILED;

    public String getDisplayLabel() {
        return switch (this) {
            case IN_PROGRESS -> "Running";
            case SUCCESS     -> "Success";
            case SKIPPED     -> "Skipped";
            case FAILED      -> "Failed";
        };
    }

    public String getBadgeCss() {
        return switch (this) {
            case IN_PROGRESS -> "bg-blue-100 text-blue-700";
            case SUCCESS     -> "bg-green-100 text-green-800";
            case SKIPPED     -> "bg-gray-100 text-gray-600";
            case FAILED      -> "bg-red-100 text-red-700";
        };
    }
}
