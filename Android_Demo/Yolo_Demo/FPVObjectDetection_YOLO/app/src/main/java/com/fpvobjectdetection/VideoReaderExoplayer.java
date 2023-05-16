package com.fpvobjectdetection;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.NonNullApi;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;

import java.io.IOException;

import usb.AndroidUSBInputStream;
//####################################

public class VideoReaderExoplayer {
    private static final String TAG = "DIGIVIEW";
    private Handler videoReaderEventListener;
    private ExoPlayer mPlayer;
    static final String VideoPreset = "VideoPreset";
    private final SurfaceView surfaceView;
    private AndroidUSBInputStream inputStream;
    private UsbMaskConnection mUsbMaskConnection;
    private boolean zoomedIn;
    private final Context context;
    private PerformancePreset performancePreset = PerformancePreset.getPreset(PerformancePreset.PresetType.DEFAULT);
    static final String VideoZoomedIn = "VideoZoomedIn";
    private final SharedPreferences sharedPreferences;
    private String user_drone_preset = "not_defined";

    VideoReaderExoplayer(SurfaceView videoSurface, Context c) {
        surfaceView = videoSurface;
        context = c;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(c);
    }

    VideoReaderExoplayer(SurfaceView videoSurface, Context c, Handler v) {
        this(videoSurface, c);
        videoReaderEventListener = v;

    }

    protected Bitmap ConvertToBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(surfaceView.getWidth(), surfaceView.getHeight(), Bitmap.Config.ARGB_8888);
        return bitmap;
    }

    public void setUsbMaskConnection(UsbMaskConnection connection) {
        mUsbMaskConnection = connection;
        inputStream = mUsbMaskConnection.mInputStream;
    }

    public void set_drone_preset(String drone_preset) {
        user_drone_preset = drone_preset;
        performancePreset = PerformancePreset.getPreset(user_drone_preset);
        sharedPreferences.edit().putString(VideoPreset, user_drone_preset).apply();
    }

    public void start(String connect_type) {
        zoomedIn = sharedPreferences.getBoolean(VideoZoomedIn, true);

        if (connect_type.equals("start")) {
            performancePreset = PerformancePreset.getPreset(user_drone_preset);
            sharedPreferences.edit().putString(VideoPreset, user_drone_preset).apply();
        }

        if (connect_type.equals("resume")) {
            performancePreset = PerformancePreset.getPreset(sharedPreferences.getString(VideoPreset, null));
        }


        DefaultLoadControl loadControl = new DefaultLoadControl.Builder().setBufferDurationsMs(performancePreset.exoPlayerMinBufferMs, performancePreset.exoPlayerMaxBufferMs, performancePreset.exoPlayerBufferForPlaybackMs, performancePreset.exoPlayerBufferForPlaybackAfterRebufferMs).build();
        mPlayer = new ExoPlayer.Builder(context).setLoadControl(loadControl).build();
        mPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        mPlayer.setWakeMode(C.WAKE_MODE_LOCAL);

        DataSpec dataSpec = new DataSpec(Uri.EMPTY, 0, C.LENGTH_UNSET);

        Log.d(TAG, "preset: " + performancePreset);

        DataSource.Factory dataSourceFactory = () -> {
            switch (performancePreset.dataSourceType) {
                case INPUT_STREAM:
                    return (DataSource) new InputStreamDataSource(context, dataSpec, inputStream);
                case BUFFERED_INPUT_STREAM:
                default:
                    return (DataSource) new InputStreamBufferedDataSource(context, dataSpec, inputStream);
            }
        };

        ExtractorsFactory extractorsFactory = () -> new Extractor[]{new H264Extractor(performancePreset.h264ReaderMaxSyncFrameSize, performancePreset.h264ReaderSampleTime)};
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory).createMediaSource(MediaItem.fromUri(Uri.EMPTY));
        mPlayer.setMediaSource(mediaSource);

        VideoFrameMetadataListener frameMetadataListener = (presentationTimeUs, releaseTimeNs, format, mediaFormat) -> {

            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                Bitmap bitmap = ConvertToBitmap();
                mainActivity.getFrame(bitmap); // Call the someFunction method of MainActivity
            }
        };

        mPlayer.setVideoFrameMetadataListener(frameMetadataListener);

        mPlayer.prepare();
        mPlayer.play();
        mPlayer.setVideoSurfaceView(surfaceView);

        mPlayer.addListener(new ExoPlayer.Listener() {
            @Override
            public void onPlaybackStateChanged(@NonNullApi int state) {
                switch (state) {
                    case Player.STATE_IDLE:
                    case Player.STATE_READY:
                    case Player.STATE_BUFFERING:
                        break;
                    case Player.STATE_ENDED:
                        Log.d(TAG, "PLAYER_STATE - ENDED");
                        sendEvent(VideoReaderEventMessageCode.WAITING_FOR_VIDEO); // let MainActivity know so it can hide watermark/show settings button
                        (new Handler(Looper.getMainLooper())).postDelayed(() -> restart(), 1000);
                        break;
                }
            }
        });

        mPlayer.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                Log.d(TAG, "PLAYER_RENDER - FIRST FRAME");
                sendEvent(VideoReaderEventMessageCode.VIDEO_PLAYING); // let MainActivity know so it can hide watermark/show settings button
            }
        });
    }

    private void sendEvent(VideoReaderEventMessageCode eventCode) {
        if (videoReaderEventListener != null) { // let MainActivity know so it can hide watermark/show settings button
            Message videoReaderEventMessage = new Message();
            videoReaderEventMessage.obj = eventCode;
            videoReaderEventListener.sendMessage(videoReaderEventMessage);
        }
    }

    public void restart() {
        mPlayer.release();

        if (mUsbMaskConnection.isReady()) {
            mUsbMaskConnection.start();
            start("resume");
        }
    }

    public void stop() {
        if (mPlayer != null)
            mPlayer.release();
    }

    public enum VideoReaderEventMessageCode {WAITING_FOR_VIDEO, VIDEO_PLAYING}

}
