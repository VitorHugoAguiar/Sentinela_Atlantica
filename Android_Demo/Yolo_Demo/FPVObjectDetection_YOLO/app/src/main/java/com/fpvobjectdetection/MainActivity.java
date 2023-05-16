package com.fpvobjectdetection;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.bumptech.glide.Glide;
import com.fpvobjectdetection.env.BorderedText;
import com.fpvobjectdetection.env.ImageUtils;
import com.fpvobjectdetection.tracking.MultiBoxTracker;
import com.fpvobjectdetection.tflite.Classifier;
import com.fpvobjectdetection.tflite.DetectorFactory;
import com.fpvobjectdetection.tflite.YoloV5Classifier;
import com.fpvobjectdetection.env.Logger;
import com.fpvobjectdetection.customview.OverlayView2;

import android.graphics.Bitmap.Config;

public class MainActivity extends AppCompatActivity implements UsbDeviceListener {
    private static final String ACTION_USB_PERMISSION = "com.fpvobjectdetection.USB_PERMISSION";
    private static final String TAG = "FPVDETECT";
    private static final int VENDOR_ID = 11427;
    private static final int PRODUCT_ID = 31;
    private int shortAnimationDuration;
    private float buttonAlpha = 1;
    private View settingsButton;
    private View watermarkView;
    private OverlayView overlayView;
    PendingIntent permissionIntent = null;
    UsbDeviceBroadcastReceiver usbDeviceBroadcastReceiver;
    UsbManager usbManager;
    UsbDevice usbDevice;
    UsbMaskConnection mUsbMaskConnection;
    VideoReaderExoplayer mVideoReader;
    boolean usbConnected = false;
    SurfaceView fpvView;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private SharedPreferences sharedPreferences;
    private static final String ShowWatermark = "ShowWatermark";

    //********//
    private static final Logger LOGGER = new Logger();
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final String YOLOModel = "PortoSanto_s_640_300epochs-int8.tflite";
    private static int numThreads = 1;
    private static boolean UseGPU = true;
    public static float MINIMUM_CONFIDENCE_TF_OD_API = 0.0f;
    private static final float TEXT_SIZE_DIP = 8;
    private Integer sensorOrientation;
    private OverlayView2 trackingOverlay;
    private YoloV5Classifier detector;
    private Bitmap croppedBitmap = null;
    private Bitmap zoomBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private Bitmap tiltedImage = null;
    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix cropToFrameTransform;
    private Matrix frameToCropTransform;
    private MultiBoxTracker tracker;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private boolean isProcessingFrame = false;
    private int[] rgbBytes = null;
    private Runnable postInferenceCallback;
    private Handler handlerTF;
    private HandlerThread handlerThreadTF;
    private BorderedText borderedText;

    ImageView textBitmap;
    ArrayList<String> classesEnabled = new ArrayList<>();
    String[] classesElementsPerson = {"person"};
    String[] classesElementsVehicle = {"vehicle", "motorcycle", "car"};
    String[] classesElementsWeapon = {"weapon"};
    //********//

    public static MainActivity getInstance() {
        return videoReaderExoplayerInstance;
    }
    private static MainActivity videoReaderExoplayerInstance;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "APP - On Create");
        setContentView(R.layout.activity_main);

        videoReaderExoplayerInstance = this;

        // Hide top bar and status bar
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Prevent screen from sleeping
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
        } else {
            permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_ONE_SHOT);
        }

        usbDeviceBroadcastReceiver = new UsbDeviceBroadcastReceiver(this);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbDeviceBroadcastReceiver, filter);
        IntentFilter filterDetached = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDeviceBroadcastReceiver, filterDetached);

        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        watermarkView = findViewById(R.id.watermarkView);
        overlayView = findViewById(R.id.overlayView);
        fpvView = findViewById(R.id.fpvView);

        textBitmap = findViewById(R.id.imageViewBitmap);

        settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), SettingsActivity.class);
            v.getContext().startActivity(intent);
        });

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Enable resizing animations
        ((ViewGroup) findViewById(R.id.mainLayout)).getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        setupGestureDetectors();

        mUsbMaskConnection = new UsbMaskConnection();
        Handler videoReaderEventListener = new Handler(this.getMainLooper(), msg -> onVideoReaderEvent((VideoReaderExoplayer.VideoReaderEventMessageCode) msg.obj));

        mVideoReader = new VideoReaderExoplayer(fpvView, this, videoReaderEventListener);
        mVideoReader.set_drone_preset("legacy_buffered"); //aggressive -> song //legacy_buffered-> p30 //samsung, conservative -> (tablet and phone)

        if (!usbConnected) {
            if (searchDevice()) {
                connect("start");
            } else {
                showOverlay(R.string.waiting_for_usb_device, OverlayStatus.Disconnected);
            }
        }
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    public void getFrame(Bitmap bitmap) {

        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        //Bitmap bitmap = mVideoReader.ConvertToBitmap();

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                previewHeight = bitmap.getHeight();
                previewWidth = bitmap.getWidth();
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new Size(previewWidth, previewHeight), 90);

            }
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            return;
        }

        isProcessingFrame = true;

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        isProcessingFrame = false;
                    }
                };

        processImage();

        //Toast.makeText(MainActivity.this, "Show Image!", Toast.LENGTH_SHORT).show();

    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handlerTF != null) {
            handlerTF.post(r);
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    private void setupGestureDetectors() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return super.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return super.onDoubleTap(e);
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleGestureDetector.onTouchEvent(event);

        return super.onTouchEvent(event);
    }

    private void updateWatermark() {
        if (overlayView.getVisibility() == View.VISIBLE) {
            watermarkView.setAlpha(0);
            return;
        }

        if (sharedPreferences.getBoolean(ShowWatermark, true)) {
            watermarkView.setAlpha(0.7F);

        } else {
            watermarkView.setAlpha(0F);
        }
    }

    private void updateTensorFlowSettings() {
        MINIMUM_CONFIDENCE_TF_OD_API = (float) sharedPreferences.getInt("SeekBarMinimumConfidenceInterval", 60) / 100;

        classesEnabled.clear();

        if (sharedPreferences.getBoolean("person", false)) {
            classesEnabled.addAll(Arrays.asList(classesElementsPerson));
        }

        if (sharedPreferences.getBoolean("vehicle", false)) {
            classesEnabled.addAll(Arrays.asList(classesElementsVehicle));
        }

        if (sharedPreferences.getBoolean("weapon", false)) {
            classesEnabled.addAll(Arrays.asList(classesElementsWeapon));
        }
    }

    private void cancelButtonAnimation() {
        Handler handler = settingsButton.getHandler();
        if (handler != null) {
            settingsButton.getHandler().removeCallbacksAndMessages(null);
        }
    }

    private void showSettingsButton() {
        cancelButtonAnimation();

        if (overlayView.getVisibility() == View.VISIBLE) {
            buttonAlpha = 1;
            settingsButton.setAlpha(1);
        }
    }

    @Override
    public void usbDeviceApproved(UsbDevice device) {
        Log.i(TAG, "USB - usbDevice approved");
        usbDevice = device;
        showOverlay(R.string.usb_device_approved, OverlayStatus.Connected);
        connect("resume");
    }

    @Override
    public void usbDeviceDetached() {
        Log.i(TAG, "USB - usbDevice detached");
        showOverlay(R.string.usb_device_detached_waiting, OverlayStatus.Disconnected);
        this.onStop();
    }

    private boolean searchDevice() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.size() <= 0) {
            usbDevice = null;
            return false;
        }

        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID) {
                if (usbManager.hasPermission(device)) {
                    Log.i(TAG, "USB - usbDevice attached");
                    showOverlay(R.string.usb_device_found, OverlayStatus.Connected);
                    usbDevice = device;
                    return true;
                }

                usbManager.requestPermission(device, permissionIntent);
            }
        }

        return false;
    }

    private void connect(String connect_type) {
        usbConnected = true;
        mUsbMaskConnection.setUsbDevice(usbManager.openDevice(usbDevice), usbDevice);
        mVideoReader.setUsbMaskConnection(mUsbMaskConnection);
        overlayView.hide();
        mVideoReader.start(connect_type);
        updateWatermark();
        updateTensorFlowSettings();
        //autoHideSettingsButton();
        showOverlay(R.string.waiting_for_video, OverlayStatus.Connected);
    }

    public void onPreviewSizeChosen(final Size size, final int rotation) {
        float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        try {
            detector = DetectorFactory.getDetector(getAssets(), YOLOModel);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        int cropSize = detector.getInputSize();

        try {
            detector.useGpu();
            UseGPU = true;
        } catch (Exception e) {
            detector.useCPU();
            UseGPU = false;
        }

        numThreads = 1;
        detector.setNumThreads(numThreads);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                cropSize, cropSize,
                sensorOrientation, false);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> {
                    tracker.draw(canvas);
                    tracker.drawDebug(canvas);
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void processImage() {
        ++timestamp;
        long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }

        computingDetection = true;

        readyForNextImage();

        PixelCopy.request(fpvView, croppedBitmap, i ->
                Glide.with(this).load(bitmapToByte(croppedBitmap)).into(textBitmap), new Handler(Looper.getMainLooper()));

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {

                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }
                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();

                            if (location != null && result.getConfidence() >= minimumConfidence) {

                                for (int i = 0; i < classesEnabled.size(); i++) {

                                    if (result.getTitle().equals(classesEnabled.get(i))) { //|| (result.getTitle().equals("car")) || (result.getTitle().equals("motorcycle"))) {
                                        canvas.drawRect(location, paint);

                                        cropToFrameTransform.mapRect(location);

                                        result.setLocation(location);
                                        mappedRecognitions.add(result);
                                    }
                                }
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                    }
                });
    }

    private byte[] bitmapToByte(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return byteArray;
    }

    private boolean onVideoReaderEvent(VideoReaderExoplayer.VideoReaderEventMessageCode m) {

        if (VideoReaderExoplayer.VideoReaderEventMessageCode.WAITING_FOR_VIDEO.equals(m)) {
            Log.d(TAG, "event: WAITING_FOR_VIDEO");
            showOverlay(R.string.waiting_for_video, OverlayStatus.Connected);

        } else if (VideoReaderExoplayer.VideoReaderEventMessageCode.VIDEO_PLAYING.equals(m)) {
            Log.d(TAG, "event: VIDEO_PLAYING");
            hideOverlay();
        }
        return false; // false to continue listening
    }

    private void showOverlay(int textId, OverlayStatus connected) {
        overlayView.show(textId, connected);
        updateWatermark();
        updateTensorFlowSettings();
        showSettingsButton();
    }

    private void hideOverlay() {
        overlayView.hide();
        updateWatermark();
        updateTensorFlowSettings();
        showSettingsButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "APP - On Resume");

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        if (!usbConnected) {
            if (searchDevice()) {
                Log.d(TAG, "APP - On Resume usbDevice device found");
                connect("resume");
            } else {
                showOverlay(R.string.waiting_for_usb_device, OverlayStatus.Connected);
            }
        }

        settingsButton.setAlpha(1);
        updateWatermark();
        updateTensorFlowSettings();

        handlerThreadTF = new HandlerThread("inference");
        handlerThreadTF.start();
        handlerTF = new Handler(handlerThreadTF.getLooper());
    }

    @Override
    public synchronized void onStart() {
        LOGGER.d("onStart " + this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "APP - On Stop");

        mUsbMaskConnection.stop();
        mVideoReader.stop();
        usbConnected = false;
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "APP - On Pause");

        mUsbMaskConnection.stop();
        mVideoReader.stop();
        usbConnected = false;

        super.onPause();

        handlerThreadTF.quitSafely();
        try {
            handlerThreadTF.join();
            handlerThreadTF = null;
            handlerTF = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "APP - On Destroy");

        mUsbMaskConnection.stop();
        mVideoReader.stop();
        usbConnected = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}