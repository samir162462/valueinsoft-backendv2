package com.example.valueinsoftbackend.Model.Inventory;

public enum TrackingType {
    QUANTITY,
    SERIAL,
    IMEI,
    BATCH;

    public boolean isSerialized() {
        return this == SERIAL || this == IMEI;
    }

    public static TrackingType defaultIfNull(TrackingType trackingType) {
        return trackingType == null ? QUANTITY : trackingType;
    }
}
