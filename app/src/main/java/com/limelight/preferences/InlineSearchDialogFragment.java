package com.limelight.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dialog fragment displaying interactive search results for the settings screen.
 */
public class InlineSearchDialogFragment extends DialogFragment {
    public interface SearchHost {
        @NonNull
        List<Preference> collectSearchablePreferences();
    }

    private static final String TAG = "InlineSearchDialog";

    @Nullable
    private PreferenceFragmentCompat hostFragment;
    @Nullable
    private SearchHost searchHost;

    private EditText searchInput;
    private TextView stateView;
    private RecyclerView resultsView;
    @Nullable
    private PreferenceGroupAdapter adapter;
    private String currentQuery = "";
    @Nullable
    private SharedPreferences sharedPreferences;

    private final TextWatcher queryWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            filterAndDisplay(s == null ? "" : s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            (sharedPreferences, key) -> filterAndDisplay(currentQuery);

    public static void show(@NonNull PreferenceFragmentCompat fragment) {
        InlineSearchDialogFragment dialog = new InlineSearchDialogFragment();
        dialog.setTargetFragment(fragment, 0);
        dialog.show(fragment.getParentFragmentManager(), TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceFragmentCompat fragment = (PreferenceFragmentCompat) getTargetFragment();
        if (fragment == null) {
            throw new IllegalStateException("InlineSearchDialogFragment requires a target fragment");
        }
        if (!(fragment instanceof SearchHost)) {
            throw new IllegalStateException("Host fragment must implement InlineSearchDialogFragment.SearchHost");
        }
        hostFragment = fragment;
        searchHost = (SearchHost) fragment;
        PreferenceManager preferenceManager = fragment.getPreferenceManager();
        sharedPreferences = preferenceManager.getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        setStyle(STYLE_NORMAL, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sharedPreferences != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
            sharedPreferences = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchInput != null) {
            searchInput.removeTextChangedListener(queryWatcher);
        }
        resultsView.setAdapter(null);
        adapter = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_inline_search, null);
        searchInput = view.findViewById(R.id.inline_search_input);
        stateView = view.findViewById(R.id.inline_search_state);
        resultsView = view.findViewById(R.id.inline_search_results);
        resultsView.setLayoutManager(new LinearLayoutManager(getContext()));
        resultsView.setItemAnimator(null);

        searchInput.addTextChangedListener(queryWatcher);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .setNegativeButton(R.string.cancel, (d, which) -> dismissAllowingStateLoss())
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (searchInput != null) {
            searchInput.requestFocus();
        }
        filterAndDisplay(currentQuery);
    }

    private void filterAndDisplay(@NonNull String query) {
        currentQuery = query;
        if (searchHost == null || hostFragment == null) {
            return;
        }

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            clearResults(R.string.inline_search_start_typing);
            return;
        }

        List<Preference> candidates = searchHost.collectSearchablePreferences();
        List<Preference> matches = new ArrayList<>();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (Preference preference : candidates) {
            if (preference != null && preference.isVisible() && matches(preference, lower)) {
                matches.add(preference);
            }
        }

        if (matches.isEmpty()) {
            clearResults(R.string.inline_search_no_results);
            return;
        }

        PreferenceManager preferenceManager = hostFragment.getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(requireContext());
        Set<String> usedKeys = new LinkedHashSet<>();
        for (Preference preference : matches) {
            SearchResultPreference proxy = new SearchResultPreference(requireContext(), preference);
            // Ensure unique keys for the proxy screen
            String baseKey = preference.getKey() == null ? preference.getClass().getName() : preference.getKey();
            String uniqueKey = baseKey;
            int index = 0;
            while (usedKeys.contains(uniqueKey)) {
                uniqueKey = baseKey + "#" + (++index);
            }
            usedKeys.add(uniqueKey);
            proxy.setKey("inline_search_proxy_" + uniqueKey);
            screen.addPreference(proxy);
        }

        adapter = new PreferenceGroupAdapter(screen);
        resultsView.setAdapter(adapter);
        stateView.setVisibility(View.GONE);
        resultsView.setVisibility(View.VISIBLE);
    }

    private void clearResults(int messageResId) {
        if (adapter != null) {
            adapter = null;
        }
        resultsView.setAdapter(null);
        resultsView.setVisibility(View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateView.setText(messageResId);
    }

    private boolean matches(@NonNull Preference preference, @NonNull String lowerQuery) {
        CharSequence title = preference.getTitle();
        if (!TextUtils.isEmpty(title) && title.toString().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        CharSequence summary = preference.getSummary();
        if (!TextUtils.isEmpty(summary) && summary.toString().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        CharSequence contentDescription = preference.getContentDescription();
        if (!TextUtils.isEmpty(contentDescription) && contentDescription.toString().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        String key = preference.getKey();
        if (!TextUtils.isEmpty(key) && key.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        return false;
    }

    private static class SearchResultPreference extends Preference {
        private final Preference delegate;

        SearchResultPreference(@NonNull Context context, @NonNull Preference delegate) {
            super(context);
            this.delegate = delegate;
            setLayoutResource(delegate.getLayoutResource());
            setWidgetLayoutResource(delegate.getWidgetLayoutResource());
            setIcon(delegate.getIcon());
            setTitle(delegate.getTitle());
            setSummary(delegate.getSummary());
            setEnabled(delegate.isEnabled());
            setSelectable(delegate.isSelectable());
            setPersistent(false);
        }

        @Override
        public CharSequence getTitle() {
            return delegate.getTitle();
        }

        @Override
        public CharSequence getSummary() {
            return delegate.getSummary();
        }

        @Override
        public boolean isEnabled() {
            return delegate.isEnabled();
        }

        @Override
        public boolean isSelectable() {
            return delegate.isSelectable();
        }

        @Override
        public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
            delegate.onBindViewHolder(holder);
            // Nếu là EditTextPreference, cho phép chỉnh sửa trực tiếp
            if (delegate instanceof androidx.preference.EditTextPreference) {
                holder.itemView.setOnClickListener(v -> {
                    androidx.preference.EditTextPreference editPref = (androidx.preference.EditTextPreference) delegate;
                    Context context = holder.itemView.getContext();
                    android.widget.EditText input = new android.widget.EditText(context);
                    input.setText(editPref.getText());
                    input.setSelection(input.getText().length());
                    new androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle(editPref.getTitle())
                        .setView(input)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String newValue = input.getText().toString();
                            editPref.setText(newValue);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                });
            }
            // Nếu là ListPreference, cho phép chọn trực tiếp
            else if (delegate instanceof androidx.preference.ListPreference) {
                holder.itemView.setOnClickListener(v -> {
                    androidx.preference.ListPreference listPref = (androidx.preference.ListPreference) delegate;
                    Context context = holder.itemView.getContext();
                    CharSequence[] entries = listPref.getEntries();
                    CharSequence[] entryValues = listPref.getEntryValues();
                    int selectedIdx = -1;
                    String currentValue = listPref.getValue();
                    for (int i = 0; i < entryValues.length; i++) {
                        if (entryValues[i].toString().equals(currentValue)) {
                            selectedIdx = i;
                            break;
                        }
                    }
                    new androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle(listPref.getTitle())
                        .setSingleChoiceItems(entries, selectedIdx, (dialog, which) -> {
                            listPref.setValue(entryValues[which].toString());
                            dialog.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                });
            }
            // Nếu là CheckBoxPreference, cho phép toggle trực tiếp
            else if (delegate instanceof androidx.preference.CheckBoxPreference) {
                holder.itemView.setOnClickListener(v -> {
                    androidx.preference.CheckBoxPreference checkPref = (androidx.preference.CheckBoxPreference) delegate;
                    checkPref.setChecked(!checkPref.isChecked());
                });
            }
        }
    }
}
