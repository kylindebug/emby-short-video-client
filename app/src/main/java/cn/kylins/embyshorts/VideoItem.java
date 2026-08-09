package cn.kylins.embyshorts;

import java.util.Objects;

public final class VideoItem {
    public final String id;
    public final String title;
    public final String container;

    public VideoItem(String id, String title, String container) {
        this.id = id;
        this.title = title;
        this.container = container == null || container.isBlank() ? "mp4" : container;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof VideoItem)) return false;
        VideoItem that = (VideoItem) other;
        return id.equals(that.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
