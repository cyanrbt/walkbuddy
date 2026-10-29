package com.cyanrbt.walkbuddy;

/** First-use history and current capability are different inputs. No camera side effects. */
final class HomeJourney {
    static final String PREFERENCES = "home_journey";
    static final String PREVIEW_SEEN = "live_preview_seen";
    static final String PIP_SEEN = "pip_entered";

    enum Step { DONE, CURRENT, UPCOMING, OPTIONAL }
    enum Hint { CAMERA, PREVIEW, PIP, PIP_DISABLED, IN_APP, READY }

    final Step camera, preview, pip;
    final Hint hint;

    private HomeJourney(Step camera, Step preview, Step pip, Hint hint) {
        this.camera = camera;
        this.preview = preview;
        this.pip = pip;
        this.hint = hint;
    }

    static HomeJourney resolve(boolean cameraGranted, boolean previewSeen, boolean pipSeen,
                               boolean pipSupported, boolean pipAvailable) {
        Step camera = cameraGranted ? Step.DONE : Step.CURRENT;
        Step preview = previewSeen ? Step.DONE : cameraGranted ? Step.CURRENT : Step.UPCOMING;
        Step pip = !pipSupported ? Step.OPTIONAL
            : pipAvailable && pipSeen ? Step.DONE
            : cameraGranted && previewSeen ? Step.CURRENT : Step.UPCOMING;
        Hint hint = !cameraGranted ? Hint.CAMERA
            : !previewSeen ? Hint.PREVIEW
            : !pipSupported ? Hint.IN_APP
            : !pipAvailable ? Hint.PIP_DISABLED
            : !pipSeen ? Hint.PIP : Hint.READY;
        return new HomeJourney(camera, preview, pip, hint);
    }
}
