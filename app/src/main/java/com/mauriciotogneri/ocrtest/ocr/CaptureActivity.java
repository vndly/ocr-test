package com.mauriciotogneri.ocrtest.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.mauriciotogneri.ocrtest.R;
import com.mauriciotogneri.ocrtest.camera.CameraManager;

import java.io.File;

public final class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback
{
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private SurfaceHolder surfaceHolder;
    private OcrResult lastResult;
    private TessBaseAPI baseApi;

    private boolean hasSurface;
    private boolean isEngineReady;

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.capture);

        handler = null;
        lastResult = null;

        hasSurface = false;
        isEngineReady = false;

        cameraManager = new CameraManager(getApplication());
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        resetStatusView();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.previewView);
        surfaceHolder = surfaceView.getHolder();

        if (!hasSurface)
        {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        // Do OCR engine initialization, if necessary
        if (baseApi == null)
        {
            // Initialize the OCR engine
            File storageDirectory = getStorageDirectory();
            if (storageDirectory != null)
            {
                initOcrEngine(storageDirectory, Configuration.DEFAULT_SOURCE_LANGUAGE_CODE);
            }
        }
        else
        {
            // We already have the engine initialized, so just start the camera.
            resumeOCR();
        }
    }

    private void initOcrEngine(File storageRoot, String languageCode)
    {
        isEngineReady = false;

        if (handler != null)
        {
            handler.quitSynchronously();
        }

        // Start AsyncTask to install language data and init OCR
        baseApi = new TessBaseAPI();
        new OcrInitAsyncTask(this, baseApi, languageCode, storageRoot.toString(), Configuration.DEFAULT_OCR_ENGINE_MODE).execute();
    }

    public Handler getHandler()
    {
        return handler;
    }

    public TessBaseAPI getBaseApi()
    {
        return baseApi;
    }

    public CameraManager getCameraManager()
    {
        return cameraManager;
    }

    /**
     * Method to start or restart recognition after the OCR engine has been initialized,
     * or after the app regains focus. Sets state related settings and OCR engine parameters,
     * and requests camera initialization.
     */
    public void resumeOCR()
    {
        Log.d(getClass().getName(), "resumeOCR()");

        // This method is called when Tesseract has already been successfully initialized, so set
        // isEngineReady = true here.
        isEngineReady = true;

        if (handler != null)
        {
            handler.resetState();
        }
        if (baseApi != null)
        {
            baseApi.setPageSegMode(Configuration.DEFAULT_PAGE_SEGMENTATION_MODE);
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, Configuration.DEFAULT_BLACKLIST);
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, Configuration.DEFAULT_WHITELIST);
        }

        if (hasSurface)
        {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        Log.d(getClass().getName(), "surfaceCreated()");

        if (holder == null)
        {
            Log.e(getClass().getName(), "surfaceCreated gave us a null surface");
        }

        // Only initialize the camera if the OCR engine is ready to go.
        if (!hasSurface && isEngineReady)
        {
            Log.d(getClass().getName(), "surfaceCreated(): calling initCamera()...");
            initCamera(holder);
        }
        hasSurface = true;
    }

    /**
     * Initializes the camera and starts the handler to begin previewing.
     */
    private void initCamera(SurfaceHolder surfaceHolder)
    {
        Log.d(getClass().getName(), "initCamera()");
        if (surfaceHolder == null)
        {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        try
        {
            // Open and initialize the camera
            cameraManager.openDriver(surfaceHolder);

            // Creating the handler starts the preview, which can also throw a RuntimeException.
            handler = new CaptureActivityHandler(this, cameraManager, Configuration.DEFAULT_TOGGLE_CONTINUOUS);

        }
        catch (Exception ioe)
        {
            showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
        }
    }

    @Override
    protected void onPause()
    {
        if (handler != null)
        {
            handler.quitSynchronously();
        }

        // Stop using the camera, to avoid conflicting with other camera-based apps
        cameraManager.closeDriver();

        if (!hasSurface)
        {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.previewView);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    public void stopHandler()
    {
        if (handler != null)
        {
            handler.stop();
        }
    }

    @Override
    protected void onDestroy()
    {
        if (baseApi != null)
        {
            baseApi.end();
        }
        super.onDestroy();
    }

    public void surfaceDestroyed(SurfaceHolder holder)
    {
        hasSurface = false;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
    }

    /**
     * Finds the proper location on the SD card where we can save files.
     */
    private File getStorageDirectory()
    {
        String state = null;
        try
        {
            state = Environment.getExternalStorageState();
        }
        catch (RuntimeException e)
        {
            Log.e(getClass().getName(), "Is the SD card visible?", e);
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable.");
        }

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
        {

            // We can read and write the media
            //    	if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) > 7) {
            // For Android 2.2 and above

            try
            {
                return getExternalFilesDir(Environment.MEDIA_MOUNTED);
            }
            catch (NullPointerException e)
            {
                // We get an error here if the SD card is visible, but full
                Log.e(getClass().getName(), "External storage is unavailable");
                showErrorMessage("Error", "Required external storage (such as an SD card) is full or unavailable.");
            }

        }
        else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
        {
            // We can only read the media
            Log.e(getClass().getName(), "External storage is read-only");
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable for data storage.");
        }
        else
        {
            // Something else is wrong. It may be one of many other states, but all we need
            // to know is we can neither read nor write
            Log.e(getClass().getName(), "External storage is unavailable");
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable or corrupted.");
        }
        return null;
    }

    /**
     * Displays information relating to the results of a successful real-time OCR request.
     *
     * @param ocrResult Object representing successful OCR results
     */
    public void handleOcrContinuousDecode(OcrResult ocrResult)
    {
        lastResult = ocrResult;

        if (Configuration.CONTINUOUS_DISPLAY_RECOGNIZED_TEXT)
        {
            String number = extractNumber(ocrResult.getText());

            if (number != null)
            {
                Intent data = new Intent();
                data.putExtra("number", number);
                setResult(Activity.RESULT_OK, data);

                finish();
            }
        }
    }

    private String extractNumber(String text)
    {
        String[] parts = text.split("\\W+");

        for (String part : parts)
        {
            String trimmed = part.trim();

            if ((trimmed.length() == 20) && (isAllDigits(trimmed)))
            {
                return trimmed;
            }
        }

        return null;
    }

    private boolean isAllDigits(String number)
    {
        for (int i = 0; i < number.length(); i++)
        {
            if (!Character.isDigit(number.charAt(i)))
            {
                return false;
            }
        }

        return !number.isEmpty();
    }

    /**
     * Version of handleOcrContinuousDecode for failed OCR requests. Displays a failure message.
     *
     * @param obj Metadata for the failed OCR request.
     */
    public void handleOcrContinuousDecode(OcrResultFailure obj)
    {
        lastResult = null;
    }

    /**
     * Resets view elements.
     */
    private void resetStatusView()
    {
        lastResult = null;
    }

    public void showErrorMessage(String title, String message)
    {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setOnCancelListener(new FinishListener(this))
                .setPositiveButton("Done", new FinishListener(this))
                .show();
    }
}