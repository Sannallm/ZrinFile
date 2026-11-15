package com.zrinfile.app.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/** 支持双指缩放、双击缩放与拖动查看的图片视图。 */
public class ZoomImageView extends AppCompatImageView {

    private final Matrix baseMatrix = new Matrix();
    private final Matrix suppMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();
    private final float[] values = new float[9];
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float minScale = 1f;
    private float maxScale = 8f;
    private float userScale = 1f;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float target = userScale * factor;
                if (target < minScale) target = minScale;
                if (target > maxScale) target = maxScale;
                float applied = target / userScale;
                userScale = target;
                suppMatrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
                clampTranslation();
                applyMatrix();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (userScale > 1.25f) {
                    resetZoom();
                } else {
                    zoomTo(2.5f, e.getX(), e.getY());
                }
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        post(new Runnable() {
            @Override
            public void run() {
                configureBase();
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        configureBase();
    }

    private void configureBase() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float scale = Math.min(getWidth() / dw, getHeight() / dh);
        baseMatrix.reset();
        baseMatrix.postScale(scale, scale);
        baseMatrix.postTranslate((getWidth() - dw * scale) / 2f, (getHeight() - dh * scale) / 2f);
        suppMatrix.reset();
        userScale = 1f;
        applyMatrix();
    }

    private void resetZoom() {
        suppMatrix.reset();
        userScale = 1f;
        applyMatrix();
    }

    public void zoomTo(float scale, float fx, float fy) {
        float target = Math.max(minScale, Math.min(maxScale, scale));
        float applied = target / userScale;
        userScale = target;
        suppMatrix.postScale(applied, applied, fx, fy);
        clampTranslation();
        applyMatrix();
    }

    private void applyMatrix() {
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(suppMatrix);
        setImageMatrix(drawMatrix);
        invalidate();
    }

    private void clampTranslation() {
        Drawable d = getDrawable();
        if (d == null) return;
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(suppMatrix);
        drawMatrix.getValues(values);
        float[] rect = new float[]{0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight()};
        drawMatrix.mapPoints(rect);
        float w = rect[2] - rect[0];
        float h = rect[3] - rect[1];
        float dx = 0;
        float dy = 0;
        if (w <= getWidth()) {
            dx = (getWidth() - w) / 2f - rect[0];
        } else {
            if (rect[0] > 0) dx = -rect[0];
            if (rect[2] < getWidth()) dx = getWidth() - rect[2];
        }
        if (h <= getHeight()) {
            dy = (getHeight() - h) / 2f - rect[1];
        } else {
            if (rect[1] > 0) dy = -rect[1];
            if (rect[3] < getHeight()) dy = getHeight() - rect[3];
        }
        suppMatrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    lastX = event.getX();
                    lastY = event.getY();
                    suppMatrix.postTranslate(dx, dy);
                    clampTranslation();
                    applyMatrix();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return true;
    }
}
