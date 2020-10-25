package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import com.mishiranu.dashchan.util.ResourceUtils;

public class CardView extends FrameLayout {
	private static final Implementation IMPLEMENTATION = C.API_LOLLIPOP
			? new CardViewLollipop() : new CardViewJellyBean();

	private final boolean initialized;

	public CardView(Context context) {
		this(context, null);
	}

	public CardView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CardView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		float density = ResourceUtils.obtainDensity(context);
		float size = 1f * density + 0.5f;
		IMPLEMENTATION.initialize(this, context, backgroundColor, size);
		initialized = true;
	}

	private final int[] measureSpecs = new int[2];

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		measureSpecs[0] = widthMeasureSpec;
		measureSpecs[1] = heightMeasureSpec;
		IMPLEMENTATION.measure(this, measureSpecs);
		super.onMeasure(measureSpecs[0], measureSpecs[1]);
	}

	private int backgroundColor;

	private void setBackgroundColorInternal(int color) {
		backgroundColor = color;
		if (initialized) {
			IMPLEMENTATION.setBackgroundColor(this, color);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background) {
		if (background instanceof ColorDrawable) {
			int color = ((ColorDrawable) background).getColor();
			setBackgroundColorInternal(color);
			return;
		}
		super.setBackgroundDrawable(background);
	}

	@Override
	public void setBackgroundColor(int color) {
		setBackgroundColorInternal(color);
	}

	public int getBackgroundColor() {
		return backgroundColor;
	}

	private final static double COS_45 = Math.cos(Math.toRadians(45));
	private final static float SHADOW_MULTIPLIER = 1.5f;

	public static float calculateVerticalPadding(float maxShadowSize, float cornerRadius) {
		return (float) (maxShadowSize * SHADOW_MULTIPLIER + (1 - COS_45) * cornerRadius);
	}

	public static float calculateHorizontalPadding(float maxShadowSize, float cornerRadius) {
		return (float) (maxShadowSize + (1 - COS_45) * cornerRadius);
	}

	private interface Implementation {
		void initialize(CardView cardView, Context context, int backgroundColor, float size);
		void measure(CardView cardView, int[] measureSpecs);
		void setBackgroundColor(CardView cardView, int color);
	}

	private static class CardViewJellyBean implements CardView.Implementation {
		@Override
		public void initialize(CardView cardView, Context context, int backgroundColor, float size) {
			RoundRectDrawableWithShadow background = new RoundRectDrawableWithShadow(context.getResources(),
					backgroundColor, size);
			cardView.setBackgroundDrawable(background);
			Rect shadowPadding = new Rect();
			background.getMaxShadowAndCornerPadding(shadowPadding);
			cardView.setMinimumHeight((int) Math.ceil(background.getMinHeight()));
			cardView.setMinimumWidth((int) Math.ceil(background.getMinWidth()));
			cardView.setPadding(shadowPadding.left, shadowPadding.top, shadowPadding.right, shadowPadding.bottom);
		}

		@Override
		public void measure(CardView cardView, int[] measureSpecs) {
			RoundRectDrawableWithShadow background = (RoundRectDrawableWithShadow) cardView.getBackground();
			int widthMode = CardView.MeasureSpec.getMode(measureSpecs[0]);
			switch (widthMode) {
				case CardView.MeasureSpec.EXACTLY:
				case CardView.MeasureSpec.AT_MOST: {
					int minWidth = (int) Math.ceil(background.getMinWidth());
					measureSpecs[0] = CardView.MeasureSpec.makeMeasureSpec(Math.max(minWidth,
							CardView.MeasureSpec.getSize(measureSpecs[0])), widthMode);
					break;
				}
				case CardView.MeasureSpec.UNSPECIFIED: {
					break;
				}
			}
			int heightMode = CardView.MeasureSpec.getMode(measureSpecs[1]);
			switch (heightMode) {
				case CardView.MeasureSpec.EXACTLY:
				case CardView.MeasureSpec.AT_MOST: {
					int minHeight = (int) Math.ceil(background.getMinHeight());
					measureSpecs[1] = CardView.MeasureSpec.makeMeasureSpec(Math.max(minHeight,
							CardView.MeasureSpec.getSize(measureSpecs[1])), heightMode);
					break;
				}
				case CardView.MeasureSpec.UNSPECIFIED: {
					break;
				}
			}
		}

		@Override
		public void setBackgroundColor(CardView cardView, int color) {
			((RoundRectDrawableWithShadow) cardView.getBackground()).setColor(color);
		}
	}

	private static class CardViewLollipop implements CardView.Implementation {
		@Override
		public void initialize(CardView cardView, Context context, int backgroundColor, float size) {
			RoundRectDrawable backgroundDrawable = new RoundRectDrawable(backgroundColor, size);
			cardView.setBackgroundDrawable(backgroundDrawable);
			cardView.setClipToOutline(true);
			cardView.setElevation(size);
			backgroundDrawable.setPadding(size);
			float elevation = backgroundDrawable.getPadding();
			float radius = backgroundDrawable.getRadius();
			int hPadding = (int) Math.ceil(calculateHorizontalPadding(elevation, radius));
			int vPadding = (int) Math.ceil(calculateVerticalPadding(elevation, radius));
			cardView.setPadding(hPadding, vPadding, hPadding, vPadding);
		}

		@Override
		public void measure(CardView cardView, int[] measureSpecs) {}

		@Override
		public void setBackgroundColor(CardView cardView, int color) {
			((RoundRectDrawable) (cardView.getBackground())).setColor(color);
		}
	}

	private static class RoundRectDrawable extends BaseDrawable {
		private final float radius;
		private final Paint paint;
		private final RectF boundsF;
		private final Rect boundsI;
		private float padding;

		public RoundRectDrawable(int backgroundColor, float radius) {
			this.radius = radius;
			paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
			paint.setColor(backgroundColor);
			boundsF = new RectF();
			boundsI = new Rect();
		}

		public void setPadding(float padding) {
			if (padding == this.padding) {
				return;
			}
			this.padding = padding;
			updateBounds(null);
			invalidateSelf();
		}

		public float getPadding() {
			return padding;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			canvas.drawRoundRect(boundsF, radius, radius, paint);
		}

		private void updateBounds(Rect bounds) {
			if (bounds == null) {
				bounds = getBounds();
			}
			boundsF.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
			boundsI.set(bounds);
			float vInset = calculateVerticalPadding(padding, radius);
			float hInset = calculateHorizontalPadding(padding, radius);
			boundsI.inset((int) Math.ceil(hInset), (int) Math.ceil(vInset));
			boundsF.set(boundsI);
		}

		@Override
		protected void onBoundsChange(Rect bounds) {
			super.onBoundsChange(bounds);
			updateBounds(bounds);
		}

		@Override
		public void getOutline(Outline outline) {
			outline.setRoundRect(boundsI, radius);
		}

		public float getRadius() {
			return radius;
		}

		public void setColor(int color) {
			paint.setColor(color);
			invalidateSelf();
		}
	}

	private static class RoundRectDrawableWithShadow extends BaseDrawable {
		private final int insetShadow;

		private final Paint paint;
		private final Paint cornerShadowPaint;
		private final Paint edgeShadowPaint;

		private final RectF cardBounds;
		private final float cornerRadius;
		private final Path cornerShadowPath = new Path();

		private float rawMaxShadowSize;
		private float shadowSize;
		private float rawShadowSize;

		private boolean dirty = true;
		private boolean printedShadowClipWarning = false;

		public RoundRectDrawableWithShadow(Resources resources, int backgroundColor, float size) {
			float density = ResourceUtils.obtainDensity(resources);
			insetShadow = (int) (1f * density + 0.5f);
			paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
			paint.setColor(backgroundColor);
			cornerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
			cornerShadowPaint.setStyle(Paint.Style.FILL);
			cornerRadius = (int) (size + .5f);
			cardBounds = new RectF();
			edgeShadowPaint = new Paint(cornerShadowPaint);
			edgeShadowPaint.setAntiAlias(false);
			setShadowSize(size, size);
		}

		private int toEven(float value) {
			int i = (int) (value + .5f);
			if (i % 2 == 1) {
				return i - 1;
			}
			return i;
		}

		@Override
		public void setAlpha(int alpha) {
			paint.setAlpha(alpha);
			cornerShadowPaint.setAlpha(alpha);
			edgeShadowPaint.setAlpha(alpha);
		}

		@Override
		protected void onBoundsChange(Rect bounds) {
			super.onBoundsChange(bounds);
			dirty = true;
		}

		public void setShadowSize(float shadowSize, float maxShadowSize) {
			shadowSize = toEven(shadowSize);
			maxShadowSize = toEven(maxShadowSize);
			if (shadowSize > maxShadowSize) {
				shadowSize = maxShadowSize;
				if (!printedShadowClipWarning) {
					printedShadowClipWarning = true;
				}
			}
			if (rawShadowSize == shadowSize && rawMaxShadowSize == maxShadowSize) {
				return;
			}
			rawShadowSize = shadowSize;
			rawMaxShadowSize = maxShadowSize;
			this.shadowSize = (int) (shadowSize * SHADOW_MULTIPLIER + insetShadow + .5f);
			dirty = true;
			invalidateSelf();
		}

		@Override
		public boolean getPadding(Rect padding) {
			int vOffset = (int) Math.ceil(calculateVerticalPadding(rawMaxShadowSize, cornerRadius));
			int hOffset = (int) Math.ceil(calculateHorizontalPadding(rawMaxShadowSize, cornerRadius));
			padding.set(hOffset, vOffset, hOffset, vOffset);
			return true;
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
			paint.setColorFilter(cf);
			cornerShadowPaint.setColorFilter(cf);
			edgeShadowPaint.setColorFilter(cf);
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			if (dirty) {
				buildComponents(getBounds());
				dirty = false;
			}
			canvas.translate(0, rawShadowSize / 2);
			drawShadow(canvas);
			canvas.translate(0, -rawShadowSize / 2);
			canvas.drawRoundRect(cardBounds, cornerRadius, cornerRadius, paint);
		}

		private void drawShadow(Canvas canvas) {
			final float edgeShadowTop = -cornerRadius - shadowSize;
			final float inset = cornerRadius + insetShadow + rawShadowSize / 2;
			final boolean drawHorizontalEdges = cardBounds.width() - 2 * inset > 0;
			final boolean drawVerticalEdges = cardBounds.height() - 2 * inset > 0;
			int saved = canvas.save();
			canvas.translate(cardBounds.left + inset, cardBounds.top + inset);
			canvas.drawPath(cornerShadowPath, cornerShadowPaint);
			if (drawHorizontalEdges) {
				canvas.drawRect(0, edgeShadowTop, cardBounds.width() - 2 * inset, -cornerRadius, edgeShadowPaint);
			}
			canvas.restoreToCount(saved);
			saved = canvas.save();
			canvas.translate(cardBounds.right - inset, cardBounds.bottom - inset);
			canvas.rotate(180f);
			canvas.drawPath(cornerShadowPath, cornerShadowPaint);
			if (drawHorizontalEdges) {
				canvas.drawRect(0, edgeShadowTop, cardBounds.width() - 2 * inset, -cornerRadius + shadowSize,
						edgeShadowPaint);
			}
			canvas.restoreToCount(saved);
			saved = canvas.save();
			canvas.translate(cardBounds.left + inset, cardBounds.bottom - inset);
			canvas.rotate(270f);
			canvas.drawPath(cornerShadowPath, cornerShadowPaint);
			if (drawVerticalEdges) {
				canvas.drawRect(0, edgeShadowTop, cardBounds.height() - 2 * inset, -cornerRadius, edgeShadowPaint);
			}
			canvas.restoreToCount(saved);
			saved = canvas.save();
			canvas.translate(cardBounds.right - inset, cardBounds.top + inset);
			canvas.rotate(90f);
			canvas.drawPath(cornerShadowPath, cornerShadowPaint);
			if (drawVerticalEdges) {
				canvas.drawRect(0, edgeShadowTop, cardBounds.height() - 2 * inset, -cornerRadius, edgeShadowPaint);
			}
			canvas.restoreToCount(saved);
		}

		private void buildShadowCorners() {
			final int shadowStartColor = 0x37000000;
			final int shadowEndColor = 0x03000000;
			RectF innerBounds = new RectF(-cornerRadius, -cornerRadius, cornerRadius, cornerRadius);
			RectF outerBounds = new RectF(innerBounds);
			outerBounds.inset(-shadowSize, -shadowSize);
			cornerShadowPath.reset();
			cornerShadowPath.setFillType(Path.FillType.EVEN_ODD);
			cornerShadowPath.moveTo(-cornerRadius, 0);
			cornerShadowPath.rLineTo(-shadowSize, 0);
			cornerShadowPath.arcTo(outerBounds, 180f, 90f, false);
			cornerShadowPath.arcTo(innerBounds, 270f, -90f, false);
			cornerShadowPath.close();
			float startRatio = cornerRadius / (cornerRadius + shadowSize);
			cornerShadowPaint.setShader(new RadialGradient(0, 0, cornerRadius + shadowSize,
					new int[] {shadowStartColor, shadowStartColor, shadowEndColor},
					new float[] {0f, startRatio, 1f}, Shader.TileMode.CLAMP));
			edgeShadowPaint.setShader(new LinearGradient(0, -cornerRadius + shadowSize, 0,
					-cornerRadius - shadowSize, new int[] {shadowStartColor, shadowStartColor, shadowEndColor},
					new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
			edgeShadowPaint.setAntiAlias(false);
		}

		private void buildComponents(Rect bounds) {
			final float verticalOffset = rawMaxShadowSize * SHADOW_MULTIPLIER;
			cardBounds.set(bounds.left + rawMaxShadowSize, bounds.top + verticalOffset,
					bounds.right - rawMaxShadowSize, bounds.bottom - verticalOffset);
			buildShadowCorners();
		}

		public void getMaxShadowAndCornerPadding(Rect into) {
			getPadding(into);
		}

		public float getMinWidth() {
			final float content = 2 * Math.max(rawMaxShadowSize, cornerRadius + insetShadow + rawMaxShadowSize / 2);
			return content + (rawMaxShadowSize + insetShadow) * 2;
		}

		public float getMinHeight() {
			final float content = 2 * Math.max(rawMaxShadowSize, cornerRadius + insetShadow +
					rawMaxShadowSize * SHADOW_MULTIPLIER / 2);
			return content + (rawMaxShadowSize * SHADOW_MULTIPLIER + insetShadow) * 2;
		}

		public void setColor(int color) {
			paint.setColor(color);
			invalidateSelf();
		}
	}
}
