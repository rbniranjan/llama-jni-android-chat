package com.sx.llama.jni;

import android.view.View;
import android.widget.AdapterView;

public abstract class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    public interface OnItemSelected {
        void onSelected(int position);
    }

    public static SimpleItemSelectedListener of(OnItemSelected callback) {
        return new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                callback.onSelected(position);
            }
        };
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // no-op
    }
}
