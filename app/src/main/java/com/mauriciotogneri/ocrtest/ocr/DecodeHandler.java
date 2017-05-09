package com.mauriciotogneri.ocrtest.ocr;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.googlecode.leptonica.android.Pixa;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.mauriciotogneri.ocrtest.R;
import com.mauriciotogneri.ocrtest.camera.PlanarYUVLuminanceSource;

/**
 * Class to send bitmap data for OCR.
 */
public class DecodeHandler extends Handler
{
    private final CaptureActivity activity;
    private final TessBaseAPI baseApi;
    private boolean running = true;
    private boolean isDecodePending = false;

    public DecodeHandler(CaptureActivity activity)
    {
        this.activity = activity;
        this.baseApi = activity.getBaseApi();
    }

    @Override
    public void handleMessage(Message message)
    {
        if (running)
        {
            switch (message.what)
            {
                case R.id.ocr_continuous_decode:
                    decode(message);
                    break;

                case R.id.ocr_continuous_quit:
                    stop();
                    break;
            }
        }
    }

    private void decode(Message message)
    {
        // only request a decode if a request is not already pending
        if (!isDecodePending)
        {
            isDecodePending = true;
            continuousDecode((byte[]) message.obj, message.arg1, message.arg2);
        }
    }

    private void stop()
    {
        running = false;

        try
        {
            Looper.myLooper().quit();
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    public void resetDecodeState()
    {
        isDecodePending = false;
    }

    private void continuousDecode(byte[] data, int width, int height)
    {
        PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);

        if (source == null)
        {
            sendContinuousFailMessage();
            return;
        }

        Bitmap bitmap = source.renderCroppedGreyscaleBitmap();

        if (bitmap != null)
        {
            String result = result(bitmap);

            if (result == null)
            {
                try
                {
                    sendContinuousFailMessage();
                }
                catch (Exception e)
                {
                    activity.stopHandler();
                }
                finally
                {
                    bitmap.recycle();
                    baseApi.clear();
                }
            }
            else
            {
                try
                {
                    sendMessage(R.id.ocr_continuous_decode_succeeded, result);
                }
                catch (Exception e)
                {
                    activity.stopHandler();
                }
                finally
                {
                    baseApi.clear();
                }
            }
        }
    }

    private String result(Bitmap bitmap)
    {
        String result;

        try
        {
            baseApi.setImage(ReadFile.readBitmap(bitmap));
            result = baseApi.getUTF8Text();

            // check for failure to recognize text
            if ((result == null) || result.equals(""))
            {
                return null;
            }

            // always get the word bounding boxes--we want it for annotating the bitmap after the user
            // presses the shutter button, in addition to maybe wanting to draw boxes/words during the
            // continuous mode recognition.
            Pixa words = baseApi.getWords();
            words.recycle();
        }
        catch (Exception e)
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

        return result;
    }

    private void sendContinuousFailMessage()
    {
        sendMessage(R.id.ocr_continuous_decode_failed, null);
    }

    private void sendMessage(int what, Object object)
    {
        Handler handler = activity.getHandler();

        if (handler != null)
        {
            if (object != null)
            {
                Message.obtain(handler, what, object).sendToTarget();
            }
            else
            {
                Message.obtain(handler, what).sendToTarget();
            }
        }
    }
}