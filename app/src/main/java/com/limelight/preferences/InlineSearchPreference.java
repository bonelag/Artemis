package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Preference item that opens an inline search dialog allowing users to edit settings
 * directly from the search results.
 */
public class InlineSearchPreference extends Preference {
    @Nullable
    private PreferenceFragmentCompat hostFragment;

    public InlineSearchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    public InlineSearchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public InlineSearchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void attachToFragment(PreferenceFragmentCompat fragment) {
        hostFragment = fragment;
    }

    @Override
    protected void onClick() {
        super.onClick();
        PreferenceFragmentCompat fragment = hostFragment;
        if (fragment != null) {
            InlineSearchDialogFragment.show(fragment);
        }
    }
}
