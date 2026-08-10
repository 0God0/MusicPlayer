package org.techfield.tfmusic.music;

public enum PlaybackMode {
    SEQUENTIAL("sequential"),
    REPEAT_ALL("repeat_all"),
    REPEAT_ONE("repeat_one"),
    SHUFFLE("shuffle");

    private final String serializedName;

    PlaybackMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public PlaybackMode next() {
        PlaybackMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public static PlaybackMode fromSerializedName(String value) {
        for (PlaybackMode mode : values()) {
            if (mode.serializedName.equals(value)) {
                return mode;
            }
        }
        return SEQUENTIAL;
    }
}
