package cl.coders.faketraveler.joystick;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Circular touch-pad. Emits an (angle, magnitude) pair to a listener whenever the thumb
 * moves; emits {@link OnMoveListener#onRelease()} when the user lifts.
 */
public class JoystickView extends View {

    public interface OnMoveListener {
        /**
         * @param angleRad  0 = north, π/2 = east, π = south, -π/2 = west
         * @param magnitude 0..1, clamped at the pad radius
         */
        void onMove(double angleRad, double magnitude);
        void onRelease();
    }

    @Nullable private OnMoveListener listener;
    private float cx, cy;
    private float padRadius;
    private float thumbX, thumbY;

    @NonNull private final Paint padPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public JoystickView(@NonNull Context ctx) { this(ctx, null); }

    public JoystickView(@NonNull Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
        padPaint.setColor(0x40444444);
        thumbPaint.setColor(Color.WHITE);
    }

    public void setOnMoveListener(@Nullable OnMoveListener l) { this.listener = l; }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cx = w / 2f;
        cy = h / 2f;
        padRadius = Math.min(w, h) / 2f * 0.95f;
        thumbX = cx;
        thumbY = cy;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(cx, cy, padRadius, padPaint);
        canvas.drawCircle(thumbX, thumbY, padRadius * 0.25f, thumbPaint);
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        final float x = ev.getX();
        final float y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                final float dx = x - cx;
                final float dy = y - cy;
                final float dist = (float) Math.hypot(dx, dy);
                if (dist > padRadius) {
                    // Clamp the thumb to the pad edge instead of letting it drift off.
                    thumbX = cx + dx * padRadius / dist;
                    thumbY = cy + dy * padRadius / dist;
                } else {
                    thumbX = x;
                    thumbY = y;
                }
                invalidate();
                if (listener != null) {
                    // Screen y is down. Compass north = -y; rotate to get (north=0, east=π/2).
                    final double angle = Math.atan2(thumbX - cx, -(thumbY - cy));
                    final double mag = Math.min(1.0, dist / padRadius);
                    listener.onMove(angle, mag);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                thumbX = cx;
                thumbY = cy;
                invalidate();
                if (listener != null) listener.onRelease();
                return true;
            }
            default:
                return super.onTouchEvent(ev);
        }
    }
}
