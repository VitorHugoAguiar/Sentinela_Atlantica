package com.fpvobjectdetection;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.bumptech.glide.Glide;
import com.fpvobjectdetection.env.BorderedText;
import com.fpvobjectdetection.env.ImageUtils;
import com.fpvobjectdetection.env.Utils;
import com.fpvobjectdetection.tracking.MultiBoxTracker;
import com.fpvobjectdetection.tflite.Classifier;
import com.fpvobjectdetection.tflite.DetectorFactory;
import com.fpvobjectdetection.tflite.YoloV5Classifier;
import com.fpvobjectdetection.env.Logger;
import com.fpvobjectdetection.customview.OverlayView2;

import android.graphics.Bitmap.Config;
import android.widget.ToggleButton;


import boofcv.abst.tracker.TrackerObjectQuad;
import boofcv.factory.tracker.FactoryTrackerObjectQuad;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.shapes.Quadrilateral_F64;
import georegression.struct.point.Point2D_F64;


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
    SurfaceView layout_movie_wrapper;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private SharedPreferences sharedPreferences;
    private static final String ShowWatermark = "ShowWatermark";

    //********//
    private static final Logger LOGGER = new Logger();
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private String YOLOModel = null;

    private String YOLOModelGenericN = "yolov5n-int8_416.tflite";
    private String YOLOModelGenericS = "yolov5s-int8_416.tflite";
    private String YOLOModelGenericM = "yolov5m-int8_416.tflite";

    private String YOLOModelTabletN = "yolov5n-int8_320.tflite";
    private String YOLOModelTabletS = "yolov5s-int8_320.tflite";
    private String YOLOModelTabletM = "yolov5m-int8_320.tflite";

    private String YOLOModelSongN = "yolov5n-int8_256.tflite";
    private String YOLOModelSongS = "yolov5s-int8_256.tflite";
    private String YOLOModelSongM = "yolov5m-int8_256.tflite";


    private static int numThreads = 1;
    private static boolean UseGPU = true;
    public static float MINIMUM_CONFIDENCE_TF_OD_API = 0.0f;
    private static final float TEXT_SIZE_DIP = 8;
    private Integer sensorOrientation;
    private OverlayView2 trackingOverlay;
    private YoloV5Classifier detector;
    private Bitmap croppedBitmap = null;

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

    ArrayList<String> classesEnabled = new ArrayList<>();
    String[] classesElementsPerson = {"person"};
    String[] classesElementsVehicle = {"vehicle", "motorcycle", "car"};
    String[] classesElementsWeapon = {"weapon"};

    //***************************************************************************************
    private Bitmap rgbFrameBitmap = null;

    private Runnable imageConverter;

    ByteBuffer buffer;

    byte[] croppedPixels;

    PixelCopy.OnPixelCopyFinishedListener listener;

    private Bitmap croppedBitmapClone = null;

    int cropSize;

    ImageView imgPrint;

    private Bitmap sourceBitmap;
    private Bitmap cropBitmap;
    //********//
    private Bitmap imgPrintScreen;

    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private boolean isSong = false;
    private boolean isTablet = false;
    private boolean isGeneric = true;

    Bitmap finalBitmap = null;

    Timer timer;
    TimerTask updateProfile;

    ToggleButton ToggleButtonDisableDetection;
    boolean disableDetection = true;

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
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
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

        imgPrint = (ImageView) findViewById(R.id.imgPrint);

        ConstraintLayout constraintLayout = findViewById(R.id.mainLayout);
        ConstraintSet constraintSet = new ConstraintSet();

        // Set the end constraint of your view
        //constraintSet.constrainWidth(R.id.imgPrint, 640); // set the width of the view to 640 pixels
        constraintSet.constrainHeight(R.id.imgPrint, 1280); // set the width of the view to 640 pixels
        constraintSet.connect(R.id.imgPrint, ConstraintSet.START, R.id.guideline, ConstraintSet.END, 10);


        // Apply the updated constraints to the ConstraintLayout
        constraintSet.applyTo(constraintLayout);


        //this.sourceBitmap = Utils.getBitmapFromAsset(MainActivity.this, "IMG_20230323_161815.jpg");
        //this.cropBitmap = Utils.processBitmap(sourceBitmap, 640);
        //imgPrint.setImageBitmap(cropBitmap);

        //layout_movie_wrapper = findViewById(R.id.layout_movie_wrapper);

        /*android.view.ViewGroup.LayoutParams lp = fpvView.getLayoutParams();
        lp.width = 2265;
        lp.height = 1080;
        fpvView.setLayoutParams(lp);*/

        // Set the X and Y position of the SurfaceView to be at the top left corner of the screen
        /*fpvView.setX(0);
        fpvView.setY(0);
        float aspectRatio = (float)fpvView.getWidth() / (float)fpvView.getHeight();
        // Set the LayoutParams of the SurfaceView to match the desired width and height
        fpvView.setLayoutParams(new ViewGroup.LayoutParams(fpvView.getWidth(), Math.round((float)fpvView.getWidth() * aspectRatio)));
        Toast.makeText(MainActivity.this, fpvView.getWidth() + "x" + Math.round((float)fpvView.getWidth() * aspectRatio), Toast.LENGTH_SHORT).show();*/


        //fpvView.getHolder().setFixedSize(fpvView.getWidth(), Math.round((float)fpvView.getWidth() * aspectRatio));


        settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), SettingsActivity.class);
            v.getContext().startActivity(intent);
        });

        ToggleButtonDisableDetection = (ToggleButton) findViewById(R.id.simpleToggleButton);

        ToggleButtonDisableDetection.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //String status = "ToggleButton1 : " + ToggleButtonDisableDetection.getText();
                if (isChecked) {
                    disableDetection = true;
                    //Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show(); // display the current state of toggle button's
                } else {
                    disableDetection = false;
                    //Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show(); // display the current state of toggle button's
                }
            }
        });

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Enable resizing animations
        ((ViewGroup) findViewById(R.id.mainLayout)).getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        setupGestureDetectors();

        mUsbMaskConnection = new UsbMaskConnection();
        Handler videoReaderEventListener = new Handler(this.getMainLooper(), msg -> onVideoReaderEvent((VideoReaderExoplayer.VideoReaderEventMessageCode) msg.obj));

        mVideoReader = new VideoReaderExoplayer(fpvView, this, videoReaderEventListener);

        //aggressive -> song //legacy_buffered-> p30 //samsung, conservative -> (tablet and phone)
        if (isGeneric) {
            mVideoReader.set_drone_preset("legacy_buffered");
            if (sharedPreferences.getBoolean("ModelN", false)) {
                YOLOModel = YOLOModelGenericN;
            }

            if (sharedPreferences.getBoolean("ModelS", false)) {
                YOLOModel = YOLOModelGenericS;
            }

            if (sharedPreferences.getBoolean("ModelM", false)) {
                YOLOModel = YOLOModelGenericM;
            }
        }

        if (isTablet) {
            mVideoReader.set_drone_preset("conservative");
            if (sharedPreferences.getBoolean("ModelN", false)) {
                YOLOModel = YOLOModelTabletN;
            }

            if (sharedPreferences.getBoolean("ModelS", false)) {
                YOLOModel = YOLOModelTabletS;
            }

            if (sharedPreferences.getBoolean("ModelM", false)) {
                YOLOModel = YOLOModelTabletM;
            }
        }

        if (isSong) {
            mVideoReader.set_drone_preset("aggressive");
            if (sharedPreferences.getBoolean("ModelN", false)) {
                YOLOModel = YOLOModelSongN;
            }

            if (sharedPreferences.getBoolean("ModelS", false)) {
                YOLOModel = YOLOModelSongS;
            }

            if (sharedPreferences.getBoolean("ModelM", false)) {
                YOLOModel = YOLOModelSongM;
            }
        }


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

    public int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    //Toast.makeText(MainActivity.this, "Show Image!", Toast.LENGTH_SHORT).show();
    protected synchronized void runInBackground(final Runnable r) {
        if (handlerTF != null) {
            handlerTF.post(r);
        }
    }

    protected synchronized void runInBackgroundShow(final Runnable r) {
        if (backgroundHandler != null) {
            backgroundHandler.post(r);
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

        if (sharedPreferences.getBoolean("ModelN", false)) {
            if (isGeneric) {
                YOLOModel = YOLOModelGenericN;
            }

            if (isTablet) {
                YOLOModel = YOLOModelTabletN;
            }

            if (isSong) {
                YOLOModel = YOLOModelSongN;
            }

            if (usbConnected) {
                updateActiveModel(YOLOModel);
            }
        }

        if (sharedPreferences.getBoolean("ModelS", false)) {
            if (isGeneric) {
                YOLOModel = YOLOModelGenericS;
            }

            if (isTablet) {
                YOLOModel = YOLOModelTabletS;
            }

            if (isSong) {
                YOLOModel = YOLOModelSongS;
            }
            if (usbConnected) {
                updateActiveModel(YOLOModel);
            }
        }

        if (sharedPreferences.getBoolean("ModelM", false)) {
            if (isGeneric) {
                YOLOModel = YOLOModelGenericM;
            }

            if (isTablet) {
                YOLOModel = YOLOModelTabletM;
            }

            if (isSong) {
                YOLOModel = YOLOModelSongM;
            }
            if (usbConnected) {
                updateActiveModel(YOLOModel);
            }
        }

    }

    public void updateActiveModel(String modelString) {

        //Toast.makeText(MainActivity.this, "YOLOModel_RADIOBUTTON: " + YOLOModel, Toast.LENGTH_SHORT).show();

        // Disable classifier while updating
            /*if (detector != null) {
                detector.close();
                detector = null;
            }*/

        try {
            detector = DetectorFactory.getDetector(getAssets(), modelString);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing Detector!");
            Toast toast = Toast.makeText(getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }


        cropSize = detector.getInputSize();

        try {
            detector.useGpu();
            UseGPU = true;
        } catch (Exception e) {
            detector.useCPU();
            UseGPU = false;
        }

        detector.setNumThreads(numThreads);

        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        /*frameToCropTransform = ImageUtils.getTransformationMatrix(previewWidth, previewHeight, cropSize, cropSize, sensorOrientation, false);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);*/

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

    public byte[] getBitmapRgbBytes(Bitmap bitmap) {
        int previewWidth = bitmap.getWidth();
        int previewHeight = bitmap.getHeight();

        int[] pixels = new int[previewWidth * previewHeight];
        bitmap.getPixels(pixels, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        byte[] rgbBytes = new byte[pixels.length * 3];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            rgbBytes[i * 3] = (byte) Color.red(pixel);
            rgbBytes[i * 3 + 1] = (byte) Color.green(pixel);
            rgbBytes[i * 3 + 2] = (byte) Color.blue(pixel);
        }

        return rgbBytes;
    }

    public byte[] bitmapToBytes(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return byteArray;
    }

    public static void copyPixels(Bitmap src, int[] dst) {
        src.getPixels(dst, 0, src.getWidth(), 0, 0, src.getWidth(), src.getHeight());
    }

    public static void convertRGBToARGB(int[] src, Bitmap dst) {
        dst.setPixels(src, 0, dst.getWidth(), 0, 0, dst.getWidth(), dst.getHeight());
    }

    public static Bitmap drawBoundingBox(Bitmap bitmap, RectF location) {
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        canvas.drawRect(location, paint);
        return mutableBitmap;
    }

    public static Bitmap cropAndPadBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        double aspectRatio = (double) width / (double) height;
        double targetAspectRatio = (double) targetWidth / (double) targetHeight;

        int x = 0;
        int y = 0;
        int w = width;
        int h = height;

        if (aspectRatio > targetAspectRatio) {
            h = (int) Math.round(w / targetAspectRatio);
            y = (height - h) / 2;
        } else if (aspectRatio < targetAspectRatio) {
            w = (int) Math.round(h * targetAspectRatio);
            x = (width - w) / 2;
        }

        Bitmap cropAndPadBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cropAndPadBitmap);
        canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(bitmap, new Rect(x, y, x + w, y + h), new Rect(0, 0, targetWidth, targetHeight), null);

        return cropAndPadBitmap;
    }

    private byte[] bitmapToByte(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return byteArray;
    }

    private Bitmap getZoomedBitmap(float zoomScale, Bitmap bmp) {

        if (bmp != null) {
            float xPercentage = (int) ((1 - zoomScale) / 2 * bmp.getWidth());
            float yPercentage = (int) ((1 - zoomScale) / 2 * bmp.getHeight());
            bmp.setDensity(Bitmap.DENSITY_NONE);

            //Set the default values in case of bad input
            zoomScale = (zoomScale < 0.0f || zoomScale > 10.0f) ? 2.0f : zoomScale;
            xPercentage = (xPercentage < 0.0f || xPercentage > 100.0f) ? 50.0f : xPercentage;
            yPercentage = (yPercentage < 0.0f || yPercentage > 100.0f) ? 50.0f : yPercentage;

            float originalWidth = bmp.getWidth();
            float originalHeight = bmp.getHeight();

            //Get the new sizes based on zoomScale
            float newWidth = originalWidth / zoomScale;
            float newHeight = originalHeight / zoomScale;

            //get the new X/Y positions based on x/yPercentage
            float newX = (originalWidth * xPercentage / 100) - (newWidth / 2);
            float newY = (originalHeight * yPercentage / 100) - (newHeight / 2);

            //Make sure the x/y values are not lower than 0
            newX = (newX < 0) ? 0 : newX;
            newY = (newY < 0) ? 0 : newY;

            //make sure the image does not go over the right edge
            while ((newX + newWidth) > originalWidth) {
                newX -= 2;
            }

            //make sure the image does not go over the bottom edge
            while ((newY + newHeight) > originalHeight) {
                newY -= 2;
            }

            return Bitmap.createBitmap(bmp, Math.round(newX), Math.round(newY), Math.round(newWidth), Math.round(newHeight));
        }

        return null;
    }

    int countFile = 0;

    public Bitmap scaleBitmap(Bitmap bitmap, float scaleFactor) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int scaledWidth = (int) (width * scaleFactor);
        int scaledHeight = (int) (height * scaleFactor);

        float pivotX = scaledWidth / 2f;
        float pivotY = scaledHeight / 2f;

        Matrix matrix = new Matrix();
        matrix.setScale(scaleFactor, scaleFactor, pivotX, pivotY);

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    public void exoplayerScreenshot() {
        timer = new Timer();

        updateProfile = new TimerTask() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            public void run() {

                if (isProcessingFrame) {
                    LOGGER.w("Dropping frame!");
                    return;
                }

                listener = copyResult -> {
                };

                try {
                    // Initialize the storage bitmaps once when the resolution is known.
                    if (rgbBytes == null) {
                        previewHeight = fpvView.getHeight();
                        previewWidth = fpvView.getWidth();
                        rgbBytes = new int[previewWidth * previewHeight];
                        onPreviewSizeChosen(new Size(previewWidth, previewHeight), 270);
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

                processImageSong();

                runInBackgroundShow(new Runnable() {
                    @Override
                    public void run() {

                        //listener = copyResult -> {};
                        PixelCopy.request(fpvView, rgbFrameBitmap, listener, new Handler());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                imgPrint.setImageBitmap(croppedBitmap);
                                //Toast.makeText(MainActivity.this, Bitmap.createBitmap(rgbFrameBitmap, 360, 0, 720, 720).getWidth() + "x" + Bitmap.createBitmap(rgbFrameBitmap, 360, 0, 720, 720).getHeight(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });

            }
        };

    }

    public static int calculateInSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        final float srcAspect = (float) srcWidth / srcHeight;
        final float dstAspect = (float) dstWidth / dstHeight;

        if (srcAspect > dstAspect) {
            return Math.round((float) srcHeight / (float) dstHeight);
        } else {
            return Math.round((float) srcWidth / (float) dstWidth);
        }
    }

    public Bitmap convertTo16x9(Bitmap bitmap) {
        float widthAspectRatio = 32f;
        float heightAspectRatio = 9f; //5

        // Calculate the desired height based on 16:9 aspect ratio
        int height = Math.round(bitmap.getWidth() * heightAspectRatio / widthAspectRatio);

        // Check if the original width is less than or equal to the maximum width
        if (bitmap.getWidth() <= rgbFrameBitmap.getWidth()) {
            // Create a new bitmap with the same width and the desired height
            return Bitmap.createScaledBitmap(bitmap, bitmap.getWidth(), height, true);
        } else {
            // Calculate the desired width based on the maximum width and the aspect ratio
            int width = Math.round(height * widthAspectRatio / heightAspectRatio);

            // Create a new bitmap with the desired width and height
            return Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void processImageSong() {

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

        Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {

                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

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

                                for (int k = 0; k < classesEnabled.size(); k++) {
                                    if (result.getTitle().equals(classesEnabled.get(k))) { //|| (result.getTitle().equals("car")) || (result.getTitle().equals("motorcycle"))) {

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

    public void onPreviewFrame() {


        //imgPrintScreen = Bitmap.createBitmap(imgPrint.getWidth(), imgPrint.getHeight(), Bitmap.Config.ARGB_8888);
        //Canvas canvas = new Canvas(imgPrintScreen);
        //imgPrint.draw(canvas);

        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        listener = copyResult -> {
        };


        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                previewHeight = fpvView.getHeight();
                previewWidth = fpvView.getWidth();

                rgbBytes = new int[previewWidth * previewHeight];
                if (isSong) {
                    onPreviewSizeChosen(new Size(previewWidth, previewHeight), 270);
                } else {
                    onPreviewSizeChosen(new Size(previewWidth, previewHeight), 90);
                }
            }
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            return;
        }

        // Create a copy of the screenshot bitmap
        //Bitmap screenshotCopy = screenshot.copy(screenshot.getConfig(), true);

        isProcessingFrame = true;

        runInBackgroundShow(new Runnable() {
            @Override
            public void run() {

                //listener = copyResult -> {};
                PixelCopy.request(fpvView, rgbFrameBitmap, listener, new Handler());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        imgPrint.setImageBitmap(croppedBitmap);
                        //Toast.makeText(MainActivity.this, Bitmap.createBitmap(rgbFrameBitmap, 360, 0, 720, 720).getWidth() + "x" + Bitmap.createBitmap(rgbFrameBitmap, 360, 0, 720, 720).getHeight(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        imageConverter = new Runnable() {
            @Override
            public void run() {


                //screenshotCopy.copyPixelsFromBuffer(ByteBuffer.wrap(bytes));
                //croppedBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(croppedPixels));

                /*ByteBuffer buffer = ByteBuffer.allocate(croppedBitmapClone.getByteCount());
                croppedBitmapClone.copyPixelsToBuffer(buffer);
                croppedPixels = buffer.array();

                croppedBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(croppedPixels));*/

                //rgbFrameBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(croppedPixels));


            }
        };

        postInferenceCallback = new Runnable() {
            @Override
            public void run() {
                // Convert the screenshot bitmap to bytes
                /*ByteBuffer buffer = ByteBuffer.allocate(screenshot.getByteCount());
                screenshot.copyPixelsToBuffer(buffer);
                byte[] bytes = buffer.array();*/

                /*buffer = ByteBuffer.allocate(croppedBitmapClone.getByteCount());
                croppedBitmapClone.copyPixelsToBuffer(buffer);
                croppedPixels = buffer.array();*/




                /*//croppedBitmap = cropAndPadBitmap(croppedBitmap,640,640);
                rgbFrameBitmap = croppedBitmap.copy(croppedBitmap.getConfig(), true);
                // Create a new Canvas object to draw the transformed bitmap onto the croppedBitmap
                Canvas canvas = new Canvas(croppedBitmap);
                // Use the canvas to draw the original bitmap onto the croppedBitmap using the frameToCropTransform matrix
                canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);*/


                isProcessingFrame = false;
            }
        };

        processImage();
    }

    public void onPreviewSizeChosen(final Size size, final int rotation) {
        float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        //Toast.makeText(MainActivity.this, "YOLOModel_START: " + YOLOModel, Toast.LENGTH_SHORT).show();


        try {
            detector = DetectorFactory.getDetector(getAssets(), YOLOModel);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing Detector!");
            Toast toast = Toast.makeText(getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        cropSize = detector.getInputSize();

        try {
            detector.useGpu();
            UseGPU = true;
        } catch (Exception e) {
            detector.useCPU();
            UseGPU = false;
        }

        detector.setNumThreads(numThreads);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);


        //croppedBitmapClone = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

        //Toast.makeText(MainActivity.this, cropSize + "x" + cropSize, Toast.LENGTH_SHORT).show();

        frameToCropTransform = ImageUtils.getTransformationMatrix(previewWidth, previewHeight, cropSize, cropSize, sensorOrientation, false);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);


        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(canvas -> {
            tracker.draw(canvas);
            //tracker.drawDebug(canvas);
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

        imageConverter.run();

        readyForNextImage();


        //Matrix matrix = new Matrix();
        //matrix.setScale(2.5f, 1f);
        //canvas.setMatrix(matrix);

        /*int borderWidth = 400; // The width of the border to be cropped
        int croppedWidth = rgbFrameBitmap.getWidth() - 2 * borderWidth;
        int croppedHeight = rgbFrameBitmap.getHeight() - 2 * borderWidth;

        Bitmap croppedBitmapSize = Bitmap.createBitmap(rgbFrameBitmap, borderWidth, borderWidth, croppedWidth, croppedHeight);*/


        Bitmap targetBitmap = convertTo16x9(rgbFrameBitmap); //BitmapUtils.lum(rgbFrameBitmap, 120)


        //Toast.makeText(MainActivity.this, targetBitmap.getWidth() + "x" + targetBitmap.getHeight(), Toast.LENGTH_SHORT).show();

        /*float scaleFactor = 1f;
        int centerX = targetBitmap.getWidth()/4;
        int centerY = targetBitmap.getHeight()/4;

        int newWidth = (int) (targetBitmap.getWidth() * scaleFactor);
        int newHeight = (int) (targetBitmap.getHeight() * scaleFactor);

        float translateX = centerX - targetBitmap.getWidth()/2f;
        float translateY = centerY - targetBitmap.getHeight()/2f;

        Matrix matrix = new Matrix();
        matrix.postTranslate(translateX, translateY);
        matrix.postScale(scaleFactor, scaleFactor);

        Bitmap zoomedBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        //zoomedBitmap = Bitmap.createScaledBitmap(zoomedBitmap, zoomedBitmap.getWidth(), zoomedBitmap.getHeight(), true);

        Canvas canvas2 = new Canvas(zoomedBitmap);
        canvas2.drawBitmap(targetBitmap, matrix, null);*/

        // adjust object detection rectangles
        /*float scaleX = (float) croppedBitmap.getWidth() / targetBitmap.getWidth();
        float scaleY = (float) croppedBitmap.getHeight() / targetBitmap.getHeight();
        Matrix frameToCropTransform = new Matrix();
        frameToCropTransform.setScale(scaleX, scaleY);
        frameToCropTransform.postTranslate(centerX - targetBitmap.getWidth()/4, centerY - targetBitmap.getHeight()/4);*/

        /*Matrix frameToCropTransform2 = ImageUtils.getTransformationMatrix(targetBitmap.getWidth(), targetBitmap.getHeight(), cropSize, cropSize, sensorOrientation, false);

        Matrix cropToFrameTransform2 = new Matrix();
        frameToCropTransform2.invert(cropToFrameTransform2);*/

        /*BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inSampleSize = calculateInSampleSize(zoomedBitmap.getWidth(), zoomedBitmap.getHeight(), newWidth, newHeight);
        Bitmap zoomedBitmap2 = Bitmap.createScaledBitmap(zoomedBitmap, newWidth, newHeight, true);


        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);*/


        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);


        Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(targetBitmap, frameToCropTransform, paint);

        //soften
        //canvas.drawBitmap(targetBitmap, frameToCropTransform, null);


        runInBackground(new Runnable() {
            @Override
            public void run() {

                final List<Classifier.Recognition> results = detector.recognizeImage(BitmapUtils.saturation((BitmapUtils.lum(croppedBitmap, 150)), 500));

                //Toast.makeText(MainActivity.this, croppedBitmap.getWidth() + "x" + croppedBitmap.getWidth(), Toast.LENGTH_SHORT).show();

                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                switch (MODE) {
                    case TF_OD_API:
                        minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        break;
                }
                final List<Classifier.Recognition> mappedRecognitions = new LinkedList<Classifier.Recognition>();

                for (final Classifier.Recognition result : results) {
                    if (disableDetection) {
                        final RectF location = result.getLocation();

                        if (location != null && result.getConfidence() >= minimumConfidence) {

                            for (int k = 0; k < classesEnabled.size(); k++) {

                                if (result.getTitle().equals(classesEnabled.get(k))) { //|| (result.getTitle().equals("car")) || (result.getTitle().equals("motorcycle"))) {

                                    float heightRatio = (float) rgbFrameBitmap.getHeight() / (float) targetBitmap.getHeight();
                                    float widthRatio = (float) rgbFrameBitmap.getWidth() / (float) targetBitmap.getWidth();

                                    float originalHeight = location.height() * heightRatio;
                                    float originalWidth = location.width() * widthRatio;
                                    float originalTop = location.top * heightRatio;
                                    float originalLeft = location.left * widthRatio;

                                    // Calculate the original size and position of the rectangle
                                /*float originalHeight = location.height() * heightRatio / scaleFactor;
                                float originalWidth = location.width() * widthRatio / scaleFactor;
                                float originalTop = (location.top - translateY) * heightRatio / scaleFactor;
                                float originalLeft = (location.left - translateX) * widthRatio / scaleFactor;*/

                                /*cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);*/

                                    // Create a new RectF with the converted coordinates
                                    RectF originalLocation = new RectF(
                                            originalLeft, originalTop,
                                            originalLeft + originalWidth, originalTop + originalHeight);

                                    cropToFrameTransform.mapRect(originalLocation);
                                    result.setLocation(originalLocation);
                                    mappedRecognitions.add(result);


                                    //Toast.makeText(MainActivity.this, "Object Detected! " + classesEnabled.get(k), Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                }

                tracker.trackResults(mappedRecognitions, currTimestamp);

                trackingOverlay.postInvalidate();

                computingDetection = false;
                //Toast.makeText(MainActivity.this, "Empty", Toast.LENGTH_SHORT).show();
                // Notify the PixelCopy operation that the processing is finished


                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        /*trackingOverlay.addCallback(
                                new OverlayView2.DrawCallback() {
                                    @Override
                                    public void drawCallback(final Canvas canvas) {
                                        tracker.draw(canvas);
                                    }
                                });*/

                        //PixelCopy.request(fpvView, rgbFrameBitmap, listener, new Handler());


                        //imgPrint.setImageBitmap(croppedBitmap);
                        //Toast.makeText(MainActivity.this, fpvView.getWidth() + "x" + fpvView.getHeight(), Toast.LENGTH_SHORT).show();
                        //Toast.makeText(MainActivity.this, croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private boolean onVideoReaderEvent(VideoReaderExoplayer.VideoReaderEventMessageCode m) {

        if (VideoReaderExoplayer.VideoReaderEventMessageCode.WAITING_FOR_VIDEO.equals(m)) {
            Log.d(TAG, "event: WAITING_FOR_VIDEO");
            showOverlay(R.string.waiting_for_video, OverlayStatus.Connected);

        } else if (VideoReaderExoplayer.VideoReaderEventMessageCode.VIDEO_PLAYING.equals(m)) {
            Log.d(TAG, "event: VIDEO_PLAYING");
            hideOverlay();
        }

        //if (isSong) {
        //    exoplayerScreenshot();
        //    timer.scheduleAtFixedRate(updateProfile, 0, 10);
        //}

        return false; // false to continue listening
    }

    private void showOverlay(int textId, OverlayStatus connected) {
        overlayView.show(textId, connected);
        updateWatermark();
        //updateTensorFlowSettings();
        showSettingsButton();
    }

    private void hideOverlay() {
        overlayView.hide();
        updateWatermark();
        //updateTensorFlowSettings();
        showSettingsButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "APP - On Resume");

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
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
        //updateTensorFlowSettings();

        handlerThreadTF = new HandlerThread("inference");
        handlerThreadTF.start();
        handlerTF = new Handler(handlerThreadTF.getLooper());

        backgroundThread = new HandlerThread("show_inference");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
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


        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
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