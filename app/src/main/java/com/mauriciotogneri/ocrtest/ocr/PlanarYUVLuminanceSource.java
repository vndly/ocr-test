package com.mauriciotogneri.ocrtest.ocr;

import android.graphics.Bitmap;

/**
 * This object extends LuminanceSource around an array of YUV data returned from the camera driver,
 * with the option to crop to a rectangle within the full data. This can be used to exclude
 * superfluous pixels around the perimeter and speed up decoding.
 * <p>
 * It works for any pixel format where the Y channel is planar and appears first, including
 * YCbCr_420_SP and YCbCr_422_SP.
 * <p>
 * The code for this class was adapted from the ZXing project: https://github.com/zxing/zxing
 */
public final class PlanarYUVLuminanceSource
{
    private final int width;
    private final int height;
    private final byte[] yuvData;
    private final int dataWidth;
    private final int dataHeight;
    private final int left;
    private final int top;

    public PlanarYUVLuminanceSource(byte[] yuvData,
                                    int dataWidth,
                                    int dataHeight,
                                    int left,
                                    int top,
                                    int width,
                                    int height)
    {
        this.width = width;
        this.height = height;

        if (left + width > dataWidth || top + height > dataHeight)
        {
            throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
        }

        this.yuvData = yuvData;
        this.dataWidth = dataWidth;
        this.dataHeight = dataHeight;
        this.left = left;
        this.top = top;
    }

    public Bitmap renderCroppedGreyscaleBitmap()
    {
        int[] pixels = new int[width * height];
        byte[] yuv = yuvData;
        int inputOffset = top * dataWidth + left;

        for (int y = 0; y < height; y++)
        {
            int outputOffset = y * width;
            for (int x = 0; x < width; x++)
            {
                int grey = yuv[inputOffset + x] & 0xff;
                pixels[outputOffset + x] = 0xFF000000 | (grey * 0x00010101);
            }
            inputOffset += dataWidth;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}