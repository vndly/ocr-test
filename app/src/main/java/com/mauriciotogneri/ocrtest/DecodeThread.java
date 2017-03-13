package com.mauriciotogneri.ocrtest;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 * <p>
 * The code for this class was adapted from the ZXing project: https://github.com/zxing/zxing
 */
public class DecodeThread extends Thread
{
    private final CaptureActivity activity;
    private Handler handler;
    private final CountDownLatch handlerInitLatch;

    public DecodeThread(CaptureActivity activity)
    {
        this.activity = activity;
        this.handlerInitLatch = new CountDownLatch(1);
    }

    public Handler getHandler()
    {
        try
        {
            handlerInitLatch.await();
        }
        catch (Exception e)
        {
            // ignore
        }

        return handler;
    }

    @Override
    public void run()
    {
        Looper.prepare();
        handler = new DecodeHandler(activity);
        handlerInitLatch.countDown();
        Looper.loop();
    }
}