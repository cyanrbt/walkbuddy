package cn.study.window;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView status, cameraState, overlayState, noticeState, cameraBadge, overlayBadge, noticeBadge;
    private boolean visible;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() {
        status.setText(CameraWindowService.report());
        paintAccess();
        handler.postDelayed(this, 500);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        cameraState = findViewById(R.id.state_camera);
        overlayState = findViewById(R.id.state_overlay);
        noticeState = findViewById(R.id.state_notice);
        cameraBadge = findViewById(R.id.badge_camera);
        overlayBadge = findViewById(R.id.badge_overlay);
        noticeBadge = findViewById(R.id.badge_notice);
        findViewById(R.id.row_camera).setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.CAMERA}, 1));
        findViewById(R.id.row_overlay).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.row_notice).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            else toast(R.string.toast_notice_skip);
        });
        Button open = findViewById(R.id.start);
        Button stop = findViewById(R.id.stop);
        open.setBackgroundTintList(null);
        stop.setBackgroundTintList(null);
        open.setOnClickListener(v -> start());
        stop.setOnClickListener(v -> stopService(new Intent(this, CameraWindowService.class)));
    }

    private void start() {
        if (!visible) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { toast(R.string.toast_camera); return; }
        if (!Settings.canDrawOverlays(this)) { toast(R.string.toast_overlay); return; }
        try { startForegroundService(new Intent(this, CameraWindowService.class)); }
        catch (RuntimeException e) { toast(R.string.toast_start_rejected); }
    }

    @Override protected void onResume() { super.onResume(); visible = true; handler.post(refresh); }
    @Override protected void onPause() { visible = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
        super.onRequestPermissionsResult(code, names, results);
        toast(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED ? R.string.toast_granted : R.string.toast_denied);
        paintAccess();
    }

    private void paintAccess() {
        mark(cameraBadge, cameraState, checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED, R.string.access_ok, R.string.access_needed);
        mark(overlayBadge, overlayState, Settings.canDrawOverlays(this), R.string.access_ok, R.string.access_needed);
        if (Build.VERSION.SDK_INT < 33) mark(noticeBadge, noticeState, true, R.string.access_notice_skip, R.string.access_needed);
        else mark(noticeBadge, noticeState, checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED, R.string.access_ok, R.string.access_needed);
    }

    private void mark(TextView badge, TextView label, boolean ok, int okText, int needText) {
        label.setText(ok ? okText : needText);
        label.setTextColor(ok ? 0xFF5E6E76 : 0xFF1F8F7A);
        badge.setBackgroundResource(ok ? R.drawable.bg_badge_ok : R.drawable.bg_badge);
        badge.setTextColor(ok ? 0xFF102028 : 0xFFF6F3EC);
    }

    private void toast(int value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
