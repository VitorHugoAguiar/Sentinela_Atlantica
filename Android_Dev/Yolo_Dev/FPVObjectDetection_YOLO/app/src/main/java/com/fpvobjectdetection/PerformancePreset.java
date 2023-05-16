package com.fpvobjectdetection;

public class PerformancePreset {
    int h264ReaderMaxSyncFrameSize = 131072;
    int h264ReaderSampleTime = 10000;
    int exoPlayerMinBufferMs = 500;
    int exoPlayerMaxBufferMs = 2000;
    int exoPlayerBufferForPlaybackMs = 17;
    int exoPlayerBufferForPlaybackAfterRebufferMs = 17;
    DataSourceType dataSourceType = DataSourceType.INPUT_STREAM;

    private PerformancePreset(){
    }

    private PerformancePreset(int mH264ReaderMaxSyncFrameSize, int mH264ReaderSampleTime, int mExoPlayerMinBufferMs, int mExoPlayerMaxBufferMs, int mExoPlayerBufferForPlaybackMs, int mExoPlayerBufferForPlaybackAfterRebufferMs, DataSourceType mDataSourceType){
        h264ReaderMaxSyncFrameSize = mH264ReaderMaxSyncFrameSize;
        h264ReaderSampleTime = mH264ReaderSampleTime;
        exoPlayerMinBufferMs = mExoPlayerMinBufferMs;
        exoPlayerMaxBufferMs = mExoPlayerMaxBufferMs;
        exoPlayerBufferForPlaybackMs = mExoPlayerBufferForPlaybackMs;
        exoPlayerBufferForPlaybackAfterRebufferMs = mExoPlayerBufferForPlaybackAfterRebufferMs;
        dataSourceType = mDataSourceType;
    }

    static PerformancePreset getPreset(PresetType p) {
        switch (p) {
            case CONSERVATIVE:
                return new PerformancePreset(131072, 14000, 500, 2000, 34, 34, DataSourceType.INPUT_STREAM);
            case AGGRESSIVE:
                return new PerformancePreset(131072, 2000, 500, 5000, 10, 20, DataSourceType.INPUT_STREAM);
                //return new PerformancePreset(131072, 2000, 5000, 5000, 1, 5, DataSourceType.INPUT_STREAM);//return new PerformancePreset(131072, 9000, 500, 2000, 10, 10, DataSourceType.INPUT_STREAM);
            case LEGACY:
                return new PerformancePreset(30720, 200, 32768, 65536, 0, 0, DataSourceType.BUFFERED_INPUT_STREAM);
            case LEGACY_BUFFERED:
                return new PerformancePreset(30720, 150, 32768, 65536, 8, 8, DataSourceType.BUFFERED_INPUT_STREAM);
            case DEFAULT:
            default:
                return new PerformancePreset(131072, 10000, 500, 2000, 17, 17, DataSourceType.INPUT_STREAM);
        }
    }

    public enum DataSourceType {
        INPUT_STREAM,
        BUFFERED_INPUT_STREAM
    }

    static PerformancePreset getPreset(String p) {
        switch (p) {
            case "conservative":
                return getPreset(PresetType.CONSERVATIVE);
            case "aggressive":
                return getPreset(PresetType.AGGRESSIVE);
            case "legacy":
                return getPreset(PresetType.LEGACY);
            case "legacy_buffered":
                return getPreset(PresetType.LEGACY_BUFFERED);
            case "default":
            default:
                return getPreset(PresetType.DEFAULT);
        }
    }

    public enum PresetType {
        DEFAULT,
        CONSERVATIVE,
        AGGRESSIVE,
        LEGACY,
        LEGACY_BUFFERED
    }

    @Override
    public String toString() {
        return "PerformancePreset{" +
                "h264ReaderMaxSyncFrameSize=" + h264ReaderMaxSyncFrameSize +
                ", h264ReaderSampleTime=" + h264ReaderSampleTime +
                ", exoPlayerMinBufferMs=" + exoPlayerMinBufferMs +
                ", exoPlayerMaxBufferMs=" + exoPlayerMaxBufferMs +
                ", exoPlayerBufferForPlaybackMs=" + exoPlayerBufferForPlaybackMs +
                ", exoPlayerBufferForPlaybackAfterRebufferMs=" + exoPlayerBufferForPlaybackAfterRebufferMs +
                ", dataSourceType=" + dataSourceType +
                '}';
    }
}
