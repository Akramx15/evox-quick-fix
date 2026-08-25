package com.codex.evoxquickfix;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 20, 23);
    private static final int CARD = Color.rgb(30, 37, 41);
    private static final int TEXT = Color.rgb(238, 244, 245);
    private static final int MUTED = Color.rgb(177, 193, 196);
    private static final int ACCENT = Color.rgb(128, 203, 196);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Button> actionButtons = new ArrayList<>();
    private FixManager fixes;
    private TextView deviceStatus;
    private TextView operationStatus;
    private ProgressBar progress;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        fixes = new FixManager(this);
        setContentView(buildUi());
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (!busy) {
                        finish();
                    }
                });
        refreshDiagnostics();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("EvoX Quick Fix", 28, TEXT, true);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = text(
                "أداة Root محلية لجهاز SM-S918B: شفافية Recent Apps، Back Guard، وCircle to Search.",
                15, MUTED, false);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle);

        TextView safety = cardText(
                "لا تغيّر الأداة تطبيق HOME أو ASSISTANT، ولا تحذف حزمًا أو بيانات. "
                        + "الإصلاحات محدودة بالمستخدم 0 وقابلة للاسترجاع.");
        safety.setTextColor(ACCENT);
        root.addView(safety, cardParams());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        progressParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(progress, progressParams);

        operationStatus = text("جاهز للفحص", 15, TEXT, true);
        operationStatus.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(operationStatus);

        deviceStatus = cardText("جارٍ فحص الجهاز…");
        deviceStatus.setTextIsSelectable(true);
        deviceStatus.setMovementMethod(new ScrollingMovementMethod());
        root.addView(deviceStatus, cardParams());

        root.addView(sectionTitle("الإجراءات"));
        root.addView(actionButton("فحص الجهاز", view -> refreshDiagnostics()));
        root.addView(actionButton("تطبيق الكل", view -> confirmApplyAll()));
        root.addView(actionButton("شفافية Recent Apps", view ->
                runOperation("تطبيق الشفافية", fixes::applyTransparency)));
        root.addView(actionButton("منع Back في شاشة Home", view ->
                runOperation("تفعيل Back Guard", fixes::applyBackGuard)));
        root.addView(actionButton("تفعيل Circle to Search", view ->
                runOperation("تثبيت Circle to Search", fixes::applyCircleToSearch)));
        root.addView(actionButton("إعادة تشغيل الجهاز", view -> confirmReboot()));

        Button restore = actionButton("استرجاع وضع الروم", view -> confirmRestore());
        restore.setTextColor(Color.rgb(255, 183, 177));
        root.addView(restore);

        root.addView(sectionTitle("الاسترجاع الطارئ"));
        TextView emergency = cardText(
                "إذا لم يقلع النظام، عطّل وحدتي الإصلاح من Recovery/ADB ثم أعد التشغيل:\n\n"
                        + "adb shell su -c 'touch /data/adb/modules/evox_contextual_search_fix/disable; "
                        + "touch /data/adb/modules/evox_overview_transparency/disable; reboot'\n\n"
                        + "Back Guard يزول بتعطيل EvoX Quick Fix من Vector وإعادة تشغيل Quick Search.");
        emergency.setTextIsSelectable(true);
        emergency.setTypeface(Typeface.MONOSPACE);
        root.addView(emergency, cardParams());

        TextView footer = text("الإصدار 1.0.1 • بدون صلاحية إنترنت", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        root.addView(footer);
        return scroll;
    }

    private void confirmApplyAll() {
        new AlertDialog.Builder(this)
                .setTitle("تطبيق الإصلاحات الثلاثة")
                .setMessage("سيطلب التطبيق صلاحية Root، يفعّل نطاق Quick Search في Vector، "
                        + "ويثبت وحدتي systemless صغيرتين. ستحتاج Restart واحدًا لتثبيت الاستمرار.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تطبيق الكل", (dialog, which) ->
                        runOperation("تطبيق الإصلاحات الثلاثة", fixes::applyAll))
                .show();
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle("استرجاع وضع الروم")
                .setMessage("سيتم تعطيل الشفافية وBack Guard وتعليم وحدتي الإصلاح للإزالة. "
                        + "لن تُمسح بيانات Quick Search أو أي تطبيق.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("استرجاع", (dialog, which) ->
                        runOperation("استرجاع وضع الروم", fixes::restoreRomBehavior))
                .show();
    }

    private void confirmReboot() {
        new AlertDialog.Builder(this)
                .setTitle("إعادة تشغيل الجهاز")
                .setMessage("احفظ عملك أولًا. هل تريد إعادة التشغيل الآن؟")
                .setNegativeButton("لاحقًا", null)
                .setPositiveButton("إعادة التشغيل", (dialog, which) -> {
                    operationStatus.setText("جارٍ إعادة التشغيل…");
                    worker.execute(() -> {
                        try {
                            fixes.rebootDevice();
                        } catch (Throwable failure) {
                            showFailure("إعادة التشغيل", failure);
                        }
                    });
                })
                .show();
    }

    private void refreshDiagnostics() {
        setBusy(true, "جارٍ فحص الجهاز والحالة الحالية…");
        worker.execute(() -> {
            try {
                DiagnosticReport report = fixes.inspect();
                String status = report.toArabicText() + "\n\n" + VectorBridge.serviceSummary();
                runOnUiThread(() -> {
                    deviceStatus.setText(status);
                    operationStatus.setText(report.baseSupported()
                            ? "الفحص مكتمل — الجهاز جاهز" : "الفحص مكتمل — راجع العناصر غير الجاهزة");
                    setBusy(false, null);
                });
            } catch (Throwable failure) {
                showFailure("فشل الفحص", failure);
            }
        });
    }

    private void runOperation(String label, Callable<OperationResult> operation) {
        setBusy(true, label + "…");
        worker.execute(() -> {
            try {
                OperationResult result = operation.call();
                DiagnosticReport report = fixes.inspect();
                runOnUiThread(() -> {
                    deviceStatus.setText(report.toArabicText() + "\n\n" + VectorBridge.serviceSummary());
                    operationStatus.setText(result.status.arabicLabel + " — " + result.message);
                    setBusy(false, null);
                    if (result.status == FeatureStatus.REBOOT_REQUIRED) {
                        new AlertDialog.Builder(this)
                                .setTitle("يلزم Restart")
                                .setMessage(result.message + "\n\nهل تريد إعادة التشغيل الآن؟")
                                .setNegativeButton("لاحقًا", null)
                                .setPositiveButton("إعادة التشغيل", (dialog, which) -> {
                                    operationStatus.setText("جارٍ إعادة التشغيل…");
                                    worker.execute(() -> {
                                        try {
                                            fixes.rebootDevice();
                                        } catch (Throwable failure) {
                                            showFailure("إعادة التشغيل", failure);
                                        }
                                    });
                                })
                                .show();
                    }
                });
            } catch (Throwable failure) {
                showFailure(label, failure);
            }
        });
    }

    private void showFailure(String label, Throwable failure) {
        runOnUiThread(() -> {
            setBusy(false, null);
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            operationStatus.setText("فشل — " + message);
            new AlertDialog.Builder(this)
                    .setTitle(label + " لم يكتمل")
                    .setMessage(message)
                    .setPositiveButton("حسنًا", null)
                    .show();
        });
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        for (Button button : actionButtons) {
            button.setEnabled(!busy);
            button.setAlpha(busy ? 0.55f : 1f);
        }
        if (message != null) {
            operationStatus.setText(message);
        }
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(CARD);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);
        actionButtons.add(button);
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
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        // Let an already-started root transaction finish its rollback if the UI closes.
        worker.shutdown();
        super.onDestroy();
    }
}
