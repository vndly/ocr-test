package com.mauriciotogneri.ocrtest;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;

/**
 * Installs the language data required for OCR, and initializes the OCR engine using a background
 * thread.
 */
final class OcrInitAsyncTask extends AsyncTask<String, String, Boolean>
{
    private static final String TAG = OcrInitAsyncTask.class.getSimpleName();

    private Context context;
    private CaptureActivity activity;
    private TessBaseAPI baseApi;
    private ProgressDialog dialog;
    private ProgressDialog indeterminateDialog;
    private final String languageCode;
    private int ocrEngineMode;

    /**
     * AsyncTask to asynchronously download data and initialize Tesseract.
     *
     * @param activity            The calling activity
     * @param baseApi             API to the OCR engine
     * @param dialog              Dialog box with thermometer progress indicator
     * @param indeterminateDialog Dialog box with indeterminate progress indicator
     * @param languageCode        ISO 639-2 OCR language code
     * @param languageName        Name of the OCR language, for example, "English"
     * @param ocrEngineMode       Whether to use Tesseract, Cube, or both
     */
    OcrInitAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, ProgressDialog dialog,
                     ProgressDialog indeterminateDialog, String languageCode, String languageName,
                     int ocrEngineMode)
    {
        this.activity = activity;
        this.context = activity;
        this.baseApi = baseApi;
        this.dialog = dialog;
        this.indeterminateDialog = indeterminateDialog;
        this.languageCode = languageCode;
        this.ocrEngineMode = ocrEngineMode;
    }

    @Override
    protected void onPreExecute()
    {
        super.onPreExecute();
        dialog.setTitle("Please wait");
        dialog.setMessage("Checking for data installation...");
        dialog.setIndeterminate(false);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setCancelable(false);
        dialog.show();
        activity.setButtonVisibility(false);
    }

    /**
     * In background thread, perform required setup, and request initialization of
     * the OCR engine.
     *
     * @param params [0] Pathname for the directory for storing language data files to the SD card
     */
    protected Boolean doInBackground(String... params)
    {
        // Check whether we need Cube data or Tesseract data.
        // Example Cube data filename: "tesseract-ocr-3.01.eng.tar"
        // Example Tesseract data filename: "eng.traineddata"
        String destinationFilenameBase = languageCode + ".traineddata";
        boolean isCubeSupported = false;
        for (String s : CaptureActivity.CUBE_SUPPORTED_LANGUAGES)
        {
            if (s.equals(languageCode))
            {
                isCubeSupported = true;
            }
        }

        // Check for, and create if necessary, folder to hold model data
        String destinationDirBase = params[0]; // The storage directory, minus the
        // "tessdata" subdirectory
        File tessdataDir = new File(destinationDirBase + File.separator + "tessdata");
        if (!tessdataDir.exists() && !tessdataDir.mkdirs())
        {
            Log.e(TAG, "Couldn't make directory " + tessdataDir);
            return false;
        }

        // Check if an incomplete download is present. If a *.download file is there, delete it and
        // any (possibly half-unzipped) Tesseract and Cube data files that may be there.
        File incomplete = new File(tessdataDir, destinationFilenameBase + ".download");
        File tesseractTestFile = new File(tessdataDir, languageCode + ".traineddata");
        if (incomplete.exists())
        {
            incomplete.delete();
            if (tesseractTestFile.exists())
            {
                tesseractTestFile.delete();
            }
        }

        // Check whether all Cube data files have already been installed
        boolean isAllCubeDataInstalled = false;

        // If language data files are not present, install them
        if (!tesseractTestFile.exists()
                || (isCubeSupported && !isAllCubeDataInstalled))
        {
            Log.d(TAG, "Language data for " + languageCode + " not found in " + tessdataDir.toString());

            // Check assets for language data to install. If not present, download from Internet
            try
            {
                Log.d(TAG, "Checking for language data (" + destinationFilenameBase
                        + ".zip) in application assets...");
            }
            catch (Exception e)
            {
                Log.e(TAG, "Got exception", e);
            }
        }
        else
        {
            Log.d(TAG, "Language data for " + languageCode + " already installed in "
                    + tessdataDir.toString());
        }

        // If OSD data file is not present, download it
        File osdFile = new File(tessdataDir, CaptureActivity.OSD_FILENAME_BASE);
        if (!osdFile.exists())
        {
            // Check assets for language data to install. If not present, download from Internet
            try
            {
                // Check for, and delete, partially-downloaded OSD files
                String[] badFiles = {CaptureActivity.OSD_FILENAME + ".gz.download",
                        CaptureActivity.OSD_FILENAME + ".gz", CaptureActivity.OSD_FILENAME};
                for (String filename : badFiles)
                {
                    File file = new File(tessdataDir, filename);
                    if (file.exists())
                    {
                        file.delete();
                    }
                }

                Log.d(TAG, "Checking for OSD data (" + CaptureActivity.OSD_FILENAME_BASE
                        + ".zip) in application assets...");
            }
            catch (Exception e)
            {
                Log.e(TAG, "Got exception", e);
            }
        }
        else
        {
            Log.d(TAG, "OSD file already present in " + tessdataDir.toString());
        }

        // Dismiss the progress dialog box, revealing the indeterminate dialog box behind it
        try
        {
            dialog.dismiss();
        }
        catch (IllegalArgumentException e)
        {
            // Catch "View not attached to window manager" error, and continue
        }

        // Initialize the OCR engine
        if (baseApi.init(destinationDirBase + File.separator, languageCode, ocrEngineMode))
        {
            return true;
        }
        return false;
    }

    /**
     * Update the dialog box with the latest incremental progress.
     *
     * @param message [0] Text to be displayed
     * @param message [1] Numeric value for the progress
     */
    @Override
    protected void onProgressUpdate(String... message)
    {
        super.onProgressUpdate(message);

        int percentComplete = Integer.parseInt(message[1]);
        dialog.setMessage(message[0]);
        dialog.setProgress(percentComplete);
        dialog.show();
    }

    @Override
    protected void onPostExecute(Boolean result)
    {
        super.onPostExecute(result);

        try
        {
            indeterminateDialog.dismiss();
        }
        catch (IllegalArgumentException e)
        {
            // Catch "View not attached to window manager" error, and continue
        }

        if (result)
        {
            // Restart recognition
            activity.resumeOCR();
        }
        else
        {
            activity.showErrorMessage("Error", "Network is unreachable - cannot download language data. "
                    + "Please enable network access and restart this app.");
        }
    }
}