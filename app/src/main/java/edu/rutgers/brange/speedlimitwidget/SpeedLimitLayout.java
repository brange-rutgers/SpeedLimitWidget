package edu.rutgers.brange.speedlimitwidget;

import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

public class SpeedLimitLayout extends ConstraintLayout {
    private static TextView speedLimitTextView;
    private static ImageView speedLimitImageView;
    private static ImageView closeButtonImageView;

    public SpeedLimitLayout(Context context) {
        super(context);
        init();
    }

    public SpeedLimitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpeedLimitLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.speed_limit_layout, this);
        this.speedLimitTextView = findViewById(R.id.speed_limit_text);
        this.speedLimitImageView = findViewById(R.id.speed_limit_image);
        this.closeButtonImageView = findViewById(R.id.close_btn);

        ViewTreeObserver vto = this.speedLimitImageView.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                speedLimitImageView.getViewTreeObserver().removeOnPreDrawListener(this);
                float finalHeight = speedLimitImageView.getMeasuredHeight();
                float finalWidth = speedLimitImageView.getMeasuredWidth();
                //speedLimitTextView.setText("Height: " + finalHeight + " Width: " + finalWidth);
                //double scale = Math.min(width / 2.25, height / 4.0); //See the logcat >>> width = 2.25 and heigt = 4.0
                //tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, (int) (initialSize * scale));
                float size = speedLimitTextView.getTextSize();
                return true;
            }
        });
    }
}