package com.malacca.scratchcard;

import java.net.URL;
import java.io.InputStream;
import java.util.ArrayList;

import android.net.Uri;
import android.view.View;
import android.view.MotionEvent;
import androidx.annotation.Nullable;

import android.util.Base64;
import android.app.Activity;
import android.text.TextUtils;
import android.webkit.URLUtil;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuffXfermode;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

class ScratchView extends View implements View.OnTouchListener {
    private ThemedReactContext rnContext;
    private RCTEventEmitter mEventEmitter;

    // 配置
    private float threshold = 0;
    private float brushSize = 0;
    private ReadableMap listeners;

    private String imageUrl = "";
    private String resizeMode = "stretch";
    private int background = Color.GRAY;

    // 最后一次绘制的背景图
    private String lastImageUrl = "";
    private String lastResizeMode = "stretch";
    private int lastBackground = Color.GRAY;

    // 绘制过程相关临时变量
    private boolean init = false;
    private Bitmap image = null;
    private boolean imageInit = false;
    private Path mPath;     // 手势path记录
    private Paint mOutPaint;// 画笔
    private Canvas mCanvas; // 遮罩 canvas
    private Bitmap mBitmap; // 遮罩 bitmap
    private int mLastX;
    private int mLastY;

    // 计算刮开区域的围栏
    private boolean fence = false;
    private int fenceWidth;
    private int fenceHeight;
    private int fenceLeft;
    private int fenceRight;
    private int fenceTop;
    private int fenceBottom;

    // grid 计算刮开区域的相关变量
    private int gridSize;
    private int gridSizeX;
    private int gridSizeY;
    private boolean cleared;
    private int clearPointsCounter;
    private float scratchProgress;
    private ArrayList<ArrayList<Boolean>> grid;

    public ScratchView(Context context) {
        super(context);
    }

    public ScratchView(ThemedReactContext context) {
        super(context);
        rnContext = context;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnTouchListener(this);
        //手势path记录
        mPath = new Path();
        // 设置画笔基础配置
        mOutPaint = new Paint();
        mOutPaint.setDither(true); // 防抖动
        mOutPaint.setAntiAlias(true); // 抗锯齿
        mOutPaint.setStyle(Paint.Style.STROKE);
        mOutPaint.setStrokeJoin(Paint.Join.ROUND); // 圆角
        mOutPaint.setStrokeCap(Paint.Cap.ROUND); // 圆角
        mOutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT)); // 融合模式
    }

    protected void setBackground(@Nullable String background) {
        if (background != null) {
            try {
                this.background = Color.parseColor(background);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    protected void setResizeMode(String resizeMode) {
        this.resizeMode = resizeMode != null ? resizeMode.toLowerCase() : "stretch";
    }

    protected void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    protected void setBrushSize(float brushSize) {
        this.brushSize = Math.max(1, Math.min(100, brushSize));
        if (this.brushSize > 0) {
            mOutPaint.setStrokeWidth(brushSize);
        }
    }

    // 刮开百分比, 都这个值后认为全部刮开, 该值并不是精确值, 所以不要太大
    // 比如设置为90, 可能已达到 90% 的刮开区域, 但没有触发
    protected void setThreshold(float threshold) {
        this.threshold = Math.max(10, Math.min(100, threshold));
    }

    protected void setFence(int left, int top, int width, int height) {
        fence = true;
        fenceLeft = left;
        fenceTop = top;
        fenceWidth = width;
        fenceHeight = height;
    }

    protected void setListeners(ReadableMap listeners) {
        this.listeners = listeners;
    }

    private boolean hasListener(String key) {
        return listeners != null && listeners.hasKey(key) && listeners.getBoolean(key);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                reportTouchState(true);
                mLastX = x;
                mLastY = y;
                mPath.moveTo(mLastX, mLastY);
                break;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.abs(x - mLastX);
                int dy = Math.abs(y - mLastY);
                if (dx > 0 || dy > 0) {
                    mPath.lineTo(x, y);
                    updateGrid(x, y);
                }
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                reportTouchState(false);
                break;
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!init) {
            int width = getWidth();
            int height = getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            init = true;
            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
            drawBackgroundColor();
            reset();
            reportScratchState();
        }
        mCanvas.drawPath(mPath, mOutPaint);
        canvas.drawBitmap(mBitmap, 0, 0, null);
    }

    protected void reset() {
        if (!init) {
            return;
        }
        resetBackground();

        // 尺寸计算只进行一次, 因为使用的网格数据缓存, 变了尺寸, 要有很多计算处理
        // 若使用像素点扫描则没有这个问题, 但性能不好, 干脆不支持动态修改尺寸, 这种场景也很难见到
        // 真的就有这种场景了, 那就 js 端重建一个组件好了
        if (grid != null) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        // 未设置笔触宽度
        if (brushSize == 0) {
            brushSize = ((width > height ? height : width) / 10.0f);
            mOutPaint.setStrokeWidth(brushSize);
        }

        // 刮开百分比, 都这个值后认为全部刮开, 该值并不是精确值, 所以不要太大
        // 比如设置为90, 可能已达到 90% 的刮开区域, 但没有触发
        if (threshold == 0) {
            threshold = 60;
        }

        // 设置围栏, 此时 threshold 指围栏区域刮开百分比, 而不是全部
        if (fence) {
            float density = getResources().getDisplayMetrics().density;
            fenceLeft = Math.max(0, Math.min((int) (fenceLeft * density), width));
            fenceTop = Math.max(0, Math.min((int) (fenceTop * density), height));

            if (fenceWidth > 0) {
                fenceRight = Math.max(0, Math.min(fenceLeft + (int) (fenceWidth * density), width));
            } else {
                fenceRight = width;
            }
            fenceWidth = fenceRight - fenceLeft;

            if (fenceHeight > 0) {
                fenceBottom = Math.max(0, Math.min(fenceTop + (int) (fenceHeight * density), height));
            } else {
                fenceBottom = height;
            }
            fenceHeight = fenceBottom - fenceTop;

            if (fenceRight <= fenceLeft || fenceBottom <= fenceTop) {
                fence = false;
            }
            width = fenceWidth;
            height = fenceHeight;
        }

        // 缓存网格数据
        gridSizeX = (int) Math.ceil(width / (brushSize + 1));
        gridSizeY = (int) Math.ceil(height / (brushSize + 1));
        grid = new ArrayList<>();
        for (int x = 0; x < gridSizeX; x++) {
            grid.add(new ArrayList<Boolean>());
            for (int y = 0; y < gridSizeY; y++) {
                grid.get(x).add(true);
            }
        }
        cleared = false;
        scratchProgress = 0;
        clearPointsCounter = 0;
        gridSize = gridSizeX * gridSizeY;
    }

    private void resetBackground() {
        // 背景图未发送变动, 不再 load img
        if (lastImageUrl.equals(imageUrl)) {
            if (TextUtils.isEmpty(imageUrl)) {
                // 没设置背景图 && 改了背景色
                drawBackgroundColor();
                invalidate();
            } else if (!lastResizeMode.equals(resizeMode) || background != lastBackground) {
                // 改了 resizeMode || 背景色
                imageInit = false;
                drawBackgroundImage();
                invalidate();
            }
            return;
        }

        // 修改了图片
        lastImageUrl = imageUrl;
        lastResizeMode = resizeMode;

        // 背景图改为空了
        if (TextUtils.isEmpty(imageUrl)) {
            drawBackgroundColor();
            invalidate();
            recycleImage();
            return;
        }
        loadImage();
    }

    private void loadImage() {
        final Uri uri = Uri.parse(imageUrl);
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            // local file
            image = BitmapFactory.decodeFile(uri.getPath());
            loadImageEnd();
        } else if ("data".equals(scheme)) {
            // base64
            String encodedImage = imageUrl.substring(imageUrl.indexOf(",")  + 1);
            byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
            image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            loadImageEnd();
        } else if (URLUtil.isValidUrl(imageUrl)) {
            // remote file
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream is = (InputStream) new URL(imageUrl).getContent();
                        image = BitmapFactory.decodeStream(is).copy(Bitmap.Config.ARGB_8888, true);
                        loadImageEndASyn();
                    } catch (Exception e) {
                        recycleImage();
                        loadImageEndASyn();
                    }
                }
            }).start();
        } else {
            // drawable resource
            int imageResourceId = getResources().getIdentifier(imageUrl, "drawable", getContext().getPackageName());
            image = BitmapFactory.decodeResource(getContext().getResources(), imageResourceId);
            loadImageEnd();
        }
    }

    // 图片不是 UI 进程加载回调的, 需要这样才能触发 UI 进程的更新
    private void loadImageEndASyn() {
        Activity activity = rnContext.getCurrentActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(new Runnable(){
            @Override
            public void run() {
                loadImageEnd();
            }
        });
    }

    private void loadImageEnd() {
        imageInit = false;
        drawBackgroundImage();
        invalidate();
        reportImageLoadFinished(image != null);
    }

    private void drawBackgroundImage() {
        if (image == null || imageInit) {
            return;
        }
        imageInit = true;
        drawBackgroundColor();

        // 绘制背景图
        float viewWidth = (float) getWidth();
        float viewHeight = (float) getHeight();
        float imgWidth = (float) image.getWidth();
        float imgHeight = (float) image.getHeight();
        String mode = resizeMode;
        if ("repeat".equals(mode)) {
            if (imgWidth < viewWidth || imgHeight < viewHeight) {
                int xCount = (int) Math.ceil(viewWidth / imgWidth);
                int yCount = (int) Math.ceil(viewHeight / imgHeight);
                for (int x = 0; x < xCount; x++) {
                    for (int y = 0; y < yCount; y++) {
                        mCanvas.drawBitmap(image, (x * imgWidth), (y * imgHeight), null);
                    }
                }
                return;
            }
            mode = "_repeat";
        } else if ("center".equals(mode)) {
            if (imgWidth <= viewWidth && imgHeight <= viewHeight) {
                mCanvas.drawBitmap(
                        image,
                        (viewWidth - imgWidth) / 2,
                        (viewHeight - imgHeight) / 2,
                        null
                );
                return;
            }
            mode = "contain";
        }
        Rect imgRect;
        if ("_repeat".equals(mode)) {
            imgRect = new Rect(0, 0, (int) imgWidth, (int) imgHeight);
        } else {
            int offsetX = 0;
            int offsetY = 0;
            float viewAspect = viewWidth / viewHeight;
            float imgAspect = imgWidth / imgHeight;
            switch (mode) {
                case "cover":
                    if (imgAspect > viewAspect) {
                        offsetX = (int) (((viewHeight * imgAspect) - viewWidth) / 2.0f);
                    } else {
                        offsetY = (int) (((viewWidth / imgAspect) - viewHeight) / 2.0f);
                    }
                    break;
                case "contain":
                    if (imgAspect < viewAspect) {
                        offsetX = (int) (((viewHeight * imgAspect) - viewWidth) / 2.0f);
                    } else {
                        offsetY = (int) (((viewWidth / imgAspect) - viewHeight) / 2.0f);
                    }
                    break;
            }
            imgRect = new Rect(-offsetX, -offsetY, (int) viewWidth + offsetX, (int) viewHeight + offsetY);
        }
        mCanvas.drawBitmap(image, null, imgRect, null);
    }

    private void drawBackgroundColor() {
        lastBackground = this.background;
        Paint background = new Paint();
        background.setStyle(Paint.Style.FILL);
        background.setColor(this.background);
        mCanvas.drawRect(0, 0, getWidth(), getHeight(), background);
    }

    private void updateGrid(int x, int y) {
        if (cleared && !hasListener("onProgress")) {
            return;
        }
        float viewWidth, viewHeight;
        if (fence) {
            if (x < fenceLeft || x > fenceRight || y < fenceTop || y > fenceBottom) {
                return;
            }
            viewWidth = fenceWidth;
            viewHeight = fenceHeight;
            x -= fenceLeft;
            y -= fenceTop;
        } else {
            viewWidth = getWidth();
            viewHeight = getHeight();
        }
        int pointInGridX = Math.round((Math.max(Math.min(x, viewWidth), 0) / viewWidth) * (gridSizeX - 1.0f));
        int pointInGridY = Math.round((Math.max(Math.min(y, viewHeight), 0) / viewHeight) * (gridSizeY - 1.0f));
        if (!grid.get(pointInGridX).get(pointInGridY)) {
            return;
        }
        grid.get(pointInGridX).set(pointInGridY, false);
        clearPointsCounter++;
        scratchProgress = ((float) clearPointsCounter) / gridSize * 100.0f;
        reportScratchProgress();
        if (!cleared && scratchProgress > threshold) {
            cleared = true;
            reportScratchState();
        }
    }

    private void reportScratchState() {
        String e = cleared ? "onDone" : "onInit";
        if (cleared || hasListener(e)) {
            WritableMap event = Arguments.createMap();
            event.putString("event", e);
            sendEvent(event);
        }
    }

    private void reportScratchProgress() {
        if (hasListener("onProgress")) {
            WritableMap event = Arguments.createMap();
            event.putString("event", "onProgress");
            event.putDouble("extra", Math.round(scratchProgress * 100.0f) / 100.0);
            sendEvent(event);
        }
    }

    private void reportImageLoadFinished(boolean success) {
        String e = success ? "onImageLoad" : "onImageError";
        if (hasListener(e)) {
            WritableMap event = Arguments.createMap();
            event.putString("event", e);
            sendEvent(event);
        }
    }

    private void reportTouchState(boolean start) {
        String e = start ? "onTouchStart" : "onTouchEnd";
        if (hasListener(e)) {
            WritableMap event = Arguments.createMap();
            event.putString("event", e);
            sendEvent(event);
        }
    }

    private void sendEvent(WritableMap event) {
        if (mEventEmitter == null) {
            mEventEmitter = rnContext.getJSModule(RCTEventEmitter.class);
        }
        mEventEmitter.receiveEvent(getId(), RNTScratchViewManager.EVENT_NAME, event);
    }

    protected void destroy() {
        if (mCanvas != null) {
            mCanvas.setBitmap(null);
            mCanvas = null;
        }
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
            mBitmap = null;
        }
        recycleImage();
    }

    private void recycleImage() {
        if (image != null && !image.isRecycled()) {
            image.recycle();
            image = null;
        }
    }
}