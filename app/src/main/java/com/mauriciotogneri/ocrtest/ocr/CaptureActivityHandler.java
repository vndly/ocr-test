package com.mauriciotogneri.ocrtest.ocr;

import android.os.Handler;
import android.os.Message;

import com.mauriciotogneri.ocrtest.R;
import com.mauriciotogneri.ocrtest.camera.CameraManager;

/**
 * This class handles all the messaging which comprises the state machine for capture.
 */
final class CaptureActivityHandler extends Handler
{
    private final CaptureActivity activity;
    private final DecodeThread decodeThread;
    private final CameraManager cameraManager;

    private State state;

    private enum State
    {
        CONTINUOUS,
        CONTINUOUS_PAUSED,
        DONE
    }

    public CaptureActivityHandler(CaptureActivity activity, CameraManager cameraManager)
    {
        this.activity = activity;
        this.cameraManager = cameraManager;
        this.decodeThread = new DecodeThread(activity);
        this.decodeThread.start();
        this.state = State.CONTINUOUS;

        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message)
    {
        switch (message.what)
        {
            case R.id.ocr_continuous_decode_failed:
                decodeFail();
                break;

            case R.id.ocr_continuous_decode_succeeded:
                decodeSucceeded(message.obj.toString());
                break;
        }
    }

    private void decodeFail()
    {
        resetDecodeState();

        if (state == State.CONTINUOUS)
        {
            restartPreviewAndDecode();
        }
    }

    private void decodeSucceeded(String result)
    {
        resetDecodeState();

        try
        {
            activity.handleResult(result);
        }
        catch (Exception e)
        {
            // ignore
        }

        if (state == State.CONTINUOUS)
        {
            restartPreviewAndDecode();
        }
    }

    private void resetDecodeState()
    {
        DecodeHandler decodeHandler = decodeThread.handler();

        if (decodeHandler != null)
        {
            decodeHandler.resetDecodeState();
        }
    }

    public void pause()
    {
        if (state == State.CONTINUOUS)
        {
            state = State.CONTINUOUS_PAUSED;
            removeMessages();
        }
    }

    public void resume()
    {
        if (state == State.CONTINUOUS_PAUSED)
        {
            state = State.CONTINUOUS;
            restartPreviewAndDecode();
        }
    }

    void stop()
    {
        state = State.DONE;

        if (cameraManager != null)
        {
            cameraManager.stopPreview();
        }

        try
        {
            // wait at most half a second; should be enough time, and onPause() will timeout quickly
            decodeThread.join(500L);
        }
        catch (Exception e)
        {
            // ignore
        }

        removeMessages();
    }

    /**
     * Remove all the enqueued messages
     */
    private void removeMessages()
    {
        removeMessages(R.id.ocr_continuous_decode);
        removeMessages(R.id.ocr_continuous_decode_failed);
    }

    /**
     * Send a decode request for realtime OCR mode
     */
    private void restartPreviewAndDecode()
    {
        // continue capturing camera frames
        cameraManager.startPreview();

        // continue requesting decode of images
        cameraManager.requestDecode(decodeThread.handler(), R.id.ocr_continuous_decode);
    }
}