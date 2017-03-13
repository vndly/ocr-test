package com.mauriciotogneri.ocrtest.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.view.SurfaceHolder;

import com.mauriciotogneri.ocrtest.PlanarYUVLuminanceSource;

import java.io.IOException;

public class CameraManager
{
    private final Context context;
    private final CameraConfigurationManager configManager;
    private final PreviewCallback previewCallback;
    private Camera camera;
    private AutoFocusManager autoFocusManager;
    private Rect framingRect;
    private Rect framingRectInPreview;
    private boolean initialized;
    private boolean previewing;

    public CameraManager(Context context)
    {
        this.context = context;
        this.configManager = new CameraConfigurationManager(context);
        this.previewCallback = new PreviewCallback(configManager);
    }

    public synchronized void openDriver(SurfaceHolder holder) throws IOException
    {
        Camera theCamera = camera;

        if (theCamera == null)
        {
            theCamera = Camera.open();

            if (theCamera == null)
            {
                throw new IOException();
            }

            camera = theCamera;
        }

        camera.setPreviewDisplay(holder);

        if (!initialized)
        {
            initialized = true;
            configManager.initFromCameraParameters(theCamera);
            Point screenResolution = configManager.getScreenResolution();
            framingRect = new Rect(0, 0, screenResolution.x, screenResolution.y);
            framingRectInPreview = null;
        }

        configManager.setDesiredCameraParameters(theCamera);
    }

    public synchronized void closeDriver()
    {
        if (camera != null)
        {
            camera.release();
            camera = null;

            // Make sure to clear these each time we close the camera, so that any scanning rect
            // requested by intent is forgotten.
            framingRect = null;
            framingRectInPreview = null;
        }
    }

    public synchronized void startPreview()
    {
        Camera theCamera = camera;

        if ((theCamera != null) && (!previewing))
        {
            theCamera.startPreview();
            previewing = true;
            autoFocusManager = new AutoFocusManager(context, camera);
        }
    }

    public synchronized void stopPreview()
    {
        if (autoFocusManager != null)
        {
            autoFocusManager.stop();
            autoFocusManager = null;
        }

        if ((camera != null) && previewing)
        {
            camera.stopPreview();
            previewCallback.setHandler(null, 0);
            previewing = false;
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
     * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
     * respectively.
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    public synchronized void requestOcrDecode(Handler handler, int message)
    {
        Camera theCamera = camera;

        if ((theCamera != null) && previewing)
        {
            previewCallback.setHandler(handler, message);
            theCamera.setOneShotPreviewCallback(previewCallback);
        }
    }

    public synchronized void requestAutoFocus(long delay)
    {
        autoFocusManager.start(delay);
    }

    private synchronized Rect getFramingRectInPreview()
    {
        if (framingRectInPreview == null)
        {
            Rect rect = new Rect(framingRect);
            Point cameraResolution = configManager.getCameraResolution();
            Point screenResolution = configManager.getScreenResolution();

            if (cameraResolution == null || screenResolution == null)
            {
                // Called early, before init even finished
                return null;
            }

            rect.left = rect.left * cameraResolution.x / screenResolution.x;
            rect.right = rect.right * cameraResolution.x / screenResolution.x;
            rect.top = rect.top * cameraResolution.y / screenResolution.y;
            rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
            framingRectInPreview = rect;
        }

        return framingRectInPreview;
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on the format
     * of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height)
    {
        Rect rect = getFramingRectInPreview();

        if (rect == null)
        {
            return null;
        }
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height());
    }
}