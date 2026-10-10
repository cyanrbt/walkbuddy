package cn.study.window;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public final class MainActivity extends Activity {
    private TextView status;
    private boolean visible;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() {
        status.setText(CameraWindowService.report()); handler.postDelayed(this, 500);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(32,60,32,32);
        scroll.addView(column); setContentView(scroll);
        text(column, "WalkBuddy", 28);
        text(column, "个人学习交流原型 · 白天正常步行", 18);
        text(column, "主要由你观察环境。自然握持时镜头可能朝向地面，先原地验证视野。可能误报、漏报，没有提醒不代表安全。不要在道路、楼梯或骑行中依赖本工具。", 17);
        status = text(column, "已停止", 16);
        button(column, "1 · 授予摄像头权限", v -> requestPermissions(new String[]{Manifest.permission.CAMERA}, 1));
        button(column, "2 · 允许显示悬浮窗", v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        button(column, "3 · 通知权限（建议允许）", v -> { if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2); else toast("当前系统无需通知授权"); });
        button(column, "开启后置摄像头浮窗", v -> start());
        button(column, "停止并释放摄像头", v -> stopService(new Intent(this, CameraWindowService.class)));
        text(column, "拖动浮窗标题移动；右下角拖动调整大小。浮窗内“停止”或通知中的“停止”都可结束。辅助识别与声音每次启动默认关闭；只在浮窗主动勾选后启用。",16);
        text(column, "实际仅筛选人、自行车、汽车、摩托车、公交车、卡车、椅子、长凳。目标检测不能判断距离、深度或必然挡路；坑洞、台阶、玻璃等不覆盖。",16);
        text(column, "默认不保存、不上传画面；应用没有网络权限。画面冻结、采集或分析过期时会遮挡画面并停止提醒。故障后回到本页重新主动开启。锁屏停止。",16);
        text(column, "测量：浮窗/首页显示采集与显示帧龄。它们不等于相机到屏幕的真实延迟；实测请使用秒表/高速摄影，见中文测试步骤。",16);
    }
    private void start() {
        if(!visible) return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { toast("请先授予摄像头权限"); return; }
        if(!Settings.canDrawOverlays(this)) { toast("请先允许悬浮窗"); return; }
        try { startForegroundService(new Intent(this,CameraWindowService.class)); }
        catch (RuntimeException e) { toast("无法启动：请保持页面可见并检查权限"); }
    }
    @Override protected void onResume() { super.onResume(); visible=true; handler.post(refresh); }
    @Override protected void onPause() { visible=false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) { super.onRequestPermissionsResult(code,names,results); toast(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED ? "已授权；请主动开启" : "授权拒绝；功能未开启"); }
    private TextView text(LinearLayout parent,String value,int size) { TextView t=new TextView(this); t.setText(value);t.setTextSize(size);t.setPadding(0,8,0,16); parent.addView(t);return t; }
    private void button(LinearLayout parent,String value,View.OnClickListener click) { Button b=new Button(this); b.setText(value);b.setOnClickListener(click);parent.addView(b); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
}
