package com.mauriciotogneri.ocrtest.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.mauriciotogneri.ocrtest.R;
import com.mauriciotogneri.ocrtest.camera.CameraManager;

import java.io.File;

// https://github.com/rmtheis/android-ocr
public final class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback
{
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private SurfaceHolder surfaceHolder;
    private OcrResult lastResult;
    private boolean hasSurface;
    private TessBaseAPI baseApi;

    private ProgressDialog dialog; // for initOcr - language download & unzip
    private ProgressDialog indeterminateDialog; // also for initOcr - init OCR engine
    private boolean isEngineReady;
    private boolean isPaused;

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

        cameraManager = new CameraManager(getApplication());

        isEngineReady = false;
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

    /**
     * Method to start or restart recognition after the OCR engine has been initialized,
     * or after the app regains focus. Sets state related settings and OCR engine parameters,
     * and requests camera initialization.
     */
    void resumeOCR()
    {
        Log.d(getClass().getName(), "resumeOCR()");

        // This method is called when Tesseract has already been successfully initialized, so set
        // isEngineReady = true here.
        isEngineReady = true;

        isPaused = false;

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

    /**
     * Called when the shutter button is pressed in continuous mode.
     */
    void onShutterButtonPressContinuous()
    {
        isPaused = true;
        handler.stop();
        if (lastResult != null)
        {
            handleOcrDecode(lastResult);
        }
        else
        {
            Toast toast = Toast.makeText(this, "OCR failed. Please try again.", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
            resumeContinuousDecoding();
        }
    }

    /**
     * Called to resume recognition after translation in continuous mode.
     */
    void resumeContinuousDecoding()
    {
        isPaused = false;
        resetStatusView();
        DecodeHandler.resetDecodeState();
        handler.resetState();
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

    void stopHandler()
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            // First check if we're paused in continuous mode, and if so, just unpause.
            if (isPaused)
            {
                Log.d(getClass().getName(), "only resuming continuous recognition, not quitting...");
                resumeContinuousDecoding();
                return true;
            }

            // Exit the app if we're not viewing an OCR result.
            if (lastResult == null)
            {
                setResult(RESULT_CANCELED);
                finish();
                return true;
            }
            else
            {
                // Go back to previewing in regular OCR mode.
                resetStatusView();
                if (handler != null)
                {
                    handler.sendEmptyMessage(R.id.restart_preview);
                }
                return true;
            }
        }
        else if (keyCode == KeyEvent.KEYCODE_CAMERA)
        {
            if (Configuration.DEFAULT_TOGGLE_CONTINUOUS)
            {
                onShutterButtonPressContinuous();
            }
            else
            {
                handler.hardwareShutterButtonClick();
            }
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_FOCUS)
        {
            // Only perform autofocus if user is not holding down the button.
            if (event.getRepeatCount() == 0)
            {
                cameraManager.requestAutoFocus(500L);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
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

    private void initOcrEngine(File storageRoot, String languageCode)
    {
        isEngineReady = false;

        // Set up the dialog box for the thermometer-style download progress indicator
        if (dialog != null)
        {
            dialog.dismiss();
        }
        dialog = new ProgressDialog(this);

        // Display the name of the OCR engine we're initializing in the indeterminate progress dialog box
        indeterminateDialog = new ProgressDialog(this);
        indeterminateDialog.setTitle("Please wait");
        indeterminateDialog.setMessage("Initializing OCR engine for " + languageCode + "...");
        indeterminateDialog.setCancelable(false);
        indeterminateDialog.show();

        if (handler != null)
        {
            handler.quitSynchronously();
        }

        // Start AsyncTask to install language data and init OCR
        baseApi = new TessBaseAPI();
        new OcrInitAsyncTask(this, baseApi, dialog, indeterminateDialog, languageCode, Configuration.DEFAULT_OCR_ENGINE_MODE).execute(storageRoot.toString());
    }

    /**
     * Displays information relating to the result of OCR, and requests a translation if necessary.
     *
     * @param ocrResult Object representing successful OCR results
     * @return True if a non-null result was received for OCR
     */
    boolean handleOcrDecode(OcrResult ocrResult)
    {
        lastResult = ocrResult;

        // Test whether the result is null
        if (ocrResult.getText() == null || ocrResult.getText().equals(""))
        {
            Toast toast = Toast.makeText(this, "OCR failed. Please try again.", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
            return false;
        }

        setProgressBarVisibility(false);

        return true;
    }

    /**
     * Displays information relating to the results of a successful real-time OCR request.
     *
     * @param ocrResult Object representing successful OCR results
     */
    void handleOcrContinuousDecode(OcrResult ocrResult)
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
    void handleOcrContinuousDecode(OcrResultFailure obj)
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

    public void displayProgressDialog()
    {
        indeterminateDialog = new ProgressDialog(this);
        indeterminateDialog.setTitle("Please wait");
        indeterminateDialog.setMessage("Performing OCR...");
        indeterminateDialog.setCancelable(false);
        indeterminateDialog.show();
    }

    public ProgressDialog getProgressDialog()
    {
        return indeterminateDialog;
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