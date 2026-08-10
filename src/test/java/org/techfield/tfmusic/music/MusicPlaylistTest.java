package org.techfield.tfmusic.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPlaylistTest {
    @Test
    void nbtRoundTripPreservesPlaylist() {
        MusicPlaylist original = playlist("One", "Two").withMode(PlaybackMode.SHUFFLE);

        MusicPlaylist loaded = MusicPlaylist.load(original.save()).orElseThrow();

        assertEquals(original, loaded);
    }

    @Test
    void exactDuplicateIsRejectedButRenamedTrackIsAllowed() {
        MusicTrack first = track("Track", "https://example.com/one.mp3");
        MusicPlaylist playlist = MusicPlaylist.createDefault().add(first);

        assertSame(playlist, playlist.add(first));
        assertEquals(2, playlist.add(new MusicTrack(
                first.source(),
                "Renamed",
                first.durationMs(),
                first.locator(),
                first.page()
        )).entries().size());
    }

    @Test
    void moveAndRemoveKeepStableEntryIds() {
        MusicPlaylist playlist = playlist("One", "Two", "Three");
        PlaylistEntry first = playlist.entries().get(0);
        PlaylistEntry second = playlist.entries().get(1);

        MusicPlaylist moved = playlist.move(first.id(), 1);

        assertEquals(second.id(), moved.entries().get(0).id());
        assertEquals(first.id(), moved.entries().get(1).id());
        assertEquals(2, moved.remove(first.id()).entries().size());
        assertTrue(moved.remove(first.id()).findEntry(first.id()).isEmpty());
    }

    @Test
    void rejectsMoreThanMaximumTracks() {
        MusicPlaylist playlist = MusicPlaylist.createDefault();
        for (int index = 0; index < MusicPlaylist.MAX_TRACKS; index++) {
            playlist = playlist.add(track("Track " + index, "https://example.com/track" + index + ".mp3"));
        }

        assertEquals(MusicPlaylist.MAX_TRACKS, playlist.entries().size());
        assertSame(playlist, playlist.add(track("Overflow", "https://example.com/overflow.mp3")));
    }

    private static MusicPlaylist playlist(String... names) {
        MusicPlaylist playlist = MusicPlaylist.createDefault();
        for (int index = 0; index < names.length; index++) {
            playlist = playlist.add(track(names[index], "https://example.com/track" + index + ".mp3"));
        }
        return playlist;
    }

    private static MusicTrack track(String name, String locator) {
        return new MusicTrack(MusicSource.DIRECT_LINK, name, 60_000L, locator, 1);
    }
}
