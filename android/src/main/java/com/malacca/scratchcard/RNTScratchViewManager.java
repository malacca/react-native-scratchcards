package com.malacca.scratchcard;

import java.util.Map;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.common.MapBuilder;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

public class RNTScratchViewManager extends SimpleViewManager<ScratchView> {
    public static final String REACT_CLASS = "RNTScratchCards";
    public static final String EVENT_IMAGE_LOAD = "onImageLoadFinished";
    public static final String EVENT_TOUCH_STATE_CHANGED = "onTouchStateChanged";
    public static final String EVENT_SCRATCH_PROGRESS_CHANGED = "onScratchProgressChanged";
    public static final String EVENT_SCRATCH_DONE = "onScratchDone";

    @ReactProp(name = "source")
    public void setSource(final ScratchView scratchView, ReadableMap source) {
        if (scratchView != null) {
            String imageUrl;
            try {
                imageUrl = source.getString("uri");
                scratchView.setImageUrl(imageUrl);
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    @ReactProp(name = "background")
    public void setBackground(final ScratchView scratchView, @Nullable String background) {
        if (scratchView != null) {
            scratchView.setBackground(background);
        }
    }

    @ReactProp(name = "resizeMode")
    public void setResizeMode(final ScratchView scratchView, @Nullable String resizeMode) {
        if (scratchView != null) {
            scratchView.setResizeMode(resizeMode);
        }
    }

    @ReactProp(name = "brushSize")
    public void setBrushSize(final ScratchView scratchView, float brushSize) {
        if (scratchView != null) {
            scratchView.setBrushSize(brushSize);
        }
    }

    @ReactProp(name = "threshold")
    public void setThreshold(final ScratchView scratchView, float threshold) {
        if (scratchView != null) {
            scratchView.setThreshold(threshold);
        }
    }

    @ReactProp(name = "fence")
    public void setFence(final ScratchView scratchView, ReadableArray fence) {
        if (scratchView != null) {
            int size = fence.size();
            if (size > 1) {
                int left = fence.getInt(0);
                int top = fence.getInt(1);
                int width = size > 2 ? fence.getInt(2) : 0;
                int height = size > 3 ? fence.getInt(3) : 0;
                scratchView.setFence(left, top, width, height);
            }
        }
    }

    @ReactProp(name = "ignoreProgress")
    public void ignoreProgress(final ScratchView scratchView, Boolean ignore) {
        if (scratchView != null) {
            scratchView.ignoreProgress(ignore);
        }
    }

    @ReactProp(name = "ignoreDone")
    public void ignoreDone(final ScratchView scratchView, Boolean ignore) {
        if (scratchView != null) {
            scratchView.ignoreDone(ignore);
        }
    }

    @Override
    public @NonNull String getName() {
        return REACT_CLASS;
    }

    @Override
    public @NonNull ScratchView createViewInstance(@NonNull ThemedReactContext context) {
        return new ScratchView(context);
    }

    @Override
    public @Nullable Map<String, Integer> getCommandsMap() {
        return MapBuilder.of("reset", 0);
    }

    @Override
    public void receiveCommand(@NonNull ScratchView view, String commandId, @Nullable ReadableArray args) {
        super.receiveCommand(view, commandId, args);
        if (commandId != null && commandId.length() > 0) {
            view.reset();
        }
    }

    public @Nullable Map getExportedCustomBubblingEventTypeConstants() {
        return MapBuilder.builder()
                .put(EVENT_IMAGE_LOAD,
                        MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", EVENT_IMAGE_LOAD)))
                .put(EVENT_TOUCH_STATE_CHANGED,
                        MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", EVENT_TOUCH_STATE_CHANGED)))
                .put(EVENT_SCRATCH_PROGRESS_CHANGED,
                    MapBuilder.of("phasedRegistrationNames",
                        MapBuilder.of("bubbled", EVENT_SCRATCH_PROGRESS_CHANGED)))
                .put(EVENT_SCRATCH_DONE,
                        MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", EVENT_SCRATCH_DONE)))
                .build();
    }
}