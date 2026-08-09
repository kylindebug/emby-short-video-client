package cn.kylins.embyshorts;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import static org.junit.Assert.*;

public class RandomPlaylistTest {
    @Test public void upcomingAlwaysContainsNextTwoDistinctVideos() {
        RandomPlaylist list = new RandomPlaylist(Arrays.asList(
                new VideoItem("a", "A", "mp4"),
                new VideoItem("b", "B", "mp4"),
                new VideoItem("c", "C", "mp4")
        ), new Random(42));
        assertEquals(2, list.upcoming(2).size());
        assertEquals(2, new HashSet<>(list.upcoming(2)).size());
        assertFalse(list.upcoming(2).contains(list.current()));
    }

    @Test public void nextAndPreviousWrapWithoutFailure() {
        RandomPlaylist list = new RandomPlaylist(Arrays.asList(
                new VideoItem("a", "A", "mp4"),
                new VideoItem("b", "B", "mp4")
        ), new Random(7));
        VideoItem original = list.current();
        list.next();
        assertNotEquals(original, list.current());
        list.previous();
        assertEquals(original, list.current());
    }

    @Test public void preloadedItemMatchesNextAcrossCycleBoundary() {
        RandomPlaylist list = new RandomPlaylist(Arrays.asList(
                new VideoItem("a", "A", "mp4"),
                new VideoItem("b", "B", "mp4"),
                new VideoItem("c", "C", "mp4")
        ), new Random(11));
        list.next();
        list.next();
        VideoItem predicted = list.upcoming(1).get(0);
        assertEquals(predicted, list.next());
    }
}
