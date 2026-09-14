package cn.kylins.embyshorts;

/** Only safe, classified diagnostics: never include stream URLs or exception messages. */
public final class PlaybackFailure {
    public enum Kind { NETWORK, DECODE, SOURCE, UNKNOWN }
    public final Kind kind;
    public final String code;

    public PlaybackFailure(Kind kind, String code) {
        this.kind = kind;
        this.code = code;
    }

    public String message() {
        return switch (kind) {
            case NETWORK -> "网络连接失败，请检查 NAS、网络或登录状态";
            case DECODE -> "视频或音频解码失败，可尝试软解";
            case SOURCE -> "媒体格式无法读取，文件可能不完整或不受支持";
            case UNKNOWN -> "无法播放，可能是网络或媒体格式问题";
        };
    }
}
