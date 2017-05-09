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
        this.state = State.CONTINUOUS;

        // Start ourselves capturing previews (and decoding if using continuous recognition mode).
        cameraManager.startPreview();

        decodeThread = new DecodeThread(activity);
        decodeThread.start();

        restartOcrPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message)
    {

        switch (message.what)
        {
            case R.id.ocr_continuous_decode_failed:
                DecodeHandler.resetDecodeState();
                if (state == State.CONTINUOUS)
                {
                    restartOcrPreviewAndDecode();
                }
                break;

            case R.id.ocr_continuous_decode_succeeded:
                DecodeHandler.resetDecodeState();
                try
                {
                    activity.handleOcrResult((OcrResult) message.obj);
                }
                catch (NullPointerException e)
                {
                    // Continue
                }
                if (state == State.CONTINUOUS)
                {
                    restartOcrPreviewAndDecode();
                }
                break;
        }
    }

    public void pause()
    {
        state = State.CONTINUOUS_PAUSED;

        removeMessages();
    }

    public void resume()
    {
        if (state == State.CONTINUOUS_PAUSED)
        {
            state = State.CONTINUOUS;
            restartOcrPreviewAndDecode();
        }
    }

    void quitSynchronously()
    {
        state = State.DONE;

        if (cameraManager != null)
        {
            cameraManager.stopPreview();
        }

        try
        {
            // Wait at most half a second; should be enough time, and onPause() will timeout quickly
            decodeThread.join(500L);
        }
        catch (Exception e)
        {
            // continue
        }

        removeMessages();
    }

    private void removeMessages()
    {
        removeMessages(R.id.ocr_continuous_decode);
        removeMessages(R.id.ocr_continuous_decode_failed);
    }

    /**
     * Send a decode request for realtime OCR mode
     */
    private void restartOcrPreviewAndDecode()
    {
        // Continue capturing camera frames
        cameraManager.startPreview();

        // Continue requesting decode of images
        cameraManager.requestOcrDecode(decodeThread.handler(), R.id.ocr_continuous_decode);
    }
}