package com.mauriciotogneri.ocrtest;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.mauriciotogneri.ocrtest.ocr.CaptureActivity;

public class FormActivity extends AppCompatActivity
{
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setContentView(R.layout.form);

        findViewById(R.id.scan).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Intent intent = new Intent(FormActivity.this, CaptureActivity.class);
                startActivityForResult(intent, 1000);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == 1000)
        {
            if (resultCode == RESULT_OK)
            {
                String number = data.getStringExtra("number");

                TextView textView = (TextView) findViewById(R.id.number);
                textView.setText(number);
            }
        }
    }
}