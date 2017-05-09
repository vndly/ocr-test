package com.mauriciotogneri.ocrtest.ocr;

/**
 * Encapsulates the result of OCR.
 */
public class OcrResult
{
    private String text;

    public OcrResult(String text)
    {
        this.text = text;
    }

    public String getText()
    {
        return text;
    }
}