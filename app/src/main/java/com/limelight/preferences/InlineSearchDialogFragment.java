package com.limelight.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.limelight.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class InlineSearchDialogFragment extends DialogFragment {
    public static final String TAG = "InlineSettingsSearch";

    private StreamSettings.SettingsFragment host;
    private RecyclerView recyclerView;
    private SearchResultAdapter adapter;
    private TextView emptyView;
    private TextInputEditText searchEditText;
    private String currentQuery = "";

    public static InlineSearchDialogFragment newInstance() {
        return new InlineSearchDialogFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    setStyle(STYLE_NORMAL, R.style.ThemeOverlay_Cynix_SettingsSearchDialog);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment target = getTargetFragment();
        if (target instanceof StreamSettings.SettingsFragment) {
            host = (StreamSettings.SettingsFragment) target;
            host.registerInlineSearchDialog(this);
        } else {
            throw new IllegalStateException("InlineSearchDialogFragment requires SettingsFragment host");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_settings_search, container, false);

        searchEditText = root.findViewById(R.id.searchEditText);
        recyclerView = root.findViewById(R.id.searchResults);
        emptyView = root.findViewById(R.id.searchEmpty);
        View closeButton = root.findViewById(R.id.searchClose);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchResultAdapter();
        recyclerView.setAdapter(adapter);

        closeButton.setOnClickListener(v -> dismissAllowingStateLoss());

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                adapter.filterQuery(currentQuery);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        searchEditText.requestFocus();
        searchEditText.post(this::showKeyboard);

        if (host != null) {
            adapter.submit(host.getSearchCandidates(), currentQuery);
        }

        updateEmptyState();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        hideKeyboard();
        super.onDismiss(dialog);
    }

    @Override
    public void onDetach() {
        if (host != null) {
            host.unregisterInlineSearchDialog(this);
            host = null;
        }
        super.onDetach();
    }

    void onPreferenceValueChanged(String key) {
        if (host == null || adapter == null) {
            return;
        }
        adapter.submit(host.getSearchCandidates(), currentQuery);
    }

    private void updateEmptyState() {
        if (recyclerView == null || emptyView == null || adapter == null) {
            return;
        }
        boolean empty = adapter.getItemCount() == 0;
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showKeyboard() {
        if (searchEditText == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        if (searchEditText == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }
    }

    final class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {
        private final List<SearchCandidate> allItems = new ArrayList<>();
        private final List<SearchCandidate> filteredItems = new ArrayList<>();

        void submit(List<SearchCandidate> candidates, String query) {
            allItems.clear();
            allItems.addAll(candidates);
            filterInternal(query);
        }

        void filterQuery(String query) {
            filterInternal(query);
        }

        private void filterInternal(String query) {
            String normalized = TextUtils.isEmpty(query) ? "" : query.trim().toLowerCase(Locale.getDefault());
            filteredItems.clear();
            if (normalized.isEmpty()) {
                filteredItems.addAll(allItems);
            } else {
                for (SearchCandidate candidate : allItems) {
                    if (candidate.matches(normalized)) {
                        filteredItems.add(candidate);
                    }
                }
            }
            notifyDataSetChanged();
            updateEmptyState();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_settings_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final SearchCandidate candidate = filteredItems.get(position);
            final Preference pref = candidate.preference;

            CharSequence title = pref.getTitle();
            holder.title.setText(title);

            CharSequence summary = pref.getSummary();
            if (TextUtils.isEmpty(summary)) {
                holder.summary.setVisibility(View.GONE);
            } else {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(summary);
            }

            String breadcrumbText = candidate.getBreadcrumbText();
            if (TextUtils.isEmpty(breadcrumbText)) {
                holder.breadcrumb.setVisibility(View.GONE);
            } else {
                holder.breadcrumb.setVisibility(View.VISIBLE);
                holder.breadcrumb.setText(breadcrumbText);
            }

            boolean selectable = pref.isEnabled() && pref.isSelectable();
            holder.itemView.setEnabled(selectable);
            holder.itemView.setAlpha(selectable ? 1f : 0.4f);

            holder.actionContainer.setVisibility(View.VISIBLE);
            if (pref instanceof TwoStatePreference) {
                holder.switchCompat.setVisibility(View.VISIBLE);
                holder.editButton.setVisibility(View.GONE);
                holder.switchCompat.setEnabled(selectable);
                holder.switchCompat.setOnCheckedChangeListener(null);
                holder.switchCompat.setChecked(((TwoStatePreference) pref).isChecked());

                holder.itemView.setOnClickListener(v -> {
                    if (selectable) {
                        holder.switchCompat.toggle();
                    }
                });

                CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        TwoStatePreference twoStatePref = (TwoStatePreference) pref;
                        boolean current = twoStatePref.isChecked();
                        if (current == isChecked) {
                            return;
                        }
                        if (!pref.callChangeListener(isChecked)) {
                            buttonView.setOnCheckedChangeListener(null);
                            buttonView.setChecked(current);
                            buttonView.setOnCheckedChangeListener(this);
                            return;
                        }
                        twoStatePref.setChecked(isChecked);
                    }
                };
                holder.switchCompat.setOnCheckedChangeListener(listener);
            } else {
                holder.switchCompat.setVisibility(View.GONE);
                holder.switchCompat.setOnCheckedChangeListener(null);
                holder.editButton.setVisibility(View.VISIBLE);
                holder.editButton.setEnabled(selectable);
                holder.itemView.setOnClickListener(null);
                holder.editButton.setOnClickListener(null);

                if (pref instanceof ListPreference) {
                    bindListPreference(holder, (ListPreference) pref, selectable);
                }
                else if (pref instanceof EditTextPreference) {
                    bindEditTextPreference(holder, (EditTextPreference) pref, selectable);
                }
                else if (pref instanceof SeekBarPreference) {
                    bindSeekBarPreference(holder, (SeekBarPreference) pref, selectable);
                }
                else {
                    bindDefaultPreference(holder, pref, selectable);
                }
            }

            if (holder.switchCompat.getVisibility() == View.GONE && holder.editButton.getVisibility() == View.GONE) {
                holder.actionContainer.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView summary;
            final TextView breadcrumb;
            final View actionContainer;
            final SwitchCompat switchCompat;
            final MaterialButton editButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.resultTitle);
                summary = itemView.findViewById(R.id.resultSummary);
                breadcrumb = itemView.findViewById(R.id.resultBreadcrumb);
                actionContainer = itemView.findViewById(R.id.resultActionContainer);
                switchCompat = itemView.findViewById(R.id.resultSwitch);
                editButton = itemView.findViewById(R.id.resultEdit);
            }
        }

        private void bindDefaultPreference(@NonNull ViewHolder holder, @NonNull Preference pref, boolean selectable) {
            holder.editButton.setText(R.string.search_settings_open);
            if (!selectable) {
                holder.itemView.setOnClickListener(null);
                holder.editButton.setOnClickListener(null);
                return;
            }
            View.OnClickListener clickListener = v -> host.performInlinePreferenceClick(pref);
            holder.itemView.setOnClickListener(clickListener);
            holder.editButton.setOnClickListener(clickListener);
        }

        private void bindListPreference(@NonNull ViewHolder holder, @NonNull ListPreference pref, boolean selectable) {
            CharSequence currentEntry = pref.getEntry();
            holder.editButton.setText(currentEntry == null ? getString(R.string.search_settings_select) : currentEntry);
            if (!selectable) {
                holder.itemView.setOnClickListener(null);
                holder.editButton.setOnClickListener(null);
                return;
            }
            View.OnClickListener clickListener = v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                showListSelectionDialog(pref, adapterPosition);
            };
            holder.itemView.setOnClickListener(clickListener);
            holder.editButton.setOnClickListener(clickListener);
        }

        private void bindEditTextPreference(@NonNull ViewHolder holder, @NonNull EditTextPreference pref, boolean selectable) {
            CharSequence display = pref.getText();
            if (TextUtils.isEmpty(display)) {
                display = getString(R.string.search_settings_edit);
            }
            holder.editButton.setText(display);
            if (!selectable) {
                holder.itemView.setOnClickListener(null);
                holder.editButton.setOnClickListener(null);
                return;
            }
            View.OnClickListener clickListener = v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                showEditTextDialog(pref, adapterPosition);
            };
            holder.itemView.setOnClickListener(clickListener);
            holder.editButton.setOnClickListener(clickListener);
        }

        private void bindSeekBarPreference(@NonNull ViewHolder holder, @NonNull SeekBarPreference pref, boolean selectable) {
            holder.editButton.setText(pref.getSummary());
            if (!selectable) {
                holder.itemView.setOnClickListener(null);
                holder.editButton.setOnClickListener(null);
                return;
            }
            View.OnClickListener clickListener = v -> {
                pref.showDialog();
            };
            holder.itemView.setOnClickListener(clickListener);
            holder.editButton.setOnClickListener(clickListener);
        }

        private void showListSelectionDialog(@NonNull ListPreference pref, int position) {
            CharSequence[] entries = pref.getEntries();
            CharSequence[] values = pref.getEntryValues();
            if (entries == null || values == null || entries.length != values.length) {
                host.performInlinePreferenceClick(pref);
                return;
            }

            int selected = pref.findIndexOfValue(pref.getValue());

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(pref.getTitle())
                    .setSingleChoiceItems(entries, selected, (dialog, which) -> {
                        String newValue = values[which].toString();
                        if (pref.callChangeListener(newValue)) {
                            pref.setValue(newValue);
                            notifyItemChanged(position);
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }

        private void showEditTextDialog(@NonNull EditTextPreference pref, int position) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_inline_edit_text, null, false);
            TextInputEditText input = view.findViewById(R.id.inlineEditTextValue);
            CharSequence text = pref.getText();
            if (!TextUtils.isEmpty(text)) {
                input.setText(text);
                Editable editable = input.getText();
                if (editable != null) {
                    input.setSelection(editable.length());
                }
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(pref.getTitle())
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        CharSequence newValue = input.getText();
                        String value = newValue == null ? "" : newValue.toString();
                        if (pref.callChangeListener(value)) {
                            pref.setText(value);
                            notifyItemChanged(position);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    public static final class SearchCandidate {
        final Preference preference;
        final String key;
        final List<String> breadcrumb;
        final String searchableText;

        public SearchCandidate(@NonNull Preference preference, @NonNull Iterable<String> breadcrumbSource) {
            this.preference = preference;
            this.key = preference.getKey();
            this.breadcrumb = new ArrayList<>();
            Iterator<String> iterator = breadcrumbSource.iterator();
            while (iterator.hasNext()) {
                String crumb = iterator.next();
                if (!TextUtils.isEmpty(crumb)) {
                    this.breadcrumb.add(crumb);
                }
            }
            this.searchableText = buildSearchableText(preference, this.breadcrumb);
        }

        private static String buildSearchableText(Preference preference, List<String> breadcrumb) {
            StringBuilder builder = new StringBuilder();
            CharSequence title = preference.getTitle();
            if (!TextUtils.isEmpty(title)) {
                builder.append(title).append(' ');
            }
            CharSequence summary = preference.getSummary();
            if (!TextUtils.isEmpty(summary)) {
                builder.append(summary).append(' ');
            }
            for (String crumb : breadcrumb) {
                builder.append(crumb).append(' ');
            }
            if (!TextUtils.isEmpty(preference.getKey())) {
                builder.append(preference.getKey());
            }
            return builder.toString().toLowerCase(Locale.getDefault());
        }

        boolean matches(String queryLower) {
            if (TextUtils.isEmpty(queryLower)) {
                return true;
            }
            return searchableText.contains(queryLower);
        }

        String getBreadcrumbText() {
            if (breadcrumb.isEmpty()) {
                return "";
            }
            return TextUtils.join(" / ", breadcrumb);
        }
    }
}
