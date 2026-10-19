package dk.natmad.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** A quiet 8×8 mark, stable for a package even if its label or launcher activity changes. */
public final class AppIdenticon extends Drawable {
    private static final int GRID = 8;
    private final byte[] hash;
    private final Paint paint = new Paint();

    public AppIdenticon(String packageName, int color, int sizePx) {
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(packageName.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by Android", exception);
        }
        paint.setColor(color);
        setBounds(0, 0, sizePx, sizePx);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                // Four hash bits per row, reflected horizontally to make a recognizable figure.
                int bit = y * 4 + Math.min(x, GRID - 1 - x);
                if (((hash[bit / 8] >>> (7 - bit % 8)) & 1) == 0) continue;
                // Integer edges and no antialiasing keep the small pixels crisp.
                canvas.drawRect(bounds.left + x * bounds.width() / GRID,
                    bounds.top + y * bounds.height() / GRID,
                    bounds.left + (x + 1) * bounds.width() / GRID,
                    bounds.top + (y + 1) * bounds.height() / GRID, paint);
            }
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
