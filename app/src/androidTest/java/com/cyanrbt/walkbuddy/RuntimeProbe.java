package com.cyanrbt.walkbuddy;
import android.app.*;
import android.os.Bundle;
import android.graphics.*;
import java.util.*;

/** 显式只在模拟器运行：实际Android Interpreter/模型形状/标签与人像推理。 */
public final class RuntimeProbe extends PreviewProbe {
    @Override public void onStart(){if(pip){PipProbe.run(this);return;}if(preview){super.onStart();return;}Bundle result=new Bundle();try{
        LocalDetector detector=new LocalDetector(getTargetContext());StringBuilder report=new StringBuilder(detector.tensorReport());
        try{
            Bitmap bitmap=BitmapFactory.decodeStream(getContext().getAssets().open("portrait.jpg"));if(bitmap==null)throw new IllegalStateException("测试图无法解码");
            BitmapFrame frame=new BitmapFrame(bitmap);LocalDetector.Result found;
            try{found=detector.detect(frame);}finally{frame.close();}
            boolean person=false;for(int i=0;i<found.classIndices.length;i++)if(found.classIndices[i]==0 && found.scores[i]>=0.5f)person=true;
            if(!person)throw new IllegalStateException("实际人像未确认person index0；indices="+Arrays.toString(found.classIndices)+" scores="+Arrays.toString(found.scores));
            report.append("\nlabels=90 slots; person index0; supported=").append(detector.labels).append("\nactual classes=").append(Arrays.toString(found.classIndices)).append("\nscores=").append(Arrays.toString(found.scores)).append("\nfiltered category=").append(found.category);
            if(!"人".equals(found.category))throw new IllegalStateException("高分检测未返回人");
            Bitmap blank=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);BitmapFrame zero=new BitmapFrame(blank);try{LocalDetector.Result empty=detector.detect(zero);if(!empty.category.isEmpty())throw new IllegalStateException("黑输入发生高分支持目标");}finally{zero.close();}
            report.append("\nblank input: no supported target\nRUNTIME_PROBE_PASS");result.putString("stream",report.toString());
        }finally{detector.close();}
        finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","RUNTIME_PROBE_FAIL: "+error);finish(Activity.RESULT_CANCELED,result);}}
}
