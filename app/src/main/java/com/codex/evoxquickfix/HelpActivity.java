package com.codex.evoxquickfix;

import android.app.Activity;
import android.app.LocaleManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class HelpActivity extends Activity {
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
        setTitle(R.string.help_title);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text(getString(R.string.help_title), 28, TEXT, true));
        addSection(root, R.string.help_beginner_title, R.string.help_beginner_body);
        addSection(root, R.string.help_compat_title, R.string.help_compat_body);
        addSection(root, R.string.help_risk_title, R.string.help_risk_body);
        addSection(root, R.string.help_privacy_title, R.string.help_privacy_body);

        root.addView(sectionTitle(getString(R.string.help_language_title)));
        LinearLayout languages = new LinearLayout(this);
        languages.setOrientation(LinearLayout.HORIZONTAL);
        languages.setGravity(Gravity.CENTER);
        languages.addView(languageButton(R.string.language_english, "en"));
        languages.addView(languageButton(R.string.language_arabic, "ar"));
        languages.addView(languageButton(R.string.language_system, ""));
        root.addView(languages, cardParams());

        TextView footer = text(getString(R.string.footer_version), 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        root.addView(footer);
        return scroll;
    }

    private void addSection(LinearLayout root, int title, int body) {
        root.addView(sectionTitle(getString(title)));
        TextView card = cardText(getString(body));
        card.setTextIsSelectable(true);
        root.addView(card, cardParams());
    }

    private Button languageButton(int label, String tag) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setBackgroundColor(CARD);
        button.setOnClickListener(view -> {
            LocaleManager manager = getSystemService(LocaleManager.class);
            if (manager != null) {
                manager.setApplicationLocales(
                        tag.isEmpty() ? LocaleList.getEmptyLocaleList()
                                : LocaleList.forLanguageTags(tag));
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(52), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView sectionTitle(String value) {
        TextView text = text(value, 18, ACCENT, true);
        text.setPadding(0, dp(22), 0, dp(4));
        return text;
    }

    private TextView cardText(String value) {
        TextView text = text(value, 14, TEXT, false);
        text.setBackgroundColor(CARD);
        text.setPadding(dp(14), dp(14), dp(14), dp(14));
        text.setLineSpacing(0f, 1.12f);
        return text;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, dp(7));
        return params;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
