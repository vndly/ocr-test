package com.mauriciotogneri.ocrtest.ocr;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.googlecode.leptonica.android.Pixa;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.mauriciotogneri.ocrtest.R;

/**
 * Class to send bitmap data for OCR.
 * <p>
 * The code for this class was adapted from the ZXing project: https://github.com/zxing/zxing
 */
public class DecodeHandler extends Handler
{
    private final CaptureActivity activity;
    private boolean running = true;
    private final TessBaseAPI baseApi;
    private Bitmap bitmap;
    private static boolean isDecodePending;
    private long timeRequired;

    public DecodeHandler(CaptureActivity activity)
    {
        this.activity = activity;
        this.baseApi = activity.getBaseApi();
    }

    @Override
    public void handleMessage(Message message)
    {
        if (!running)
        {
            return;
        }

        switch (message.what)
        {
            case R.id.ocr_continuous_decode:
                // Only request a decode if a request is not already pending.
                if (!isDecodePending)
                {
                    isDecodePending = true;
                    ocrContinuousDecode((byte[]) message.obj, message.arg1, message.arg2);
                }
                break;

            case R.id.ocr_decode:
                ocrDecode((byte[]) message.obj, message.arg1, message.arg2);
                break;

            case R.id.quit:
                running = false;
                Looper.myLooper().quit();
                break;
        }
    }

    public static void resetDecodeState()
    {
        isDecodePending = false;
    }

    /**
     * Launch an AsyncTask to perform an OCR decode for single-shot mode.
     *
     * @param data   Image data
     * @param width  Image width
     * @param height Image height
     */
    private void ocrDecode(byte[] data, int width, int height)
    {
        activity.displayProgressDialog();

        // Launch OCR asynchronously, so we get the dialog box displayed immediately
        new OcrRecognizeAsyncTask(activity, baseApi, data, width, height).execute();
    }

    private void ocrContinuousDecode(byte[] data, int width, int height)
    {
        PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);

        if (source == null)
        {
            sendContinuousOcrFailMessage();
            return;
        }

        bitmap = source.renderCroppedGreyscaleBitmap();

        OcrResult ocrResult = getOcrResult();
        Handler handler = activity.getHandler();

        if (handler == null)
        {
            return;
        }

        if (ocrResult == null)
        {
            try
            {
                sendContinuousOcrFailMessage();
            }
            catch (NullPointerException e)
            {
                activity.stopHandler();
            }
            finally
            {
                bitmap.recycle();
                baseApi.clear();
            }

            return;
        }

        try
        {
            Message message = Message.obtain(handler, R.id.ocr_continuous_decode_succeeded, ocrResult);
            message.sendToTarget();
        }
        catch (NullPointerException e)
        {
            activity.stopHandler();
        }
        finally
        {
            baseApi.clear();
        }
    }

    private OcrResult getOcrResult()
    {
        OcrResult ocrResult;
        String textResult;
        long start = System.currentTimeMillis();

        try
        {
            baseApi.setImage(ReadFile.readBitmap(bitmap));
            textResult = baseApi.getUTF8Text();
            timeRequired = System.currentTimeMillis() - start;

            // Check for failure to recognize text
            if (textResult == null || textResult.equals(""))
            {
                return null;
            }

            ocrResult = new OcrResult();
            ocrResult.setWordConfidences(baseApi.wordConfidences());
            ocrResult.setMeanConfidence(baseApi.meanConfidence());

            // Always get the word bounding boxes--we want it for annotating the bitmap after the user
            // presses the shutter button, in addition to maybe wanting to draw boxes/words during the
            // continuous mode recognition.
            Pixa words = baseApi.getWords();
            ocrResult.setWordBoundingBoxes(words.getBoxRects());
            words.recycle();
        }
        catch (RuntimeException e)
        {
            e.printStackTrace();

            try
            {
                baseApi.clear();
                activity.stopHandler();
            }
            catch (Exception e1)
            {
                // continue
            }

            return null;
        }

        timeRequired = System.currentTimeMillis() - start;
        ocrResult.setBitmap(bitmap);
        ocrResult.setText(textResult);
        ocrResult.setRecognitionTimeRequired(timeRequired);

        return ocrResult;
    }

    private void sendContinuousOcrFailMessage()
    {
        Handler handler = activity.getHandler();

        if (handler != null)
        {
            Message message = Message.obtain(handler, R.id.ocr_continuous_decode_failed, new OcrResultFailure(timeRequired));
            message.sendToTarget();
        }
    }
}