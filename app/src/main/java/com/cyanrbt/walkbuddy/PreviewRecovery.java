package com.cyanrbt.walkbuddy;

import android.view.View;
import android.widget.TextView;

/** Keeps the opaque camera cover in place and puts the next step before settings. */
final class PreviewRecovery {
    static void problemInfo(View root,boolean available) {
        root.findViewById(R.id.copy_problem_info).setVisibility(available?View.VISIBLE:View.GONE);
    }
    static void clear(View root) {
        root.findViewById(R.id.recovery_hint).setVisibility(View.GONE);
        root.findViewById(R.id.restart_preview).setEnabled(true);
        root.findViewById(R.id.enable_depth).setEnabled(true);
    }
    static void show(View root,RecoveryGuide.Prompt prompt) {
        TextView cover=root.findViewById(R.id.preview_cover),hint=root.findViewById(R.id.recovery_hint);
        TextView action=root.findViewById(R.id.restart_preview);
        TextView info=root.findViewById(R.id.preview_info);
        cover.setVisibility(View.VISIBLE);
        if(!prompt.title.contentEquals(cover.getText()))cover.setText(prompt.title);
        if(!prompt.body.contentEquals(hint.getText()))hint.setText(prompt.body);
        if(!prompt.button.contentEquals(action.getText()))action.setText(prompt.button);
        hint.setVisibility(View.VISIBLE);
        if(!android.text.TextUtils.equals(info.getContext().getText(R.string.info_stopped),info.getText()))info.setText(R.string.info_stopped);
        root.findViewById(R.id.preview_recognition).setEnabled(false);
        root.findViewById(R.id.preview_sound).setEnabled(false);
        root.findViewById(R.id.preview_vibration).setEnabled(false);
        action.setEnabled(prompt.action!=RecoveryGuide.Action.WAIT);
        root.findViewById(R.id.enable_depth).setEnabled(false);
    }
}
