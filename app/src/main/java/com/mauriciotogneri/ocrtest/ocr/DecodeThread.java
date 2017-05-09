package com.mauriciotogneri.ocrtest.ocr;

import android.os.Looper;

import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 */
public class DecodeThread extends Thread
{
    private final CaptureActivity activity;
    private final CountDownLatch handlerInitLatch;
    private DecodeHandler handler;

    public DecodeThread(CaptureActivity activity)
    {
        this.activity = activity;
        this.handlerInitLatch = new CountDownLatch(1);
    }

    public DecodeHandler handler()
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