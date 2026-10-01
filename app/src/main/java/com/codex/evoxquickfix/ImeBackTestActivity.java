package com.codex.evoxquickfix;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A deliberately ordinary editor screen used to verify OneBack end to end. */
public final class ImeBackTestActivity extends Activity {
    private static final int BG = Color.rgb(16, 20, 23);
    private static final int CARD = Color.rgb(30, 37, 41);
    private static final int TEXT = Color.rgb(238, 244, 245);
    private static final int MUTED = Color.rgb(177, 193, 196);
    private static final int ACCENT = Color.rgb(128, 203, 196);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(26));
        root.setBackgroundColor(BG);

        TextView title = text(getString(R.string.one_back_test_title), 26, TEXT, true);
        root.addView(title, matchWrap());

        TextView instructions = text(
                getString(R.string.one_back_test_instructions), 15, MUTED, false);
        instructions.setPadding(0, dp(10), 0, dp(18));
        instructions.setLineSpacing(0f, 1.15f);
        root.addView(instructions, matchWrap());

        EditText editor = new EditText(this);
        editor.setHint(R.string.one_back_test_hint);
        editor.setHintTextColor(MUTED);
        editor.setTextColor(TEXT);
        editor.setTextSize(17);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setMinLines(5);
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setBackgroundColor(CARD);
        root.addView(editor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        editor.requestFocus();
        editor.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                InputMethodManager inputMethod = getSystemService(InputMethodManager.class);
                if (inputMethod != null) {
                    inputMethod.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 250L);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setGravity(Gravity.START);
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
