package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.limelight.R;

public class InlineSearchPreference extends Preference {
    public interface OnSearchClickListener {
        void onSearchPreferenceClicked(@NonNull InlineSearchPreference preference);
    }

    private OnSearchClickListener listener;

    public InlineSearchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InlineSearchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setIcon(android.R.drawable.ic_menu_search);
        setTitle(R.string.search_settings_hint);
    }

    public void setOnSearchClickListener(@Nullable OnSearchClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onClick() {
        super.onClick();
        if (listener != null) {
            listener.onSearchPreferenceClicked(this);
        }
    }
}
