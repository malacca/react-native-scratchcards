package com.malacca.scratchcard;

import java.util.Map;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.common.MapBuilder;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ReactStylesDiffMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

class RNTScratchViewManager extends SimpleViewManager<ScratchView> {
    static final String EVENT_NAME = "onScratchEvent";

    @Override
    public @NonNull String getName() {
        return "RNTScratchCards";
    }

    @Override
    public @NonNull ScratchView createViewInstance(@NonNull ThemedReactContext context) {
        return new ScratchView(context);
    }

    @Override
    public void onDropViewInstance(@NonNull ScratchView view) {
        super.onDropViewInstance(view);
        view.destroy();
    }

    @Override
    public void updateProperties(@NonNull ScratchView view, ReactStylesDiffMap props) {
        super.updateProperties(view, props);
        // 若背景发生变化, 可进行更新
        if (props.hasKey("background") || props.hasKey("source") || props.hasKey("resizeMode")) {
            view.reset();
        }
    }

    @ReactProp(name = "background")
    public void setBackground(ScratchView scratchView, @Nullable String background) {
        scratchView.setBackground(background);
    }

    @ReactProp(name = "source")
    public void setSource(ScratchView scratchView, ReadableMap source) {
        try {
            String imageUrl = source.getString("uri");
            scratchView.setImageUrl(imageUrl);
        } catch (Exception e) {
            // do nothing
        }
    }

    @ReactProp(name = "resizeMode")
    public void setResizeMode(ScratchView scratchView, @Nullable String resizeMode) {
        scratchView.setResizeMode(resizeMode);
    }

    @ReactProp(name = "brushSize")
    public void setBrushSize(ScratchView scratchView, float brushSize) {
        scratchView.setBrushSize(brushSize);
    }

    @ReactProp(name = "threshold")
    public void setThreshold(ScratchView scratchView, float threshold) {
        scratchView.setThreshold(threshold);
    }

    @ReactProp(name = "fence")
    public void setFence(ScratchView scratchView, ReadableArray fence) {
        int size = fence.size();
        if (size > 1) {
            int left = fence.getInt(0);
            int top = fence.getInt(1);
            int width = size > 2 ? fence.getInt(2) : 0;
            int height = size > 3 ? fence.getInt(3) : 0;
            scratchView.setFence(left, top, width, height);
        }
    }

    @ReactProp(name = "listeners")
    public void setListeners(ScratchView scratchView, ReadableMap listeners) {
        scratchView.setListeners(listeners);
    }

    @Override
    public @Nullable Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.<String, Object>builder()
                .put(EVENT_NAME, MapBuilder.of("registrationName", EVENT_NAME))
                .build();
    }
}