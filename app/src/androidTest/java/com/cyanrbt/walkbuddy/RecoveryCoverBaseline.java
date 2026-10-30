package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.*;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Observes the released v0.4.35 presentation; overflow is evidence, not an acceptance claim. */
final class RecoveryCoverBaseline {
    static void record(Context target,File output,StringBuilder report)throws Exception {
        JSONArray samples=new JSONArray();int overflow=0;
        for(int[] size:new int[][]{{96,128},{116,154}})for(float font:new float[]{1,1.5f,2})for(boolean night:new boolean[]{false,true}) {
            Configuration c=new Configuration(target.getResources().getConfiguration());c.fontScale=font;c.densityDpi=160;
            c.screenWidthDp=size[0];c.screenHeightDp=size[1];c.orientation=Configuration.ORIENTATION_PORTRAIT;
            c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(night?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
            Context context=new ContextThemeWrapper(target.createConfigurationContext(c),R.style.Theme_WalkBuddy);
            View root=LayoutInflater.from(context).inflate(R.layout.activity_preview,null);
            PreviewChrome.compact(root,true,false);
            RecoveryGuide.Prompt prompt=RecoveryGuide.resolve(RecoveryGuide.Reason.GENERAL,true);
            PreviewRecovery.show(root,prompt);
            root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));
            root.layout(0,0,size[0],size[1]);
            TextView cover=root.findViewById(R.id.preview_cover);
            int available=cover.getHeight()-cover.getPaddingTop()-cover.getPaddingBottom();
            boolean fits=cover.getLayout()!=null && cover.getLayout().getHeight()<=available;
            if(!fits)overflow++;
            String name="recovery-baseline-"+size[0]+"x"+size[1]+"-font"+Math.round(font*100)+(night?"-night":"-day")+".png";
            Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
            try(FileOutputStream file=new FileOutputStream(new File(output,name))){root.draw(new Canvas(bitmap));bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}finally{bitmap.recycle();}
            samples.put(new JSONObject().put("width",size[0]).put("height",size[1]).put("fontScale",font).put("night",night)
                .put("text",cover.getText()).put("textSizePx",cover.getTextSize()).put("lineCount",cover.getLineCount())
                .put("textHeight",cover.getLayout()==null?-1:cover.getLayout().getHeight()).put("availableTextHeight",available)
                .put("fits",fits).put("image",name));
        }
        try(FileOutputStream file=new FileOutputStream(new File(output,"recovery-cover-baseline.json"))){file.write(samples.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        report.append("RECOVERY_COVER_BASELINE_CAPTURE samples=").append(samples.length()).append(" overflow=").append(overflow)
            .append("; static released layout observation, not compact recovery acceptance\n");
    }
}
