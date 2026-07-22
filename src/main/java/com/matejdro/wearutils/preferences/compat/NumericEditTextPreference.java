package com.matejdro.wearutils.preferences.compat;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.preference.EditTextPreference;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.matejdro.wearutils.miscutils.HtmlCompat;

import java.util.IllegalFormatException;

public class NumericEditTextPreference extends EditTextPreference implements PreferenceWithDialog {
    private String summaryFormat;

    public NumericEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Same reasoning as formatSummary: this runs during preference-screen inflation, so a
        // preference declared without android:summary would otherwise NPE here and take the entire
        // screen down rather than simply showing no summary.
        CharSequence summary = getSummary();
        summaryFormat = summary != null ? summary.toString() : "";
    }


    @Override
    public void setText(String text) {
        super.setText(text);

        setSummary(HtmlCompat.fromHtml(formatSummary(getText())));
    }

    /**
     * Substitutes the current value into the summary, which doubles as the format string.
     *
     * Guarded because that makes every summary a format string whether or not it was written as
     * one: a single stray '%' - easily introduced by a translation - makes String.format throw, and
     * this runs during onSetInitialValue, i.e. while the preference screen is being inflated. An
     * unguarded throw there takes down the whole settings screen before it is ever shown, turning a
     * typo in a translated string into a hard crash. Falling back to the raw summary degrades that
     * to a cosmetic glitch: the user sees the literal placeholder instead of the value.
     */
    private String formatSummary(String value) {
        try {
            return String.format(summaryFormat, value);
        } catch (IllegalFormatException e) {
            Log.w("NumericEditTextPref", "Malformed summary format for preference " + getKey(), e);
            return summaryFormat;
        }
    }

    @Override
    public PreferenceDialogFragmentCompat createDialog(String key) {
        return NumericEditTextPreferenceDialog.create(key);
    }

    public static class NumericEditTextPreferenceDialog extends
            EditTextPreferenceDialogFragmentCompat {

        public static NumericEditTextPreferenceDialog create(String key) {
            NumericEditTextPreferenceDialog fragment = new NumericEditTextPreferenceDialog();

            Bundle arguments = new Bundle(1);
            arguments.putString(ARG_KEY, key);

            fragment.setArguments(arguments);
            return fragment;
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);

            EditText editBox = view.findViewById(android.R.id.edit);
            editBox.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
    }
}
