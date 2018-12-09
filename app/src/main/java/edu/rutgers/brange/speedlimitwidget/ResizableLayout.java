package edu.rutgers.brange.speedlimitwidget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.RelativeLayout;

public class ResizableLayout extends RelativeLayout {
    private ScaleGestureDetector mScaleDetector;
    private float mScaleFactor = 1.f;

    static final float MIN_FACTOR = 1.f;
    static final float MAX_FACTOR = 4.0f;


    public ResizableLayout(Context context) {
        super(context);
        init(context);
    }

    public ResizableLayout(Context context, AttributeSet attr) {
        super(context, attr);
        init(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the ScaleGestureDetector inspect all events.
        return mScaleDetector.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.scale(mScaleFactor, mScaleFactor);

        // onDraw() code goes here

        canvas.restore();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean performLongClick() {
        return super.performLongClick();
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        this.setWillNotDraw(false);
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(MIN_FACTOR, Math.min(mScaleFactor, MAX_FACTOR));

            invalidate();
            return true;
        }
    }
}
