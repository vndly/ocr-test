package com.mauriciotogneri.ocrtest.camera;

import android.hardware.Camera;

import java.util.Timer;
import java.util.TimerTask;

public class AutoFocusManager implements Camera.AutoFocusCallback
{
    private static final long AUTO_FOCUS_INTERVAL_MS = 3500L;

    private boolean active;
    private boolean manual;
    private final Camera camera;
    private final Timer timer;
    private TimerTask outstandingTask;

    public AutoFocusManager(Camera camera)
    {
        this.camera = camera;
        this.timer = new Timer(true);
        this.manual = false;
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
        active = true;
        start();
    }

    public synchronized void start()
    {
        try
        {
            camera.autoFocus(this);
        }
        catch (Exception re)
        {
            // ignore
        }
    }

    public synchronized void stop()
    {
        camera.cancelAutoFocus();

        if (outstandingTask != null)
        {
            outstandingTask.cancel();
            outstandingTask = null;
        }

        active = false;
        manual = false;
    }
}