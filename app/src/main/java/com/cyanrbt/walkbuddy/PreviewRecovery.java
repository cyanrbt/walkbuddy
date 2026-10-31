package com.cyanrbt.walkbuddy;

import android.view.View;
import android.widget.TextView;

/** Keeps the opaque camera cover in place and puts the next step before settings. */
final class PreviewRecovery {
    static void problemInfo(View root,boolean available) {
        root.findViewById(R.id.copy_problem_info).setVisibility(available?View.VISIBLE:View.GONE);
    }
    static void clear(View root) {
        root.findViewById(R.id.preview_live_settings).setVisibility(View.VISIBLE);
        root.findViewById(R.id.preview_optional_settings).setVisibility(View.VISIBLE);
        ((PreviewContentLayout)root.findViewById(R.id.preview_content)).setRecovering(false);
        PreviewCover cover=root.findViewById(R.id.preview_cover);cover.preview();
        if(cover.getContentDescription()!=null)cover.setContentDescription(null);
        root.findViewById(R.id.recovery_hint).setVisibility(View.GONE);
        root.findViewById(R.id.restart_preview).setEnabled(true);
    }
    static void show(View root,RecoveryGuide.Prompt prompt) {
        show(root,prompt,false);
    }
    static void show(View root,RecoveryGuide.Prompt prompt,boolean compact) {
        PreviewChrome.viewfinder(root,compact,false,false);
        PreviewCover cover=root.findViewById(R.id.preview_cover);cover.recovery(compact);
        TextView hint=root.findViewById(R.id.recovery_hint);
        TextView action=root.findViewById(R.id.restart_preview);
        TextView info=root.findViewById(R.id.preview_info);
        cover.setVisibility(View.VISIBLE);
        CharSequence title=compact?root.getContext().getText(prompt.action==RecoveryGuide.Action.WAIT
            ?R.string.recovery_compact_wait:R.string.recovery_compact_stopped):prompt.title;
        if(!title.equals(cover.getText()))cover.setText(title);
        String description=compact?root.getContext().getString(R.string.recovery_compact_description,prompt.title,prompt.body):null;
        if(!android.text.TextUtils.equals(description,cover.getContentDescription()))cover.setContentDescription(description);
        root.findViewById(R.id.preview_live_settings).setVisibility(View.GONE);
        root.findViewById(R.id.preview_optional_settings).setVisibility(View.GONE);
        ((PreviewContentLayout)root.findViewById(R.id.preview_content)).setRecovering(true);
        for(int id:new int[]{R.id.pip_depth_status,R.id.recent_alert,R.id.posture_hint})root.findViewById(id).setVisibility(View.GONE);
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
