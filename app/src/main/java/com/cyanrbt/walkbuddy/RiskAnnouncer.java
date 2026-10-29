package com.cyanrbt.walkbuddy;

import android.view.View;
import android.widget.TextView;

/** A visible, polite live region. Fast-changing details are not a second announcement source. */
final class RiskAnnouncer {
    private final TextView banner;
    private final AccessibilitySpeech speech=new AccessibilitySpeech();
    private Detection.Risk shownRisk=Detection.Risk.CAPTURED;

    RiskAnnouncer(View root) {
        banner=root.findViewById(R.id.recent_alert);
    }

    void update(String message,Detection.Risk worst,boolean confirmed,long now) {
        String spoken=speech.update(message,worst,confirmed,now);
        if(worst==Detection.Risk.CAPTURED || message==null || message.isEmpty()) {
            banner.setVisibility(View.GONE);shownRisk=Detection.Risk.CAPTURED;return;
        }
        // A downgrade cancels a higher-tier visual reminder as well as its queued speech.
        if(worst.ordinal()<shownRisk.ordinal()){banner.setVisibility(View.GONE);shownRisk=Detection.Risk.CAPTURED;}
        if(spoken==null)return;
        String description=banner.getContext().getString(R.string.recent_alert,spoken);
        String text=banner.getContext().getString(R.string.recent_alert,
            banner.getContext().getString(worst==Detection.Risk.NEAR?R.string.recent_near:R.string.recent_caution));
        banner.setTextColor(banner.getContext().getColor(worst==Detection.Risk.NEAR?R.color.risk_near:R.color.warning));
        if(!text.contentEquals(banner.getText()))banner.setText(text);
        if(!android.text.TextUtils.equals(description,banner.getContentDescription()))banner.setContentDescription(description);
        banner.setVisibility(View.VISIBLE);
        shownRisk=worst;
    }

    void clear() {
        speech.clear();shownRisk=Detection.Risk.CAPTURED;banner.setVisibility(View.GONE);banner.setText("");banner.setContentDescription(null);
    }
}
