package com.mauriciotogneri.ocrtest.ocr;

import com.googlecode.tesseract.android.TessBaseAPI;

public class Configuration
{
    /**
     * ISO 639-3 language code indicating the default recognition language.
     */
    public static final String DEFAULT_SOURCE_LANGUAGE_CODE = "eng";

    /**
     * The default OCR engine to use.
     */
    public static final int DEFAULT_OCR_ENGINE_MODE = TessBaseAPI.OEM_TESSERACT_ONLY;

    /**
     * The default page segmentation mode to use.
     */
    public static final int DEFAULT_PAGE_SEGMENTATION_MODE = TessBaseAPI.PageSegMode.PSM_AUTO_OSD;

    /**
     * Whether to initially show a looping, real-time OCR display.
     */
    public static final boolean DEFAULT_TOGGLE_CONTINUOUS = true;

    /**
     * Flag to display the real-time recognition results at the top of the scanning screen.
     */
    public static final boolean CONTINUOUS_DISPLAY_RECOGNIZED_TEXT = true;

    public static final String DEFAULT_BLACKLIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmopqrstuvwxyz";

    public static final String DEFAULT_WHITELIST = "0123456789";
}