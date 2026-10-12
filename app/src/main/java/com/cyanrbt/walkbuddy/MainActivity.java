package com.cyanrbt.walkbuddy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView status, cameraState, pipState, cameraBadge, pipBadge;
    private boolean visible;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() {
        status.setText(PreviewActivity.report());
        paintAccess();
        handler.postDelayed(this, 500);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        cameraState = findViewById(R.id.state_camera);
        pipState = findViewById(R.id.state_pip);
        cameraBadge = findViewById(R.id.badge_camera);
        pipBadge = findViewById(R.id.badge_pip);
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
        startActivity(new Intent(this, PreviewActivity.class));
    }

    @Override protected void onResume() { super.onResume(); visible = true; handler.post(refresh); }
    @Override protected void onPause() { visible = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
        super.onRequestPermissionsResult(code, names, results);
        toast(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED ? R.string.toast_granted : R.string.toast_denied);
        paintAccess();
    }

    private void paintAccess() {
        mark(cameraBadge, cameraState, checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            R.string.access_ok, R.string.access_needed);
        mark(pipBadge, pipState, PipSupport.available(this), R.string.pip_available,
            PipSupport.supported(this) ? R.string.pip_disabled : R.string.pip_in_app_only);
    }

    private void mark(TextView badge, TextView label, boolean ok, int okText, int needText) {
        label.setText(ok ? okText : needText);
        label.setTextColor(ok ? 0xFF5E6E76 : 0xFF1F8F7A);
        badge.setBackgroundResource(ok ? R.drawable.bg_badge_ok : R.drawable.bg_badge);
        badge.setTextColor(ok ? 0xFF102028 : 0xFFF6F3EC);
    }

    private void toast(int value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
