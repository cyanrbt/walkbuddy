package com.cyanrbt.walkbuddy;

/** Explicit emulator probes; no arguments select the actual bundled-model and buffer checks. */
public final class RuntimeProbe extends PreviewProbe {
    @Override public void onStart() {
        if(pip){PipProbe.run(this);return;}
        if(overlay){OverlayProbe.run(this);return;}
        if(layout){LayoutProbe.run(this);return;}
        if(recovery){RecoveryProbe.run(this);return;}
        if(preview){super.onStart();return;}
        DetectorProbe.run(this);
    }
}
