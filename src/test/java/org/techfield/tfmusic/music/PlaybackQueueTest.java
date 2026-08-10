package org.techfield.tfmusic.music;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackQueueTest {
    @Test
    void sequentialStopsAfterTheLastTrack() {
        MusicPlaylist playlist = playlist("One", "Two");
        PlaybackCursor first = PlaybackQueue.initial(playlist);

        PlaybackQueue.AdvanceResult second = PlaybackQueue.advance(playlist, first, false);
        PlaybackQueue.AdvanceResult stopped = PlaybackQueue.advance(playlist, second.cursor(), false);

        assertEquals("Two", second.cursor().track().name());
        assertTrue(second.continuePlaying());
        assertFalse(stopped.continuePlaying());
        assertEquals("One", stopped.cursor().track().name());
    }

    @Test
    void repeatAllWrapsAndRepeatOneKeepsTheCurrentEntry() {
        MusicPlaylist repeatAll = playlist("One", "Two").withMode(PlaybackMode.REPEAT_ALL);
        PlaybackCursor allFirst = PlaybackQueue.initial(repeatAll);
        PlaybackCursor allSecond = PlaybackQueue.advance(repeatAll, allFirst, false).cursor();
        PlaybackQueue.AdvanceResult allWrapped = PlaybackQueue.advance(repeatAll, allSecond, false);

        assertTrue(allWrapped.continuePlaying());
        assertEquals(allFirst.entryId(), allWrapped.cursor().entryId());

        MusicPlaylist repeatOne = playlist("One", "Two").withMode(PlaybackMode.REPEAT_ONE);
        PlaybackCursor oneFirst = PlaybackQueue.initial(repeatOne);
        PlaybackQueue.AdvanceResult oneAgain = PlaybackQueue.advance(repeatOne, oneFirst, false);

        assertTrue(oneAgain.continuePlaying());
        assertEquals(oneFirst.entryId(), oneAgain.cursor().entryId());
        assertEquals(0L, oneAgain.cursor().progressMs());
    }

    @Test
    void shuffleOrderIsACompletePersistedPermutation() {
        MusicPlaylist playlist = playlist("One", "Two", "Three", "Four").withMode(PlaybackMode.SHUFFLE);
        PlaybackCursor cursor = PlaybackQueue.initial(playlist);
        Set<java.util.UUID> ids = new HashSet<>();

        for (int index = 0; index < 4; index++) {
            assertNotNull(cursor.entryId());
            assertTrue(ids.add(cursor.entryId()));
            cursor = PlaybackQueue.advance(playlist, cursor, false).cursor();
        }

        assertEquals(4, ids.size());
        PlaybackCursor loaded = PlaybackCursor.load(cursor.save(), playlist);
        assertEquals(cursor, loaded);
    }

    @Test
    void deletingCurrentTrackLetsTheSnapshotFinishThenUsesTheNewPosition() {
        MusicPlaylist original = playlist("One", "Two", "Three");
        PlaybackCursor current = PlaybackQueue.initial(original);
        MusicPlaylist edited = original.remove(current.entryId());

        PlaybackCursor synchronizedCursor = PlaybackQueue.synchronize(edited, current);
        PlaybackQueue.AdvanceResult next = PlaybackQueue.advance(edited, synchronizedCursor, false);

        assertEquals("One", synchronizedCursor.track().name());
        assertEquals("Two", next.cursor().track().name());
        assertTrue(next.continuePlaying());
    }

    @Test
    void successorSurvivesFurtherEditsBeforeTheCurrentSnapshotFinishes() {
        MusicPlaylist original = playlist("One", "Two", "Three", "Four");
        PlaybackCursor current = PlaybackQueue.initial(original);
        current = PlaybackQueue.advance(original, current, false).cursor();
        current = PlaybackQueue.advance(original, current, false).cursor();

        MusicPlaylist withoutCurrent = original.remove(current.entryId());
        current = PlaybackQueue.synchronize(withoutCurrent, current);
        MusicPlaylist editedAgain = withoutCurrent.remove(withoutCurrent.entries().get(0).id());
        current = PlaybackQueue.synchronize(editedAgain, current);
        PlaybackQueue.AdvanceResult next = PlaybackQueue.advance(editedAgain, current, false);

        assertEquals("Three", current.track().name());
        assertEquals("Four", next.cursor().track().name());
        assertTrue(next.continuePlaying());
    }

    @Test
    void newlyAddedTrackJoinsThePendingOrderAfterCurrentAndSuccessorAreDeleted() {
        MusicPlaylist original = playlist("One", "Two", "Three", "Four");
        PlaybackCursor current = PlaybackQueue.initial(original);
        current = PlaybackQueue.advance(original, current, false).cursor();
        current = PlaybackQueue.advance(original, current, false).cursor();

        MusicPlaylist edited = original.remove(current.entryId());
        current = PlaybackQueue.synchronize(edited, current);
        edited = edited.remove(edited.entries().get(2).id());
        edited = edited.add(new MusicTrack(
                MusicSource.DIRECT_LINK,
                "Five",
                60_000L,
                "https://example.com/five.mp3",
                1
        ));
        current = PlaybackQueue.synchronize(edited, current);
        PlaybackQueue.AdvanceResult next = PlaybackQueue.advance(edited, current, false);

        assertEquals("Five", next.cursor().track().name());
        assertTrue(next.continuePlaying());
    }

    private static MusicPlaylist playlist(String... names) {
        MusicPlaylist playlist = MusicPlaylist.createDefault();
        for (int index = 0; index < names.length; index++) {
            playlist = playlist.add(new MusicTrack(
                    MusicSource.DIRECT_LINK,
                    names[index],
                    60_000L,
                    "https://example.com/track" + index + ".mp3",
                    1
            ));
        }
        return playlist;
    }
}
