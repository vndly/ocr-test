package com.mauriciotogneri.ocrtest.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
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

        hasSurface = false;
        isEngineReady = false;

        cameraManager = new CameraManager(getApplication());
    }

    private void initOcrEngine(File storageRoot, String languageCode)
    {
        isEngineReady = false;

        if (handler != null)
        {
            handler.quitSynchronously();
        }

        baseApi = new TessBaseAPI();

        // Start AsyncTask to install language data and init OCR
        new OcrInitAsyncTask(this, baseApi, languageCode, storageRoot.toString(), TessBaseAPI.OEM_TESSERACT_ONLY).execute();
    }

    /**
     * Method to start or restart recognition after the OCR engine has been initialized,
     * or after the app regains focus. Sets state related settings and OCR engine parameters,
     * and requests camera initialization.
     */
    public void resumeOCR()
    {
        isEngineReady = true;

        if (baseApi != null)
        {
            baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmopqrstuvwxyz");
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789");
        }

        if (hasSurface)
        {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        }
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

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        // only initialize the camera if the OCR engine is ready to go.
        if (!hasSurface && isEngineReady)
        {
            initCamera(holder);
        }

        hasSurface = true;

        if (handler != null)
        {
            handler.resume();
        }
    }

    /**
     * Initializes the camera and starts the handler to begin previewing.
     */
    private void initCamera(SurfaceHolder surfaceHolder)
    {
        if (surfaceHolder == null)
        {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        try
        {
            // Open and initialize the camera
            cameraManager.openDriver(surfaceHolder);

            // Creating the handler starts the preview, which can also throw a RuntimeException.
            handler = new CaptureActivityHandler(this, cameraManager);
        }
        catch (Exception ioe)
        {
            showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.previewView);
        surfaceHolder = surfaceView.getHolder();

        if (!hasSurface)
        {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        // do OCR engine initialization, if necessary
        if (baseApi == null)
        {
            // initialize the OCR engine
            File storageDirectory = getStorageDirectory();

            if (storageDirectory != null)
            {
                initOcrEngine(storageDirectory, "eng");
            }
        }
        else
        {
            // we already have the engine initialized, so just start the camera.
            resumeOCR();
        }
    }

    @Override
    protected void onPause()
    {
        if (handler != null)
        {
            if (isFinishing())
            {
                handler.quitSynchronously();
            }
            else
            {
                handler.pause();
            }
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

    @Override
    protected void onDestroy()
    {
        if (baseApi != null)
        {
            baseApi.end();
        }
        super.onDestroy();
    }

    public void stopHandler()
    {
        if (handler != null)
        {
            handler.pause();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        hasSurface = false;
    }

    @Override
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
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable.");
        }

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
        {
            try
            {
                return getExternalFilesDir(Environment.MEDIA_MOUNTED);
            }
            catch (NullPointerException e)
            {
                // We get an error here if the SD card is visible, but full
                showErrorMessage("Error", "Required external storage (such as an SD card) is full or unavailable.");
            }

        }
        else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
        {
            // We can only read the media
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable for data storage.");
        }
        else
        {
            // Something else is wrong. It may be one of many other states, but all we need
            // to know is we can neither read nor write
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable or corrupted.");
        }

        return null;
    }

    /**
     * Displays information relating to the results of a successful real-time OCR request.
     *
     * @param ocrResult Object representing successful OCR results
     */
    public void handleOcrResult(OcrResult ocrResult)
    {
        String number = extractNumber(ocrResult.text());

        if (number != null)
        {
            Intent data = new Intent();
            data.putExtra("number", number);
            setResult(Activity.RESULT_OK, data);

            finish();
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

    public void showErrorMessage(String title, String message)
    {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setOnCancelListener(new OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        CaptureActivity.this.finish();
                    }
                })
                .setPositiveButton("Done", new OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        CaptureActivity.this.finish();
                    }
                })
                .show();
    }
}