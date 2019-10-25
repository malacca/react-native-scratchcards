package com.malacca.scratchcard;

import java.net.URL;
import java.io.InputStream;
import java.util.ArrayList;

import android.util.Base64;
import android.webkit.URLUtil;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuffXfermode;

import android.net.Uri;
import android.view.View;
import android.view.MotionEvent;
import android.util.AttributeSet;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;

public class ScratchView extends View implements View.OnTouchListener {
    // 配置
    private String imageUrl = null;
    private int background = -1;
    private float threshold = 0;
    private float brushSize = 0;
    private String resizeMode = "stretch";
    private boolean ignoreProgress = false;
    private boolean ignoreDone = false;

    // 绘制过程相关临时变量
    private boolean init = false;
    private Bitmap image = null;
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
        init();
    }

    public ScratchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScratchView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnTouchListener(this);
        mPath = new Path();
        // 设置画笔基础配置
        mOutPaint = new Paint();
        mOutPaint.setDither(true); // 防抖动
        mOutPaint.setAntiAlias(true); // 抗锯齿
        mOutPaint.setStyle(Paint.Style.STROKE);
        mOutPaint.setStrokeJoin(Paint.Join.ROUND); // 圆角
        mOutPaint.setStrokeCap(Paint.Cap.ROUND); // 圆角
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setBackground(@Nullable String background) {
        if (background != null) {
            try {
                this.background = Color.parseColor(background);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void setResizeMode(String resizeMode) {
        if (resizeMode != null) {
            this.resizeMode = resizeMode.toLowerCase();
        }
    }

    public void setBrushSize(float brushSize) {
        this.brushSize = brushSize;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public void setFence(int left, int top, int width, int height) {
        this.fence = true;
        this.fenceLeft = left;
        this.fenceTop = top;
        this.fenceWidth = width;
        this.fenceHeight = height;
    }

    public void ignoreProgress(Boolean ignore) {
        this.ignoreProgress = ignore;
    }

    public void ignoreDone(Boolean ignore) {
        this.ignoreDone = ignore;
    }

    public void reportImageLoadFinished(boolean success) {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putBoolean("success", success);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class).receiveEvent(getId(),
                    RNTScratchViewManager.EVENT_IMAGE_LOAD, event);
        }
    }

    public void reportTouchState(boolean state) {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putBoolean("touchState", state);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class).receiveEvent(getId(),
                    RNTScratchViewManager.EVENT_TOUCH_STATE_CHANGED, event);
        }
    }

    public void reportScratchProgress() {
        if (ignoreProgress) {
            return;
        }
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putDouble("progressValue", Math.round(scratchProgress * 100.0f) / 100.0);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class).receiveEvent(getId(),
                    RNTScratchViewManager.EVENT_SCRATCH_PROGRESS_CHANGED, event);
        }
    }

    public void reportScratchState() {
        if (ignoreDone) {
            return;
        }
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putBoolean("isScratchDone", cleared);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class).receiveEvent(getId(),
                    RNTScratchViewManager.EVENT_SCRATCH_DONE, event);
        }
    }

    public void reset() {
        int width = getWidth();
        int height = getHeight();
        // 刮开百分比, 都这个值后认为全部刮开, 该值并不是精确值, 所以不要太大
        // 比如设置为90, 可能已达到 90% 的刮开区域, 但没有触发
        threshold = threshold > 0 ? threshold : 60;

        // 笔触宽度
        if (brushSize > 0) {
            brushSize = Math.max(1, Math.min(100, brushSize));
        } else {
            brushSize = ((width > height ? height : width) / 10.0f);
        }
        mOutPaint.setStrokeWidth(brushSize);

        // 设置围栏, 此时 threshold 指围栏区域刮开百分比, 而不是全部
        if (fence && !(ignoreProgress && ignoreDone) ) {
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
        }

        // bitmap初始化
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        drawBackground();
        loadImage();
        initGrid();
        reportScratchProgress();
        reportScratchState();
    }

    private void loadImage() {
        if (imageUrl == null) {
            return;
        }
        final Uri uri = Uri.parse(imageUrl);
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            // local file
            image = BitmapFactory.decodeFile(uri.getPath());
            reportImageLoadFinished(image != null);
            invalidate();
        } else if ("data".equals(scheme)) {
            // base64
            String encodedImage = imageUrl.substring(imageUrl.indexOf(",")  + 1);
            byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
            image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            reportImageLoadFinished(image != null);
            invalidate();
        } else if (URLUtil.isValidUrl(imageUrl)) {
            // remote file
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream is = (InputStream) new URL(imageUrl).getContent();
                        image = BitmapFactory.decodeStream(is).copy(Bitmap.Config.ARGB_8888, true);
                        reportImageLoadFinished(true);
                        invalidate();
                    } catch (Exception e) {
                        reportImageLoadFinished(false);
                        e.printStackTrace();
                    }
                }
            }).start();
        } else {
            // drawable resource
            int imageResourceId = getResources().getIdentifier(imageUrl, "drawable", getContext().getPackageName());
            image = BitmapFactory.decodeResource(getContext().getResources(), imageResourceId);
            reportImageLoadFinished(image != null);
            invalidate();
        }
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
        if (!init && getWidth() > 0) {
            init = true;
            reset();
        }
        drawPath();
        canvas.drawBitmap(mBitmap, 0, 0, null);
    }

    private void drawPath() {
        drawImage();
        mOutPaint.setStyle(Paint.Style.STROKE);
        mOutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mCanvas.drawPath(mPath, mOutPaint);
    }

    private void drawImage() {
        if (image == null) {
            return;
        }
        drawBackground();

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
                image = null;
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
                image = null;
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
        image = null;
    }

    private void drawBackground() {
        Paint background = new Paint();
        background.setStyle(Paint.Style.FILL);
        background.setColor(this.background != -1 ? this.background : Color.GRAY);
        mCanvas.drawRect(0, 0, getWidth(), getHeight(), background);
    }

    private void initGrid() {
        if (ignoreProgress && ignoreDone) {
            return;
        }
        int width = fence ? fenceWidth : getWidth();
        int height = fence ? fenceHeight : getHeight();
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

    private void updateGrid(int x, int y) {
        if (ignoreProgress && ignoreDone) {
            return;
        }
        if (ignoreProgress && cleared) {
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
}