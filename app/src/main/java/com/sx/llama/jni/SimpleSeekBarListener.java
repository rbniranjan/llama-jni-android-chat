package com.sx.llama.jni;

import android.widget.SeekBar;

public abstract class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
    public interface OnProgressChanged {
        void onChanged(SeekBar seekBar, int progress, boolean fromUser);
    }

    public static SimpleSeekBarListener of(OnProgressChanged callback) {
        return new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                callback.onChanged(seekBar, progress, fromUser);
            }
        };
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // no-op
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // no-op
    }
}
