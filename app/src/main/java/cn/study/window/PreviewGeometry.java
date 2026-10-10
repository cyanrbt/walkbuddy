package cn.study.window;

/** TextureView 的默认纹理已补偿 sensor rotation，但被铺满到 view。后置镜头，无镜像。 */
final class PreviewGeometry {
    final float[] matrix;
    final float left,top,width,height;
    final int relativeRotation,viewWidth,viewHeight,naturalWidth,naturalHeight,displayDegrees;
    PreviewGeometry(int bufferWidth,int bufferHeight,int sensorDegrees,int displayDegrees,int viewWidth,int viewHeight) {
        if(bufferWidth<=0 || bufferHeight<=0 || viewWidth<=0 || viewHeight<=0 || sensorDegrees%90!=0 || displayDegrees%90!=0)
            throw new IllegalArgumentException("尺寸必须为正，方向必须为90度整数倍");
        this.viewWidth=viewWidth;this.viewHeight=viewHeight;
        int sensor=Math.floorMod(sensorDegrees,360),display=Math.floorMod(displayDegrees,360);
        relativeRotation=Math.floorMod(sensor-display,360);
        this.displayDegrees=display;
        naturalWidth=sensor%180==0?bufferWidth:bufferHeight;
        naturalHeight=sensor%180==0?bufferHeight:bufferWidth;
        float rotatedWidth=display%180==0?naturalWidth:naturalHeight;
        float rotatedHeight=display%180==0?naturalHeight:naturalWidth;
        float fit=Math.min(viewWidth/rotatedWidth,viewHeight/rotatedHeight);
        width=rotatedWidth*fit;height=rotatedHeight*fit;
        left=(viewWidth-width)/2;top=(viewHeight-height)/2;
        int[] cos={1,0,-1,0},sin={0,-1,0,1};int quadrant=display/90;
        float a=cos[quadrant]*naturalWidth/(float)viewWidth*fit;
        float b=-sin[quadrant]*naturalHeight/(float)viewHeight*fit;
        float c=sin[quadrant]*naturalWidth/(float)viewWidth*fit;
        float d=cos[quadrant]*naturalHeight/(float)viewHeight*fit;
        matrix=new float[]{a,b,viewWidth/2f-a*viewWidth/2f-b*viewHeight/2f,
            c,d,viewHeight/2f-c*viewWidth/2f-d*viewHeight/2f,0,0,1};
    }
    float[] map(float x,float y){return new float[]{matrix[0]*x+matrix[1]*y+matrix[2],matrix[3]*x+matrix[4]*y+matrix[5]};}
}
