package com.cyanrbt.walkbuddy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView status, cameraState, pipState, cameraBadge, pipBadge, journeyHint, moreToggle;
    private View morePanel;
    private boolean visible;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() {
        String report = PreviewActivity.report();
        if (!report.contentEquals(status.getText())) status.setText(report);
        paintAccess();
        paintJourney();
        handler.postDelayed(this, 500);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        View home = findViewById(R.id.home_scroll);
        home.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        home.requestApplyInsets();
        status = findViewById(R.id.status);
        cameraState = findViewById(R.id.state_camera);
        pipState = findViewById(R.id.state_pip);
        cameraBadge = findViewById(R.id.badge_camera);
        pipBadge = findViewById(R.id.badge_pip);
        journeyHint = findViewById(R.id.journey_hint);
        morePanel = findViewById(R.id.more_panel);
        moreToggle = findViewById(R.id.more_toggle);
        moreToggle.setOnClickListener(v -> {
            boolean open = morePanel.getVisibility() != View.VISIBLE;
            morePanel.setVisibility(open ? View.VISIBLE : View.GONE);
            moreToggle.setText(open ? R.string.more_hide : R.string.more_show);
        });
        findViewById(R.id.row_camera).setOnClickListener(v -> requestCamera());
        findViewById(R.id.row_pip).setOnClickListener(v -> {
            if (PipSupport.supported(this)) PipSupport.openSettings(this);
            else toast(R.string.pip_unsupported);
        });
        Button open = findViewById(R.id.start);
        Button stop = findViewById(R.id.stop);
        open.setBackgroundTintList(null);
        stop.setBackgroundTintList(null);
        open.setOnClickListener(v -> start());
        stop.setOnClickListener(v -> PreviewActivity.stopActivePreview());
        paintJourney();
    }

    private void requestCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            toast(R.string.toast_granted); return;
        }
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("asked_camera", false);
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException error) { toast(R.string.settings_unavailable); }
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("asked_camera", true).apply();
        requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
    }

    private void start() {
        if (!visible) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCamera(); return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("preview_opened", true).apply();
        startActivity(new Intent(this, PreviewActivity.class));
    }

    @Override protected void onResume() { super.onResume(); visible = true; handler.post(refresh); paintJourney(); }
    @Override protected void onPause() { visible = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
        super.onRequestPermissionsResult(code, names, results);
        toast(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED ? R.string.toast_granted : R.string.toast_denied);
        paintAccess();
        paintJourney();
    }

    private void paintAccess() {
        mark(cameraBadge, cameraState, checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            R.string.access_ok, R.string.access_needed);
        mark(pipBadge, pipState, PipSupport.available(this), R.string.pip_available,
            PipSupport.supported(this) ? R.string.pip_disabled : R.string.pip_in_app_only);
    }

    private void paintJourney() {
        if (journeyHint == null) return;
        boolean camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean preview = getPreferences(MODE_PRIVATE).getBoolean("preview_opened", false);
        boolean pip = PipSupport.available(this);
        paintStep(R.id.home_step_camera, R.id.home_dot_camera, R.id.home_label_camera, camera, !camera);
        paintStep(R.id.home_step_preview, R.id.home_dot_preview, R.id.home_label_preview, preview, camera && !preview);
        paintStep(R.id.home_step_pip, R.id.home_dot_pip, R.id.home_label_pip, pip, preview && !pip);
        int hint = !camera ? R.string.journey_hint_start
            : !preview ? R.string.journey_hint_camera
            : !pip ? R.string.journey_hint_preview
            : R.string.journey_hint_done;
        journeyHint.setText(hint);
        Button start = findViewById(R.id.start);
        if (start != null) start.setText(camera ? R.string.start : R.string.journey_allow_camera);
    }

    private void paintStep(int stepId, int dotId, int labelId, boolean done, boolean active) {
        View step = findViewById(stepId);
        TextView dot = findViewById(dotId), label = findViewById(labelId);
        String name = label.getText().toString();
        if (done) {
            dot.setBackgroundResource(R.drawable.bg_badge_ok);
            dot.setTextColor(getColor(R.color.ink));
            label.setTextColor(getColor(R.color.mint));
            step.setContentDescription(name + "，已完成");
        } else if (active) {
            dot.setBackgroundResource(R.drawable.bg_step_active);
            dot.setTextColor(getColor(R.color.ink));
            label.setTextColor(getColor(R.color.ink_text));
            step.setContentDescription(name + "，进行中");
        } else {
            dot.setBackgroundResource(R.drawable.bg_badge);
            dot.setTextColor(getColor(R.color.on_ink));
            label.setTextColor(getColor(R.color.muted));
            step.setContentDescription(name + "，未开始");
        }
    }

    private void mark(TextView badge, TextView label, boolean ok, int okText, int needText) {
        label.setText(ok ? okText : needText);
        label.setTextColor(getColor(ok ? R.color.muted : R.color.mint));
        badge.setBackgroundResource(ok ? R.drawable.bg_badge_ok : R.drawable.bg_badge);
        badge.setTextColor(getColor(ok ? R.color.ink : R.color.on_ink));
        badge.setContentDescription(getString(ok ? okText : needText));
    }

    private void toast(int value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
