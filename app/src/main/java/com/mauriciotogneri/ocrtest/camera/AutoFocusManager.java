package com.mauriciotogneri.ocrtest.camera;

import android.hardware.Camera;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class AutoFocusManager implements Camera.AutoFocusCallback
{
    private static final String TAG = AutoFocusManager.class.getSimpleName();
    private static final long AUTO_FOCUS_INTERVAL_MS = 3500L;

    private boolean active;
    private boolean manual;
    private final boolean useAutoFocus;
    private final Camera camera;
    private final Timer timer;
    private TimerTask outstandingTask;

    public AutoFocusManager(Camera camera)
    {
        this.camera = camera;
        timer = new Timer(true);
        String currentFocusMode = camera.getParameters().getFocusMode();
        useAutoFocus = true;
        Log.i(TAG, "Current focus mode '" + currentFocusMode + "'; use auto focus? " + useAutoFocus);
        manual = false;
        checkAndStart();
    }

    @Override
    public synchronized void onAutoFocus(boolean success, Camera theCamera)
    {
        if (active && !manual)
        {
            outstandingTask = new TimerTask()
            {
                @Override
                public void run()
                {
                    checkAndStart();
                }
            };
            timer.schedule(outstandingTask, AUTO_FOCUS_INTERVAL_MS);
        }
        manual = false;
    }

    public void checkAndStart()
    {
        if (useAutoFocus)
        {
            active = true;
            start();
        }
    }

    public synchronized void start()
    {
        try
        {
            camera.autoFocus(this);
        }
        catch (RuntimeException re)
        {
            // Have heard RuntimeException reported in Android 4.0.x+; continue?
            Log.w(TAG, "Unexpected exception while focusing", re);
        }
    }

    public synchronized void stop()
    {
        if (useAutoFocus)
        {
            camera.cancelAutoFocus();
        }
        if (outstandingTask != null)
        {
            outstandingTask.cancel();
            outstandingTask = null;
        }
        active = false;
        manual = false;
    }
}