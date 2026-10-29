package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

/** 独立worker串行调用；固定许可核验模型，UINT8输入和4个输出逐项校验。 */
final class LocalDetector implements InferenceWorker.Detector<BitmapFrame,LocalDetector.Result> {
    static final boolean AVAILABLE=true;
    final Interpreter interpreter;
    final List<String> labels;
    private DetectorInput input;
    private Object[] inputs;
    private final FloatBuffer boxes=outputBuffer(100),classes=outputBuffer(25),scores=outputBuffer(25),count=outputBuffer(1);
    private final Map<Integer,Object> outputs=new HashMap<>();
    private final float[] box=new float[4];
    private boolean closed;
    private boolean firstInference=true;
    static final class Result {
        final String category;
        final List<Detection> detections;
        final int[] classIndices;
        final float[] scores;
        Result(String category,int[] indices,float[] scores,List<Detection> detections){this.category=category;this.classIndices=indices;this.scores=scores;this.detections=Collections.unmodifiableList(new ArrayList<>(detections));}
    }
    LocalDetector(Context context) throws IOException {
        labels=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("labelmap.txt"),java.nio.charset.StandardCharsets.UTF_8))) {String line;while((line=reader.readLine())!=null)labels.add(line);}
        if(labels.size()!=90 || !"person".equals(labels.get(0)))throw new IOException("模型标签映射不匹配");
        try(AssetFileDescriptor fd=context.getAssets().openFd("efficientdet_lite0_v1.tflite");FileInputStream stream=new FileInputStream(fd.getFileDescriptor())) {
            ByteBuffer model=stream.getChannel().map(FileChannel.MapMode.READ_ONLY,fd.getStartOffset(),fd.getDeclaredLength());
            interpreter=new Interpreter(model,new Interpreter.Options().setNumThreads(2));
        }
        try {
            if(interpreter.getInputTensorCount()!=1 || interpreter.getOutputTensorCount()!=4)throw new IOException("模型张量数量不匹配");
            if(interpreter.getInputTensor(0).dataType()!=DataType.UINT8 || !Arrays.equals(interpreter.getInputTensor(0).shape(),new int[]{1,320,320,3}))throw new IOException("模型输入不匹配");
            int[][] shapes={{1,25,4},{1,25},{1,25},{1}};
            for(int i=0;i<4;i++)if(interpreter.getOutputTensor(i).dataType()!=DataType.FLOAT32 || !Arrays.equals(interpreter.getOutputTensor(i).shape(),shapes[i]))throw new IOException("模型输出不匹配："+i);
            input=new DetectorInput();inputs=new Object[1];
            outputs.put(0,boxes);outputs.put(1,classes);outputs.put(2,scores);outputs.put(3,count);
        }catch(IOException|RuntimeException|Error e){interpreter.close();if(input!=null)input.close();throw e;}
    }
    @Override public Result detect(BitmapFrame frame) {
        if(closed)throw new IllegalStateException("识别器已释放");
        long started=android.os.SystemClock.elapsedRealtime();
        inputs[0]=input.prepare(frame.bitmap);
        boxes.clear();classes.clear();scores.clear();count.clear();
        long prepared=android.os.SystemClock.elapsedRealtime();
        interpreter.runForMultipleInputsOutputs(inputs,outputs);
        long finished=android.os.SystemClock.elapsedRealtime();
        if(firstInference || finished-started>=500)android.util.Log.i("WalkBuddyAnalysis","Inference prepareMs="+(prepared-started)
            +" nativeMs="+(finished-prepared)+" first="+firstInference);
        firstInference=false;
        float total=count.get(0);
        if(!Float.isFinite(total) || total<0 || total>25)throw new IllegalStateException("模型检测数量无效");
        int size=Math.round(total);int[] indices=new int[size];float[] values=new float[size];String category="";List<Detection> detections=new ArrayList<>();
        for(int i=0;i<size;i++) {
            float categoryIndex=classes.get(i);
            if(!Float.isFinite(categoryIndex) || Math.abs(categoryIndex-Math.round(categoryIndex))>0.001f)throw new IllegalStateException("模型类别值无效");
            for(int j=0;j<4;j++){box[j]=boxes.get(i*4+j);if(!Float.isFinite(box[j]))throw new IllegalStateException("模型框坐标无效");}
            int index=Math.round(categoryIndex);float score=scores.get(i);
            if(index<0 || index>=labels.size() || !Float.isFinite(score) || score<0 || score>1)throw new IllegalStateException("模型结果/标签索引无效");
            indices[i]=index;values[i]=score;String name=labels.get(index);
            if(score>=0.35f && !"???".equals(name)) {
                Detection detection=Detection.fromModel(chinese(name),score,box,input.width,input.height,input.padX,input.padY);
                if(detection!=null){detections.add(detection);if(category.isEmpty() && score>=0.5f)category=detection.label;}
            }
        }
        return new Result(category,indices,values,detections);
    }
    String tensorReport(){StringBuilder out=new StringBuilder();out.append("input ").append(interpreter.getInputTensor(0).name()).append(" ").append(interpreter.getInputTensor(0).dataType()).append(" ").append(Arrays.toString(interpreter.getInputTensor(0).shape()));for(int i=0;i<4;i++)out.append("\noutput ").append(i).append(" ").append(interpreter.getOutputTensor(i).name()).append(" ").append(interpreter.getOutputTensor(i).dataType()).append(" ").append(Arrays.toString(interpreter.getOutputTensor(i).shape()));return out.toString();}
    private static String chinese(String name){switch(name){case"person":return"人";case"bicycle":return"自行车";case"car":return"汽车";case"motorcycle":return"摩托车";case"bus":return"公交车";case"truck":return"卡车";case"chair":return"椅子";case"bench":return"长凳";case"dining table":return"桌子";case"couch":return"沙发";case"potted plant":return"盆栽";case"dog":return"狗";case"cat":return"猫";case"suitcase":return"行李箱";case"backpack":return"背包";case"bottle":return"瓶子";case"bed":return"床";case"stop sign":return"停车标志";case"fire hydrant":return"消防栓";default:return name;}}
    private static FloatBuffer outputBuffer(int size){return ByteBuffer.allocateDirect(size*Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();}
    @Override public void close(){if(closed)return;closed=true;try{interpreter.close();}finally{input.close();input=null;inputs=null;outputs.clear();}}
}
