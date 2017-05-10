package com.mauriciotogneri.ocrtest.camera;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 * <p>
 * The code for this class was adapted from the ZXing project: https://github.com/zxing/zxing
 */
public class CameraConfigurationManager
{
    // This is bigger than the size of a small screen, which is still supported. The routine
    // below will still select the default (presumably 320x240) size for these. This prevents
    // accidental selection of very low resolution on some devices.
    private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    private static final int MAX_PREVIEW_PIXELS = 800 * 600; // more than large/HD screen

    private final Context context;
    private Point screenResolution;
    private Point cameraResolution;

    public CameraConfigurationManager(Context context)
    {
        this.context = context;
    }

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    public void initFromCameraParameters(Camera camera)
    {
        Camera.Parameters parameters = camera.getParameters();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        // We're landscape-only, and have apparently seen issues with display thinking it's portrait
        // when waking from sleep. If it's not landscape, assume it's mistaken and reverse them:
        if (width < height)
        {
            int temp = width;
            width = height;
            height = temp;
        }

        screenResolution = new Point(width, height);
        cameraResolution = findBestPreviewSizeValue(parameters, screenResolution);
    }

    public void setDesiredCameraParameters(Camera camera)
    {
        Camera.Parameters parameters = camera.getParameters();

        if (parameters == null)
        {
            return;
        }

        initializeTorch(parameters);
        String focusMode = findSettableValue(parameters.getSupportedFocusModes(), Camera.Parameters.FOCUS_MODE_AUTO);

        // Maybe selected auto-focus but not available, so fall through here:
        if (focusMode == null)
        {
            focusMode = findSettableValue(parameters.getSupportedFocusModes(), Camera.Parameters.FOCUS_MODE_MACRO, "edof"); // Camera.Parameters.FOCUS_MODE_EDOF in 2.2+
        }

        if (focusMode != null)
        {
            parameters.setFocusMode(focusMode);
        }

        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
        camera.setParameters(parameters);
    }

    public Point getCameraResolution()
    {
        return cameraResolution;
    }

    public Point getScreenResolution()
    {
        return screenResolution;
    }

    private static void initializeTorch(Camera.Parameters parameters)
    {
        doSetTorch(parameters, false);
    }

    private static void doSetTorch(Camera.Parameters parameters, boolean newSetting)
    {
        String flashMode;

        if (newSetting)
        {
            flashMode = findSettableValue(parameters.getSupportedFlashModes(),
                                          Camera.Parameters.FLASH_MODE_TORCH,
                                          Camera.Parameters.FLASH_MODE_ON);
        }
        else
        {
            flashMode = findSettableValue(parameters.getSupportedFlashModes(),
                                          Camera.Parameters.FLASH_MODE_OFF);
        }

        if (flashMode != null)
        {
            parameters.setFlashMode(flashMode);
        }
    }

    private Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution)
    {
        // Sort by size, descending
        List<Camera.Size> supportedPreviewSizes = new ArrayList<>(parameters.getSupportedPreviewSizes());
        Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>()
        {
            @Override
            public int compare(Camera.Size a, Camera.Size b)
            {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels)
                {
                    return -1;
                }
                if (bPixels > aPixels)
                {
                    return 1;
                }
                return 0;
            }
        });

        Point bestSize = null;
        float screenAspectRatio = (float) screenResolution.x / (float) screenResolution.y;
        float diff = Float.POSITIVE_INFINITY;

        for (Camera.Size supportedPreviewSize : supportedPreviewSizes)
        {
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;
            int pixels = realWidth * realHeight;
            if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS)
            {
                continue;
            }
            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y)
            {
                return new Point(realWidth, realHeight);
            }
            float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
            float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            if (newDiff < diff)
            {
                bestSize = new Point(realWidth, realHeight);
                diff = newDiff;
            }
        }

        if (bestSize == null)
        {
            Camera.Size defaultSize = parameters.getPreviewSize();
            bestSize = new Point(defaultSize.width, defaultSize.height);
        }

        return bestSize;
    }

    private static String findSettableValue(Collection<String> supportedValues, String... desiredValues)
    {
        String result = null;

        if (supportedValues != null)
        {
            for (String desiredValue : desiredValues)
            {
                if (supportedValues.contains(desiredValue))
                {
                    result = desiredValue;
                    break;
                }
            }
        }

        return result;
    }
}