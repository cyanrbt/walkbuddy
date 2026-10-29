package com.cyanrbt.walkbuddy;

import android.view.View;
import android.widget.TextView;

/** Shared by the Activity and native layout checks; actions stay outside the scroll content. */
final class HomeChrome {
    static void actions(View root,boolean camera,boolean settings,boolean present,SessionState.Phase phase) {
        boolean running=phase==SessionState.Phase.STARTING || phase==SessionState.Phase.LIVE;
        TextView start=root.findViewById(R.id.start);
        int label=camera?(present?R.string.home_return_preview:R.string.start)
            :(settings?R.string.journey_camera_settings:R.string.journey_allow_camera);
        start.setText(label);
        View stop=root.findViewById(R.id.stop);
        stop.setEnabled(running);stop.setVisibility(running?View.VISIBLE:View.GONE);
        root.findViewById(R.id.home_action_gap).setVisibility(running?View.VISIBLE:View.GONE);
        int hint=phase==SessionState.Phase.STARTING?R.string.home_action_starting
            :phase==SessionState.Phase.LIVE?R.string.home_action_live
            :camera && present?R.string.home_action_restore:R.string.home_action_stand;
        ((TextView)root.findViewById(R.id.home_action_hint)).setText(hint);
        boolean observing=present || running || root.findViewById(R.id.home_steps).getVisibility()==View.GONE;
        root.findViewById(R.id.home_status_container).setVisibility(observing?View.VISIBLE:View.GONE);
    }
    static void journey(View root,HomeJourney journey,int hint,boolean settings) {
        boolean compact=journey.hint==HomeJourney.Hint.READY || journey.hint==HomeJourney.Hint.IN_APP;
        root.findViewById(R.id.home_steps).setVisibility(compact?View.GONE:View.VISIBLE);
        ((TextView)root.findViewById(R.id.home_journey_label)).setText(compact?R.string.status_label:R.string.journey_label);
        root.findViewById(R.id.home_status_label).setVisibility(compact?View.GONE:View.VISIBLE);
        root.findViewById(R.id.journey_hint).setVisibility(journey.hint==HomeJourney.Hint.READY?View.GONE:View.VISIBLE);
        paintStep(root,R.id.home_step_camera,R.id.home_dot_camera,R.id.home_label_camera,journey.camera,"1");
        paintStep(root,R.id.home_step_preview,R.id.home_dot_preview,R.id.home_label_preview,journey.preview,"2");
        paintStep(root,R.id.home_step_pip,R.id.home_dot_pip,R.id.home_label_pip,journey.pip,"3");
        ((TextView)root.findViewById(R.id.journey_hint)).setText(hint);
        root.findViewById(R.id.journey_pip_settings).setVisibility(settings?View.VISIBLE:View.GONE);
    }
    private static void paintStep(View root,int stepId, int dotId, int labelId, HomeJourney.Step state, String number) {
        View step = root.findViewById(stepId);
        TextView dot = root.findViewById(dotId), label = root.findViewById(labelId);
        String name = label.getText().toString();
        dot.setText(state == HomeJourney.Step.DONE ? "✓" : state == HomeJourney.Step.OPTIONAL ? "–" : number);
        if (state == HomeJourney.Step.DONE) {
            dot.setBackgroundResource(R.drawable.bg_badge_ok);
            dot.setTextColor(root.getContext().getColor(R.color.ink));
            label.setTextColor(root.getContext().getColor(R.color.ink_text));
            step.setContentDescription(name + "，已完成");
        } else if (state == HomeJourney.Step.CURRENT) {
            dot.setBackgroundResource(R.drawable.bg_step_active);
            dot.setTextColor(root.getContext().getColor(R.color.ink));
            label.setTextColor(root.getContext().getColor(R.color.ink_text));
            step.setContentDescription(name + "，进行中");
        } else {
            dot.setBackgroundResource(R.drawable.bg_home_step_todo);
            dot.setTextColor(root.getContext().getColor(R.color.muted));
            label.setTextColor(root.getContext().getColor(R.color.muted));
            step.setContentDescription(name + (state == HomeJourney.Step.OPTIONAL ? "，此设备不支持，可跳过" : "，未开始"));
        }
    }


}
