package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import java.lang.reflect.Field;
import java.nio.*;
import java.util.*;

/** Android pixel parity, actual bundled-model outputs, and scoped allocation observations. */
final class DetectorProbe {
    private static volatile int sink;
    static void compareThreads(Instrumentation runner) {
        Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        Bitmap portrait=null,blank=null,white=null;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Thread parity probe is restricted to emulators");
            try(java.io.InputStream stream=runner.getContext().getAssets().open("portrait.jpg")){portrait=BitmapFactory.decodeStream(stream);}
            if(portrait==null)throw new AssertionError("Portrait fixture did not decode");
            blank=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);
            white=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);white.eraseColor(Color.WHITE);
            Bitmap[] sources={portrait,blank,white,portrait};
            List<float[]> expected=new ArrayList<>();List<String> categories=new ArrayList<>();
            try(LocalDetector detector=new LocalDetector(runner.getTargetContext(),2)) {
                for(Bitmap source:sources) {
                    LocalDetector.Result found=detector.detect(new BitmapFrame(source));
                    expected.add(tensors(detector));categories.add(found.category);
                }
            }
            try(LocalDetector detector=new LocalDetector(runner.getTargetContext(),1)) {
                for(int i=0;i<sources.length;i++) {
                    LocalDetector.Result found=detector.detect(new BitmapFrame(sources[i]));float[] actual=tensors(detector);
                    for(int j=0;j<actual.length;j++)equal(expected.get(i)[j],actual[j]);
                    if(!categories.get(i).equals(found.category))throw new AssertionError("Thread count changed confirmed category");
                    if(sources[i].isRecycled())throw new AssertionError("Detector recycled input");
                }
            }
            result.putString("stream","THREAD_PARITY_PASS cases=4; all 151 floats match threads=2/1 within 1e-6; portrait, blank, white, repeated portrait\n");
            code=Activity.RESULT_OK;
        }catch(Throwable error){result.putString("stream","THREAD_PARITY_FAIL: "+error);}
        finally {for(Bitmap bitmap:new Bitmap[]{portrait,blank,white})if(bitmap!=null)bitmap.recycle();}
        runner.finish(code,result);
    }
    private static float[] tensors(LocalDetector detector) throws Exception {
        float[] result=new float[151];int at=0;
        for(String name:new String[]{"boxes","classes","scores","count"}) {
            FloatBuffer values=buffer(detector,name);
            for(int i=0;i<values.capacity();i++)result[at++]=values.get(i);
        }
        if(at!=result.length)throw new AssertionError("Unexpected tensor size");
        return result;
    }
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Detector probe is restricted to emulators");
            pixels(report);model(runner,report);
            result.putString("stream",report+"RUNTIME_PROBE_PASS\n");runner.finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream",report+"RUNTIME_PROBE_FAIL: "+error);runner.finish(Activity.RESULT_CANCELED,result);
        }
    }

    private static void pixels(StringBuilder report) {
        DetectorInput workspace=new DetectorInput();ByteBuffer identity=null;int cases=0;
        try {
            for(int cycle=0;cycle<2;cycle++)for(int[] size:new int[][]{{640,480},{480,640},{320,320},{137,59},{1,719},{719,1},{113,211}}) {
                Bitmap source=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
                try {
                    // Alternating shapes, alpha and colors expose old image/padding remnants.
                    source.eraseColor(cycle==0?Color.WHITE:Color.TRANSPARENT);
                    Paint paint=new Paint();paint.setColor(cycle==0?Color.rgb(7,131,239):Color.argb(91,250,70,10));
                    new Canvas(source).drawRect(0,0,size[0]*0.7f,size[1]*0.6f,paint);
                    ByteBuffer expected=baseline(source),actual=workspace.prepare(source);
                    if(identity!=null && actual!=identity)throw new AssertionError("Input buffer changed identity");identity=actual;
                    if(!actual.isDirect() || actual.position()!=0 || actual.remaining()!=320*320*3)
                        throw new AssertionError("Input buffer has invalid layout");
                    for(int i=0;i<expected.capacity();i++)if(expected.get(i)!=actual.get(i))
                        throw new AssertionError("RGB parity failed for "+Arrays.toString(size)+" cycle="+cycle+" byte="+i);
                    if(source.isRecycled())throw new AssertionError("Preparing input took ownership of the source");
                    // A consumer may move the buffer position/limit; the next frame must reset both.
                    actual.limit(3);actual.position(3);cases++;
                } finally {source.recycle();}
            }
            Bitmap source=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);
            try {
                source.eraseColor(Color.GRAY);
                for(int i=0;i<10;i++){sink+=baseline(source).get(0);sink+=workspace.prepare(source).get(0);}
                long oldBytes=0,newBytes=0;
                for(int round=0;round<3;round++) {
                    long before=allocated();for(int i=0;i<30;i++)sink+=baseline(source).get(0);long after=allocated();
                    if(before>=0 && after>=before)oldBytes+=after-before;else oldBytes=-1;
                    before=allocated();for(int i=0;i<30;i++)sink+=workspace.prepare(source).get(0);after=allocated();
                    if(before>=0 && after>=before)newBytes+=after-before;else newBytes=-1;
                }
                report.append("PREPROCESS_PARITY_PASS cases=").append(cases).append("; RGB bytes, alternating padding/alpha, fixed input buffer, consumer positions\n");
                report.append("PREPROCESS_ALLOCATION_OBSERVATION rounds=3 frames=90 baselineBytes=").append(oldBytes)
                    .append(" reusedBytes=").append(newBytes).append("; ART process-wide managed allocation, excludes native bitmap memory\n");
            } finally {source.recycle();}
        } finally {workspace.close();workspace.close();}
        Bitmap source=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
        try {try{workspace.prepare(source);throw new AssertionError("Closed workspace accepted an image");}catch(IllegalStateException expected){/* closed */}}
        finally{source.recycle();}
    }

    private static void model(Instrumentation runner,StringBuilder report) throws Exception {
        Bitmap portrait;
        try(java.io.InputStream stream=runner.getContext().getAssets().open("portrait.jpg")){portrait=BitmapFactory.decodeStream(stream);}
        if(portrait==null)throw new AssertionError("Portrait fixture did not decode");
        Bitmap blank=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888),white=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);white.eraseColor(Color.WHITE);
        try(LocalDetector detector=new LocalDetector(runner.getTargetContext())) {
            report.append(detector.tensorReport()).append('\n');
            LocalDetector.Result retained=null;int[] savedIndices=null;float[] savedScores=null;String savedDetections=null;int cases=0;
            for(Bitmap source:new Bitmap[]{portrait,blank,white,portrait}) {
                float[][][] boxes=new float[1][25][4];float[][] classes=new float[1][25],scores=new float[1][25];float[] count=new float[1];
                Map<Integer,Object> outputs=new HashMap<>();outputs.put(0,boxes);outputs.put(1,classes);outputs.put(2,scores);outputs.put(3,count);
                detector.interpreter.runForMultipleInputsOutputs(new Object[]{baseline(source)},outputs);
                for(String name:new String[]{"boxes","classes","scores","count"}) {
                    FloatBuffer buffer=buffer(detector,name);for(int i=0;i<buffer.capacity();i++)buffer.put(i,Float.NaN);
                    buffer.limit(1);buffer.position(1); // Reused outputs must reset limits as well as positions.
                }
                LocalDetector.Result found=detector.detect(new BitmapFrame(source));
                equal(count[0],buffer(detector,"count").get(0));
                for(int i=0;i<25;i++) {
                    equal(classes[0][i],buffer(detector,"classes").get(i));equal(scores[0][i],buffer(detector,"scores").get(i));
                    for(int j=0;j<4;j++)equal(boxes[0][i][j],buffer(detector,"boxes").get(i*4+j));
                }
                if(found.classIndices.length!=Math.round(count[0]))throw new AssertionError("Result count mismatch");
                for(int i=0;i<found.classIndices.length;i++){if(found.classIndices[i]!=Math.round(classes[0][i]))throw new AssertionError("Class mismatch");equal(scores[0][i],found.scores[i]);}
                if(source==portrait && !"人".equals(found.category))throw new AssertionError("Portrait no longer confirms person index 0");
                if(source==blank && !found.category.isEmpty())throw new AssertionError("Black input has a high-score target");
                if(retained!=null && (!Arrays.equals(savedIndices,retained.classIndices) || !Arrays.equals(savedScores,retained.scores)
                    || !savedDetections.equals(describe(retained))))throw new AssertionError("Reused output mutated a previously delivered result");
                if(retained==null){retained=found;savedIndices=found.classIndices.clone();savedScores=found.scores.clone();savedDetections=describe(found);}
                if(source.isRecycled())throw new AssertionError("Detector recycled the worker-owned frame");cases++;
            }
            report.append("MODEL_PARITY_PASS cases=").append(cases).append("; all 151 floats match array-output baseline within 1e-6; person, blank, repeated portrait; delivered results independent\n");
            detector.close();detector.close();
            try{detector.detect(new BitmapFrame(blank));throw new AssertionError("Closed detector accepted a frame");}catch(IllegalStateException expected){/* closed */}
        } finally {portrait.recycle();blank.recycle();white.recycle();}
    }
    private static void equal(float expected,float actual) {
        if(!Float.isFinite(expected) || !Float.isFinite(actual) || Math.abs(expected-actual)>0.000001f)
            throw new AssertionError("Model output differs: "+expected+" vs "+actual);
    }
    private static FloatBuffer buffer(LocalDetector detector,String name) throws Exception {
        Field field=LocalDetector.class.getDeclaredField(name);field.setAccessible(true);return (FloatBuffer)field.get(detector);
    }
    private static String describe(LocalDetector.Result result) {
        StringBuilder out=new StringBuilder(result.category);
        for(Detection d:result.detections)out.append(d.label).append(':').append(d.score).append(':').append(d.left).append(':').append(d.top).append(':').append(d.right).append(':').append(d.bottom).append(':').append(d.risk);
        return out.toString();
    }
    private static long allocated() {
        String value=Debug.getRuntimeStat("art.gc.bytes-allocated");return value==null?-1:Long.parseLong(value);
    }
    /** Frozen v0.4.8 preprocessing, kept only in the test APK as the parity/measurement baseline. */
    private static ByteBuffer baseline(Bitmap source) {
        float scale=Math.min(320f/source.getWidth(),320f/source.getHeight());
        float width=source.getWidth()*scale,height=source.getHeight()*scale,padX=(320-width)/2,padY=(320-height)/2;
        Bitmap padded=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);
        ByteBuffer input=ByteBuffer.allocateDirect(320*320*3).order(ByteOrder.nativeOrder());
        try {
            Canvas canvas=new Canvas(padded);canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(source,null,new RectF(padX,padY,padX+width,padY+height),new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] pixels=new int[320*320];padded.getPixels(pixels,0,320,0,0,320,320);
            for(int pixel:pixels)input.put((byte)Color.red(pixel)).put((byte)Color.green(pixel)).put((byte)Color.blue(pixel));
            input.rewind();return input;
        } finally {padded.recycle();}
    }
}
