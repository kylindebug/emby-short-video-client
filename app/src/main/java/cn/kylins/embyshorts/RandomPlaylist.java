package cn.kylins.embyshorts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class RandomPlaylist {
    private final List<VideoItem> items;
    private final Random random;
    private int index;

    public RandomPlaylist(List<VideoItem> source) { this(source, new Random()); }

    RandomPlaylist(List<VideoItem> source, Random random) {
        this.random = random;
        this.items = new ArrayList<>(source);
        Collections.shuffle(this.items, random);
        this.index = 0;
    }

    public boolean isEmpty() { return items.isEmpty(); }
    public VideoItem current() { return items.get(index); }

    public VideoItem next() {
        if (items.size() == 1) return items.get(0);
        index++;
        if (index >= items.size()) {
            index = 0;
        }
        return current();
    }

    public VideoItem previous() {
        index--;
        if (index < 0) index = items.size() - 1;
        return current();
    }

    public List<VideoItem> upcoming(int count) {
        List<VideoItem> result = new ArrayList<>();
        if (items.isEmpty()) return result;
        for (int offset = 1; offset <= Math.min(count, items.size() - 1); offset++) {
            result.add(items.get((index + offset) % items.size()));
        }
        return result;
    }
}
