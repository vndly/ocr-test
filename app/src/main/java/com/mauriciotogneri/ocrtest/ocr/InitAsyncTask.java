package com.mauriciotogneri.ocrtest.ocr;

import android.app.ProgressDialog;
import android.os.AsyncTask;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.mauriciotogneri.ocrtest.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the language data required for OCR, and initializes the OCR engine using a background
 * thread.
 */
public class InitAsyncTask extends AsyncTask<String, String, Boolean>
{
    private final CaptureActivity activity;
    private final TessBaseAPI baseApi;
    private final ProgressDialog dialogProgress;
    private final ProgressDialog dialogWait;
    private final String languageCode;
    private final String destinationDirBase;
    private final int engineMode;

    private static final String CODE_CLOSE_DIALOG_PROGRESS = "close.dialog.progress";

    /**
     * AsyncTask to asynchronously download data and initialize Tesseract.
     *
     * @param activity     The calling activity
     * @param baseApi      API to the OCR engine
     * @param languageCode ISO 639-2 OCR language code
     * @param engineMode   Whether to use Tesseract, Cube, or both
     */
    public InitAsyncTask(CaptureActivity activity,
                         TessBaseAPI baseApi,
                         String languageCode,
                         String destinationDirBase,
                         int engineMode)
    {
        this.activity = activity;
        this.baseApi = baseApi;
        this.dialogProgress = new ProgressDialog(activity);
        this.dialogWait = new ProgressDialog(activity);
        this.languageCode = languageCode;
        this.destinationDirBase = destinationDirBase;
        this.engineMode = engineMode;
    }

    @Override
    protected void onPreExecute()
    {
        super.onPreExecute();

        initDialogProgress();
        initDialogWait();

        dialogProgress.show();
    }

    private void initDialogProgress()
    {
        dialogProgress.setTitle(activity.getString(R.string.dialog_wait));
        dialogProgress.setMessage(activity.getString(R.string.dialog_checking));
        dialogProgress.setIndeterminate(false);
        dialogProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialogProgress.setCancelable(false);
    }

    private void initDialogWait()
    {
        dialogWait.setTitle(activity.getString(R.string.dialog_wait));
        dialogWait.setMessage(activity.getString(R.string.dialog_initializing));
        dialogWait.setCancelable(false);
        dialogWait.show();
    }

    /**
     * In background thread, perform required setup, and request initialization of
     * the OCR engine.
     *
     * @param params [0] Path name for the directory for storing language data files to the SD card
     */
    protected Boolean doInBackground(String... params)
    {
        String destinationFilenameBase = languageCode + ".traineddata";
        File tessdataDir = new File(destinationDirBase + File.separator + "tessdata");

        if (!tessdataDir.exists() && !tessdataDir.mkdirs())
        {
            return false;
        }

        // check if an incomplete download is present. If a *.download file is there, delete it and
        // any (possibly half-unzipped) Tesseract and Cube data files that may be there.
        File incomplete = new File(tessdataDir, destinationFilenameBase + ".download");
        File tesseractTestFile = new File(tessdataDir, destinationFilenameBase);

        deleteIncompleteFiles(incomplete, tesseractTestFile);

        // if language data files are not present, install them
        boolean installSuccess = installFile(tesseractTestFile, destinationFilenameBase, tessdataDir);

        // dismiss the progress dialog box
        publishProgress("", CODE_CLOSE_DIALOG_PROGRESS);

        // initialize the OCR engine
        return baseApi.init(destinationDirBase + File.separator, languageCode, engineMode) && installSuccess;
    }

    private void deleteIncompleteFiles(File incomplete, File tesseractTestFile)
    {
        if (incomplete.exists())
        {
            incomplete.delete();

            if (tesseractTestFile.exists())
            {
                tesseractTestFile.delete();
            }
        }
    }

    private boolean installFile(File tesseractTestFile, String destinationFilenameBase, File tessdataDir)
    {
        boolean result = false;

        if (!tesseractTestFile.exists())
        {
            // check assets for language data to install. If not present, download from Internet
            try
            {
                // check for a file like "eng.traineddata.zip" or "tesseract-ocr-3.01.eng.tar.zip"
                result = installFromAssets(destinationFilenameBase + ".zip", tessdataDir);
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        else
        {
            result = true;
        }

        return result;
    }

    /**
     * Install a file from application assets to device external storage.
     *
     * @param sourceFilename File in assets to install
     * @param modelRoot      Directory on SD card to install the file to
     * @return True if installZipFromAssets returns true
     * @throws IOException
     */
    private boolean installFromAssets(String sourceFilename, File modelRoot) throws IOException
    {
        String extension = sourceFilename.substring(sourceFilename.lastIndexOf('.'), sourceFilename.length());

        try
        {
            if (extension.equals(".zip"))
            {
                return installZipFromAssets(sourceFilename, modelRoot);
            }
            else
            {
                throw new IllegalArgumentException("Extension " + extension + " is unsupported.");
            }
        }
        catch (FileNotFoundException e)
        {
            // ignore
        }

        return false;
    }

    /**
     * Unzip the given Zip file, located in application assets, into the given
     * destination file.
     *
     * @param sourceFilename Name of the file in assets
     * @param destinationDir Directory to save the destination file in
     * @return
     * @throws IOException
     * @throws FileNotFoundException
     */
    private boolean installZipFromAssets(String sourceFilename, File destinationDir) throws IOException
    {
        // attempt to open the zip archive
        publishProgress("Uncompressing data for " + languageCode + "...", "0");
        ZipInputStream inputStream = new ZipInputStream(activity.getAssets().open(sourceFilename));

        // loop through all the files and folders in the zip archive (but there should just be one)
        for (ZipEntry entry = inputStream.getNextEntry(); entry != null; entry = inputStream.getNextEntry())
        {
            File destinationFile = new File(destinationDir, entry.getName());

            if (entry.isDirectory())
            {
                destinationFile.mkdirs();
            }
            else
            {
                // Note getSize() returns -1 when the zipfile does not have the size set
                long zippedFileSize = entry.getSize();

                // Create a file output stream
                FileOutputStream outputStream = new FileOutputStream(destinationFile);
                final int BUFFER = 8192;

                // Buffer the output to the file
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, BUFFER);
                int unzippedSize = 0;

                // Write the contents
                int count;
                Integer percentComplete;
                Integer percentCompleteLast = 0;
                byte[] data = new byte[BUFFER];

                while ((count = inputStream.read(data, 0, BUFFER)) != -1)
                {
                    bufferedOutputStream.write(data, 0, count);
                    unzippedSize += count;
                    percentComplete = (int) ((unzippedSize / zippedFileSize) * 100);

                    if (percentComplete > percentCompleteLast)
                    {
                        publishProgress("Uncompressing data for " + languageCode + "...",
                                        percentComplete.toString(), "0");
                        percentCompleteLast = percentComplete;
                    }
                }
                bufferedOutputStream.close();
            }
            inputStream.closeEntry();
        }
        inputStream.close();

        return true;
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

        String code = message[1];

        if (code.equals(CODE_CLOSE_DIALOG_PROGRESS))
        {
            closeDialog(dialogProgress);
            dialogWait.show();
        }
        else
        {
            int percentComplete = Integer.parseInt(message[1]);
            dialogProgress.setMessage(message[0]);
            dialogProgress.setProgress(percentComplete);
            dialogProgress.show();
        }
    }

    private void closeDialog(ProgressDialog dialog)
    {
        try
        {
            dialog.dismiss();
        }
        catch (Exception e)
        {
            //ignore
        }
    }

    @Override
    protected void onPostExecute(Boolean result)
    {
        super.onPostExecute(result);

        closeDialog(dialogWait);

        if (result)
        {
            activity.resume();
        }
        else
        {
            activity.showErrorMessage("Error", "Network is unreachable - cannot download language data. Please enable network access and restart this app.");
        }
    }
}