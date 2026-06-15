package com.looper.ads.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

public class TransparentButton extends AppCompatButton {
    public TransparentButton(@NonNull Context context) {
        super(context);
    }

    public TransparentButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TransparentButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(null, null);
    }
}