package org.techfield.tfmusic.music;

public enum SoundMode {
    SPATIAL("spatial"),
    GLOBAL("global");

    private final String serializedName;

    SoundMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public SoundMode next() {
        return this == SPATIAL ? GLOBAL : SPATIAL;
    }

    public static SoundMode fromSerializedName(String value) {
        return "global".equals(value) ? GLOBAL : SPATIAL;
    }
}
