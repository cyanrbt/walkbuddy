package cn.study.window;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

/** 独立worker串行调用；固定许可核验模型，UINT8输入和4个输出逐项校验。 */
final class LocalDetector implements InferenceWorker.Detector<BitmapFrame,LocalDetector.Result> {
    static final boolean AVAILABLE=true;
    static final Set<String> SUPPORTED=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("person","bicycle","car","motorcycle","bus","truck","chair","bench")));
    final Interpreter interpreter;
    final List<String> labels;
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
        if(labels.size()!=90 || !"person".equals(labels.get(0)) || !labels.containsAll(SUPPORTED))throw new IOException("模型标签映射不匹配");
        try(AssetFileDescriptor fd=context.getAssets().openFd("efficientdet_lite0_v1.tflite");FileInputStream stream=new FileInputStream(fd.getFileDescriptor())) {
            ByteBuffer model=stream.getChannel().map(FileChannel.MapMode.READ_ONLY,fd.getStartOffset(),fd.getDeclaredLength());
            interpreter=new Interpreter(model,new Interpreter.Options().setNumThreads(2));
        }
        try {
            if(interpreter.getInputTensorCount()!=1 || interpreter.getOutputTensorCount()!=4)throw new IOException("模型张量数量不匹配");
            if(interpreter.getInputTensor(0).dataType()!=DataType.UINT8 || !Arrays.equals(interpreter.getInputTensor(0).shape(),new int[]{1,320,320,3}))throw new IOException("模型输入不匹配");
            int[][] shapes={{1,25,4},{1,25},{1,25},{1}};
            for(int i=0;i<4;i++)if(interpreter.getOutputTensor(i).dataType()!=DataType.FLOAT32 || !Arrays.equals(interpreter.getOutputTensor(i).shape(),shapes[i]))throw new IOException("模型输出不匹配："+i);
        }catch(IOException|RuntimeException e){interpreter.close();throw e;}
    }
    @Override public Result detect(BitmapFrame frame) {
        Bitmap source=frame.bitmap;
        float scale=Math.min(320f/source.getWidth(),320f/source.getHeight());
        float width=source.getWidth()*scale,height=source.getHeight()*scale,padX=(320-width)/2,padY=(320-height)/2;
        Bitmap padded=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);
        ByteBuffer input=ByteBuffer.allocateDirect(320*320*3).order(ByteOrder.nativeOrder());
        try {
            Canvas canvas=new Canvas(padded);canvas.drawColor(Color.BLACK);Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(source,null,new RectF(padX,padY,padX+width,padY+height),paint);
            int[] pixels=new int[320*320];padded.getPixels(pixels,0,320,0,0,320,320);
            for(int pixel:pixels){input.put((byte)Color.red(pixel));input.put((byte)Color.green(pixel));input.put((byte)Color.blue(pixel));}input.rewind();
        }finally{padded.recycle();}
        float[][][] boxes=new float[1][25][4];float[][] classes=new float[1][25],scores=new float[1][25];float[] count=new float[1];
        Map<Integer,Object> output=new HashMap<>();output.put(0,boxes);output.put(1,classes);output.put(2,scores);output.put(3,count);
        interpreter.runForMultipleInputsOutputs(new Object[]{input},output);
        if(!Float.isFinite(count[0]) || count[0]<0 || count[0]>25)throw new IllegalStateException("模型检测数量无效");
        int size=Math.round(count[0]);int[] indices=new int[size];float[] values=new float[size];String category="";List<Detection> detections=new ArrayList<>();
        for(int i=0;i<size;i++) {
            if(!Float.isFinite(classes[0][i]) || Math.abs(classes[0][i]-Math.round(classes[0][i]))>0.001f)throw new IllegalStateException("模型类别值无效");
            for(float coordinate:boxes[0][i])if(!Float.isFinite(coordinate))throw new IllegalStateException("模型框坐标无效");
            int index=Math.round(classes[0][i]);float score=scores[0][i];
            if(index<0 || index>=labels.size() || !Float.isFinite(score) || score<0 || score>1)throw new IllegalStateException("模型结果/标签索引无效");
            indices[i]=index;values[i]=score;String name=labels.get(index);
            if(score>=0.35f && SUPPORTED.contains(name)) {
                Detection detection=Detection.fromModel(chinese(name),score,boxes[0][i],width,height,padX,padY);
                if(detection!=null){detections.add(detection);if(category.isEmpty() && detection.attention())category=detection.label;}
            }
        }
        return new Result(category,indices,values,detections);
    }
    String tensorReport(){StringBuilder out=new StringBuilder();out.append("input ").append(interpreter.getInputTensor(0).name()).append(" ").append(interpreter.getInputTensor(0).dataType()).append(" ").append(Arrays.toString(interpreter.getInputTensor(0).shape()));for(int i=0;i<4;i++)out.append("\noutput ").append(i).append(" ").append(interpreter.getOutputTensor(i).name()).append(" ").append(interpreter.getOutputTensor(i).dataType()).append(" ").append(Arrays.toString(interpreter.getOutputTensor(i).shape()));return out.toString();}
    private static String chinese(String name){switch(name){case"person":return"人";case"bicycle":return"自行车";case"car":return"汽车";case"motorcycle":return"摩托车";case"bus":return"公交车";case"truck":return"卡车";case"chair":return"椅子";case"bench":return"长凳";default:return"";}}
    @Override public void close(){interpreter.close();}
}
