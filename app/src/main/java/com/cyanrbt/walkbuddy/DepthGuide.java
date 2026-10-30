package com.cyanrbt.walkbuddy;

/**
 * Setup guidance for spatial detection: four unlockable steps and the single action
 * the user should take now. Pure logic so the wording and unlock order stay testable.
 */
final class DepthGuide {
    enum Step { SERVICE, GROUND, DEPTH, ALERT }

    static final class Progress {
        final Step active;
        final boolean serviceDone, groundDone, depthDone, alertDone;
        final String hint;
        final String buttonLabel;
        Progress(Step active, boolean serviceDone, boolean groundDone, boolean depthDone,
                 boolean alertDone, String hint, String buttonLabel) {
            this.active = active;
            this.serviceDone = serviceDone;
            this.groundDone = groundDone;
            this.depthDone = depthDone;
            this.alertDone = alertDone;
            this.hint = hint;
            this.buttonLabel = buttonLabel;
        }
        boolean done(Step step) {
            switch (step) {
                case SERVICE: return serviceDone;
                case GROUND: return groundDone;
                case DEPTH: return depthDone;
                default: return alertDone;
            }
        }
        boolean samePresentation(Progress other) {
            return other != null && active == other.active && serviceDone == other.serviceDone
                && groundDone == other.groundDone && depthDone == other.depthDone
                && alertDone == other.alertDone && hint.equals(other.hint)
                && buttonLabel.equals(other.buttonLabel);
        }
        String compactHint() {
            if(active==Step.GROUND)return "确认地面中 · 未测距";
            if(active==Step.DEPTH)return "深度未就绪 · 未测距";
            if(depthDone)return alertDone?"空间检测 · 提醒已开启":"深度就绪 · 检测未开启";
            return "增强启动中 · 未测距";
        }
    }

    /** @param availability null while AR availability is still unknown. */
    static Progress resolve(boolean depthOn, Boolean availability, boolean checking,
                            DepthSnapshot.Stage stage, boolean groundReady, boolean depthReady,
                            boolean recognitionOn) {
        if (!depthOn) {
            if (checking || availability == null) {
                return new Progress(Step.SERVICE, false, false, false, false,
                    "正在检查设备是否支持空间增强…", "检测增强能力…");
            }
            if (!availability) {
                return new Progress(Step.SERVICE, false, false, false, false,
                    "此设备暂不支持增强测距，可继续基础模式", "增强不可用 · 说明");
            }
            return new Progress(Step.SERVICE, true, false, false, false,
                "点「开启空间检测」；将检查 AR 服务，并引导你看向前方地面",
                "开启空间检测");
        }
        if (stage == null) {
            return new Progress(Step.SERVICE, true, false, false, false,
                "正在启动增强，请稍候…", "空间检测：已开启");
        }
        switch (stage) {
            case TRACKING:
                return new Progress(Step.GROUND, true, false, false, false,
                    "缓慢移动手机，让画面建立环境", "空间检测：已开启");
            case ORIENT:
                return new Progress(Step.GROUND, true, false, false, false,
                    "把镜头抬向前方，看到地面与前方空间", "空间检测：已开启");
            case GROUND_SEARCH:
                return new Progress(Step.GROUND, true, false, false, false,
                    "站稳，让镜头看到前方地面", "空间检测：已开启");
            case GROUND_HOLD:
                return new Progress(Step.GROUND, true, false, false, false,
                    "保持姿势，正在确认地面…", "空间检测：已开启");
            case DEPTH_WAIT:
                return new Progress(Step.DEPTH, true, groundReady, false, false,
                    "继续缓慢平移手机，等待深度数据", "空间检测：已开启");
            case DEPTH_STALE:
                return new Progress(Step.DEPTH, true, groundReady, false, false,
                    "深度更新较慢 · 暂停测距，继续缓慢移动", "空间检测：已开启");
            case DEPTH_SPARSE:
                return new Progress(Step.DEPTH, true, groundReady, false, false,
                    "深度覆盖不足 · 对准地面与前方障碍再试", "空间检测：已开启");
            case MEASURING:
            default:
                boolean measuring = groundReady && depthReady;
                if (!measuring) {
                    return new Progress(groundReady ? Step.DEPTH : Step.GROUND,
                        true, groundReady, false, false,
                        groundReady ? "深度未稳定 · 继续缓慢移动" : "正在确认地面 · 暂不判断距离",
                        "空间检测：已开启");
                }
                if (!recognitionOn) {
                    return new Progress(Step.ALERT, true, true, true, false,
                        "测距已就绪 · 打开「检测」后查看黄/红提示",
                        "空间检测：已开启");
                }
                return new Progress(Step.ALERT, true, true, true, true,
                    "空间提示已就绪 · 灰黄红按占用分级，灰色不代表安全",
                    "空间检测：已开启");
        }
    }
}
