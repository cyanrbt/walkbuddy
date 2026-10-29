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
    private boolean visible, pendingPreviewPermission, openAfterPermission;
    private int lastAccess = -1, lastJourney = -1;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() {
        if(!visible)return;
        refreshHome();
        if(PreviewActivity.hasPreview())handler.postDelayed(this, 500);
    }};
    private void refreshHome() {
        String report = PreviewActivity.report();
        if (!report.contentEquals(status.getText())) status.setText(report);
        paintAccess();
        paintJourney();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) pendingPreviewPermission = state.getBoolean("pending_preview_permission");
        setContentView(R.layout.activity_main);
        View home = findViewById(R.id.home_root);
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
        if (state != null && state.getBoolean("more_open")) {
            morePanel.setVisibility(View.VISIBLE);
            moreToggle.setText(R.string.more_hide);
        }
        findViewById(R.id.journey_pip_settings).setOnClickListener(v -> PipSupport.openSettings(this));
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
        stop.setOnClickListener(v -> {PreviewActivity.stopActivePreview();refreshHome();});
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
            pendingPreviewPermission = !cameraSettingsNeeded();
            requestCamera(); return;
        }
        Intent preview=new Intent(this, PreviewActivity.class);
        if(PreviewActivity.hasPreview())preview.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(preview);
    }

    @Override protected void onResume() {
        super.onResume(); visible = true; handler.removeCallbacks(refresh);handler.post(refresh);
        continueAfterPermission();
    }
    @Override protected void onPause() { visible = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused && visible){handler.removeCallbacks(refresh);handler.post(refresh);}
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pending_preview_permission", pendingPreviewPermission);
        state.putBoolean("more_open", morePanel.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(state);
    }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
        super.onRequestPermissionsResult(code, names, results);
        if (code != 1) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        openAfterPermission = granted && pendingPreviewPermission;
        pendingPreviewPermission = false;
        if (!openAfterPermission) toast(granted ? R.string.toast_granted : R.string.toast_denied);
        continueAfterPermission();
        paintAccess();
        paintJourney();
    }

    private void continueAfterPermission() {
        if (visible && openAfterPermission) {
            openAfterPermission = false;
            start();
        }
    }

    private boolean cameraSettingsNeeded() {
        return getPreferences(MODE_PRIVATE).getBoolean("asked_camera", false)
            && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
    }

    private void paintAccess() {
        boolean camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean supported = PipSupport.supported(this), pip = PipSupport.available(this);
        int access = (camera ? 1 : 0) | (supported ? 2 : 0) | (pip ? 4 : 0);
        if (access == lastAccess) return;
        lastAccess = access;
        mark(cameraBadge, cameraState, camera,
            R.string.access_ok, R.string.access_needed);
        mark(pipBadge, pipState, pip, R.string.pip_available,
            supported ? R.string.pip_disabled : R.string.pip_in_app_only);
    }

    private void paintJourney() {
        if (journeyHint == null) return;
        boolean camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        android.content.SharedPreferences history = getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE);
        boolean preview = history.getBoolean(HomeJourney.PREVIEW_SEEN, false);
        boolean pipSeen = history.getBoolean(HomeJourney.PIP_SEEN, false);
        boolean supported = PipSupport.supported(this), pip = PipSupport.available(this);
        boolean settings = !camera && cameraSettingsNeeded();
        boolean present=PreviewActivity.hasPreview();SessionState.Phase phase=PreviewActivity.homePhase();
        int snapshot = (camera ? 1 : 0) | (preview ? 2 : 0) | (pipSeen ? 4 : 0)
            | (supported ? 8 : 0) | (pip ? 16 : 0) | (settings ? 32 : 0) | (present ? 64 : 0) | (phase.ordinal()<<7);
        if (snapshot == lastJourney) return;
        lastJourney = snapshot;
        HomeJourney journey = HomeJourney.resolve(camera, preview, pipSeen, supported, pip);
        int hint;
        switch (journey.hint) {
            case CAMERA: hint = settings ? R.string.journey_hint_camera_settings : R.string.journey_hint_start; break;
            case PREVIEW: hint = R.string.journey_hint_camera; break;
            case PIP: hint = R.string.journey_hint_preview; break;
            case PIP_DISABLED: hint = R.string.journey_hint_pip_disabled; break;
            case IN_APP: hint = R.string.journey_hint_in_app; break;
            default: hint = R.string.journey_hint_done;
        }
        HomeChrome.journey(findViewById(R.id.home_root),journey,hint,camera && supported && !pip);
        HomeChrome.actions(findViewById(R.id.home_root),camera,settings,present,phase);
    }

    private void mark(TextView badge, TextView label, boolean ok, int okText, int needText) {
        label.setText(ok ? okText : needText);
        label.setTextColor(getColor(ok ? R.color.muted : R.color.home_accent_text));
        badge.setBackgroundResource(ok ? R.drawable.bg_badge_ok : R.drawable.bg_badge);
        badge.setTextColor(getColor(ok ? R.color.ink : R.color.on_ink));
        badge.setContentDescription(getString(ok ? okText : needText));
    }

    private void toast(int value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
