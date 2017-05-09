package com.mauriciotogneri.ocrtest.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
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
    private TextView statusViewBottom;
    private TextView statusViewTop;
    private View resultView;
    private View progressView;
    private OcrResult lastResult;
    private boolean hasSurface;
    private TessBaseAPI baseApi; // Java interface for the Tesseract OCR engine

    private final String sourceLanguageCodeOcr = Configuration.DEFAULT_SOURCE_LANGUAGE_CODE;
    private final boolean isContinuousModeActive = Configuration.DEFAULT_TOGGLE_CONTINUOUS;

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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.capture);
        resultView = findViewById(R.id.result_view);

        statusViewBottom = (TextView) findViewById(R.id.status_view_bottom);
        registerForContextMenu(statusViewBottom);
        statusViewTop = (TextView) findViewById(R.id.status_view_top);
        registerForContextMenu(statusViewTop);

        handler = null;
        lastResult = null;
        hasSurface = false;

        TextView ocrResultView = (TextView) findViewById(R.id.ocr_result_text_view);
        registerForContextMenu(ocrResultView);
        TextView translationView = (TextView) findViewById(R.id.translation_text_view);
        registerForContextMenu(translationView);

        progressView = findViewById(R.id.indeterminate_progress_indicator_view);

        cameraManager = new CameraManager(getApplication());

        isEngineReady = false;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        resetStatusView();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
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
                initOcrEngine(storageDirectory, sourceLanguageCodeOcr);
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
    @SuppressWarnings("unused")
    void resumeContinuousDecoding()
    {
        isPaused = false;
        resetStatusView();
        setStatusViewForContinuous();
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
            handler = new CaptureActivityHandler(this, cameraManager, isContinuousModeActive);

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
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
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
            if (isContinuousModeActive)
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

        // Disable continuous mode if we're using Cube. This will prevent bad states for devices
        // with low memory that crash when running OCR with Cube, and prevent unwanted delays.
        //        if (ocrEngineMode == TessBaseAPI.OEM_CUBE_ONLY || ocrEngineMode == TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED)
        //        {
        //            Log.d(getClass().getName(), "Disabling continuous preview");
        //            isContinuousModeActive = false;
        //        }

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

        // Turn off capture-related UI elements
        statusViewBottom.setVisibility(View.GONE);
        statusViewTop.setVisibility(View.GONE);
        resultView.setVisibility(View.VISIBLE);

        ImageView bitmapImageView = (ImageView) findViewById(R.id.image_view);
        Bitmap lastBitmap = ocrResult.getBitmap();

        if (lastBitmap == null)
        {
            bitmapImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        }
        else
        {
            bitmapImageView.setImageBitmap(lastBitmap);
        }

        // Display the recognized text
        TextView sourceLanguageTextView = (TextView) findViewById(R.id.source_language_text_view);
        sourceLanguageTextView.setText(sourceLanguageCodeOcr);
        TextView ocrResultTextView = (TextView) findViewById(R.id.ocr_result_text_view);
        ocrResultTextView.setText(ocrResult.getText());
        // Crudely scale betweeen 22 and 32 -- bigger font for shorter text
        int scaledSize = Math.max(22, 32 - ocrResult.getText().length() / 4);
        ocrResultTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);

        //TextView translationLanguageLabelTextView = (TextView) findViewById(R.id.translation_language_label_text_view);
        //TextView translationLanguageTextView = (TextView) findViewById(R.id.translation_language_text_view);
        TextView translationTextView = (TextView) findViewById(R.id.translation_text_view);

        //translationLanguageLabelTextView.setVisibility(View.GONE);
        //translationLanguageTextView.setVisibility(View.GONE);
        translationTextView.setVisibility(View.GONE);
        progressView.setVisibility(View.GONE);
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

        // Send an OcrResultText object to the ViewfinderView for text rendering
        OcrResultText resultText = new OcrResultText(ocrResult.getText(),
                                                     ocrResult.getWordConfidences(),
                                                     ocrResult.getMeanConfidence(),
                                                     ocrResult.getBitmapDimensions(),
                                                     ocrResult.getRegionBoundingBoxes(),
                                                     ocrResult.getTextlineBoundingBoxes(),
                                                     ocrResult.getStripBoundingBoxes(),
                                                     ocrResult.getWordBoundingBoxes(),
                                                     ocrResult.getCharacterBoundingBoxes());

        Integer meanConfidence = ocrResult.getMeanConfidence();

        if (Configuration.CONTINUOUS_DISPLAY_RECOGNIZED_TEXT)
        {
            // Display the recognized text on the screen
            /*statusViewTop.setText(ocrResult.getText());
            int scaledSize = Math.max(22, 32 - ocrResult.getText().length() / 4);
            statusViewTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
            statusViewTop.setTextColor(Color.BLACK);
            statusViewTop.setBackgroundResource(R.color.status_top_text_background);

            statusViewTop.getBackground().setAlpha(meanConfidence * (255 / 100));*/

            String number = extractNumber(ocrResult.getText());

            if (number != null)
            {
                //Toast.makeText(this, number, Toast.LENGTH_SHORT).show();
                Intent data = new Intent();
                data.putExtra("number", number);
                setResult(Activity.RESULT_OK, data);

                finish();
            }
        }

        if (Configuration.CONTINUOUS_DISPLAY_METADATA)
        {
            // Display recognition-related metadata at the bottom of the screen
            //long recognitionTimeRequired = ocrResult.getRecognitionTimeRequired();
            //statusViewBottom.setTextSize(14);
            //statusViewBottom.setText("OCR: " + sourceLanguageCodeOcr + " - Mean confidence: " +
            //                                 meanConfidence.toString() + " - Time required: " + recognitionTimeRequired + " ms");
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

        // Reset the text in the recognized text box.
        statusViewTop.setText("");

        if (Configuration.CONTINUOUS_DISPLAY_METADATA)
        {
            // Color text delimited by '-' as red.
            //statusViewBottom.setTextSize(14);
            //CharSequence cs = setSpanBetweenTokens("OCR: " + sourceLanguageCodeOcr + " - OCR failed - Time required: "
            //                                               + obj.getTimeRequired() + " ms", "-", new ForegroundColorSpan(0xFFFF0000));
            //statusViewBottom.setText(cs);
        }
    }

    /**
     * Given either a Spannable String or a regular String and a token, apply
     * the given CharacterStyle to the span between the tokens.
     * <p>
     * NOTE: This method was adapted from:
     * http://www.androidengineer.com/2010/08/easy-method-for-formatting-android.html
     * <p>
     * <p>
     * For example, {@code setSpanBetweenTokens("Hello ##world##!", "##", new
     * ForegroundColorSpan(0xFFFF0000));} will return a CharSequence {@code
     * "Hello world!"} with {@code world} in red.
     */
    private CharSequence setSpanBetweenTokens(CharSequence text, String token,
                                              CharacterStyle... cs)
    {
        // Start and end refer to the points where the span will apply
        int tokenLen = token.length();
        int start = text.toString().indexOf(token) + tokenLen;
        int end = text.toString().indexOf(token, start);

        if (start > -1 && end > -1)
        {
            // Copy the spannable string to a mutable spannable string
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            for (CharacterStyle c : cs)
            {
                ssb.setSpan(c, start, end, 0);
            }
            text = ssb;
        }
        return text;
    }

    /**
     * Resets view elements.
     */
    private void resetStatusView()
    {
        resultView.setVisibility(View.GONE);
        if (Configuration.CONTINUOUS_DISPLAY_METADATA)
        {
            statusViewBottom.setText("");
            statusViewBottom.setTextSize(14);
            statusViewBottom.setTextColor(getResources().getColor(R.color.status_text));
            statusViewBottom.setVisibility(View.VISIBLE);
        }
        if (Configuration.CONTINUOUS_DISPLAY_RECOGNIZED_TEXT)
        {
            statusViewTop.setText("");
            statusViewTop.setTextSize(14);
            statusViewTop.setVisibility(View.VISIBLE);
        }
        lastResult = null;
    }

    /**
     * Displays an initial message to the user while waiting for the first OCR request to be
     * completed after starting realtime OCR.
     */
    void setStatusViewForContinuous()
    {
        if (Configuration.CONTINUOUS_DISPLAY_METADATA)
        {
            //statusViewBottom.setText("OCR: " + sourceLanguageCodeOcr + " - waiting for OCR...");
        }
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