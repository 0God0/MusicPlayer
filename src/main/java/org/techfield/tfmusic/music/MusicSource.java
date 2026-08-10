package org.techfield.tfmusic.music;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public enum MusicSource {
    DIRECT_LINK("direct_link"),
    BILIBILI("bilibili");

    private static final Set<String> DIRECT_EXTENSIONS = Set.of("mp3", "m4a");
    private static final Pattern BVID_PATTERN = Pattern.compile("^BV[1-9A-HJ-NP-Za-km-z]{10}$");

    private final String serializedName;

    MusicSource(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MusicSource fromSerializedName(String value) {
        for (MusicSource source : values()) {
            if (source.serializedName.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown music source: " + value);
    }

    public String validateLocator(String locator) {
        return switch (this) {
            case DIRECT_LINK -> validateDirectLink(locator);
            case BILIBILI -> BVID_PATTERN.matcher(locator).matches() ? null : "Invalid BiliBili BV id";
        };
    }

    private static String validateDirectLink(String input) {
        if (input == null || input.isBlank()) {
            return "Music URL is empty";
        }

        final URI uri;
        try {
            uri = URI.create(input.trim());
        } catch (IllegalArgumentException exception) {
            return "Music URL is invalid";
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return "Only HTTP and HTTPS URLs are supported";
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return "Music URL has no host";
        }
        if (uri.getPort() < -1 || uri.getPort() > 65535) {
            return "Music URL has an invalid port";
        }

        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return "Music URL has no file name";
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "Music URL has no audio extension";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return DIRECT_EXTENSIONS.contains(extension) ? null : "Only MP3 and M4A direct links are supported";
    }
}
