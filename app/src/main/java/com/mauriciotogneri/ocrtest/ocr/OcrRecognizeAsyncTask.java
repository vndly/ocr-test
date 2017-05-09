package com.mauriciotogneri.ocrtest.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel;
import com.mauriciotogneri.ocrtest.R;

import java.util.ArrayList;

/**
 * Class to send OCR requests to the OCR engine in a separate thread, send a success/failure message,
 * and dismiss the indeterminate progress dialog box. Used for non-continuous mode OCR only.
 */
public class OcrRecognizeAsyncTask extends AsyncTask<Void, Void, Boolean>
{
    private CaptureActivity activity;
    private TessBaseAPI baseApi;
    private byte[] data;
    private int width;
    private int height;
    private OcrResult ocrResult;

    OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, byte[] data, int width, int height)
    {
        this.activity = activity;
        this.baseApi = baseApi;
        this.data = data;
        this.width = width;
        this.height = height;
    }

    @Override
    protected Boolean doInBackground(Void... arg0)
    {
        Bitmap bitmap = activity.getCameraManager().buildLuminanceSource(data, width, height).renderCroppedGreyscaleBitmap();
        String textResult;

        try
        {
            baseApi.setImage(ReadFile.readBitmap(bitmap));
            textResult = baseApi.getUTF8Text();

            // Check for failure to recognize text
            if (textResult == null || textResult.equals(""))
            {
                return false;
            }
            ocrResult = new OcrResult(textResult);

            // Iterate through the results.
            final ResultIterator iterator = baseApi.getResultIterator();
            int[] lastBoundingBox;
            ArrayList<Rect> charBoxes = new ArrayList<>();
            iterator.begin();
            do
            {
                lastBoundingBox = iterator.getBoundingBox(PageIteratorLevel.RIL_SYMBOL);
                Rect lastRectBox = new Rect(lastBoundingBox[0], lastBoundingBox[1],
                                            lastBoundingBox[2], lastBoundingBox[3]);
                charBoxes.add(lastRectBox);
            } while (iterator.next(PageIteratorLevel.RIL_SYMBOL));
            iterator.delete();

        }
        catch (RuntimeException e)
        {
            Log.e("OcrRecognizeAsyncTask", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
            e.printStackTrace();
            try
            {
                baseApi.clear();
                activity.stopHandler();
            }
            catch (NullPointerException e1)
            {
                // Continue
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result)
    {
        super.onPostExecute(result);

        Handler handler = activity.getHandler();
        if (handler != null)
        {
            // Send results for single-shot mode recognition.
            if (result)
            {
                Message message = Message.obtain(handler, R.id.ocr_decode_succeeded, ocrResult);
                message.sendToTarget();
            }
            else
            {
                Message message = Message.obtain(handler, R.id.ocr_decode_failed, ocrResult);
                message.sendToTarget();
            }
        }
        if (baseApi != null)
        {
            baseApi.clear();
        }
    }
}