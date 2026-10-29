package com.cyanrbt.walkbuddy;

/** Explicit failure categories choose useful next steps; never infer actions from error prose. */
final class RecoveryGuide {
    enum Reason { GENERAL, PERMISSION, DARK, CAMERA_BUSY, UNSUPPORTED, DEPTH, MODEL, LOCKED, HIDDEN, INSTALLING, SWITCHING }
    enum Action { RETRY, SETTINGS, HOME, BASIC, WAIT }
    static final class Prompt {
        final String title,body,button;
        final Action action;
        Prompt(String title,String body,String button,Action action) {
            this.title=title;this.body=body;this.button=button;this.action=action;
        }
    }
    static Prompt resolve(Reason reason,boolean cameraGranted) {
        if(!cameraGranted)return new Prompt("需要摄像头权限", "在系统应用设置中允许摄像头，返回后再点重新开启。不会自动恢复相机。", "前往摄像头设置", Action.SETTINGS);
        switch(reason) {
            case PERMISSION:return new Prompt("摄像头权限已允许", "准备好后点下方按钮重新开启，先站着确认镜头方向。识别和声音仍默认关闭。", "重新开启预览", Action.RETRY);
            case DARK:return new Prompt("画面过暗或被遮挡", "请移到明亮处，确认镜头未被遮挡，并检查系统相机隐私开关。准备好后重新开启。", "重新开启预览", Action.RETRY);
            case CAMERA_BUSY:return new Prompt("摄像头暂不可用", "请关闭其他正在使用相机的应用，并检查系统相机隐私开关，再重新开启。", "重新开启预览", Action.RETRY);
            case UNSUPPORTED:return new Prompt("暂不支持此相机配置", "未找到支持当前取景尺寸的后置相机。反复重试无法解决，可返回首页停止本次观察。", "返回首页", Action.HOME);
            case DEPTH:return new Prompt("空间增强暂不可用", "可以先使用基础预览。开启后检测与声音默认关闭，准备好后再按需打开。", "开启基础预览", Action.BASIC);
            case MODEL:return new Prompt("检测暂不可用", "已停止本次观察和提醒。可以先只看基础画面，检测保持关闭。", "仅开启基础预览", Action.BASIC);
            case LOCKED:return new Prompt("锁屏后已停止", "解锁后可主动重新开启。相机与提醒不会在解锁时自动恢复。", "重新开启预览", Action.RETRY);
            case HIDDEN:return new Prompt("预览已停止", "离开预览后已关闭相机和提醒。准备好后重新开启，先确认画面能看到前方。", "重新开启预览", Action.RETRY);
            case INSTALLING:return new Prompt("等待增强服务安装", "请完成系统安装流程后返回。如果取消或安装失败，仍可使用基础预览。", "等待安装完成", Action.WAIT);
            case SWITCHING:return new Prompt("正在切换观察模式", "请稍候，切换完成后会显示新画面。离开预览会停止本次操作。", "正在切换…", Action.WAIT);
            default:return new Prompt("画面暂不可用", "本次观察和提醒已停止。请先站稳，确认镜头未被遮挡、相机未被其他应用占用，再重新开启。", "重新开启预览", Action.RETRY);
        }
    }
}
