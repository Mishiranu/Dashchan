/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.graphics.RoundedCornersDrawable;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class AttachmentView extends ClickableView {
	private final Rect source = new Rect();
	private final RectF destination = new RectF();
	private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

	private TransparentTileDrawable tileDrawable;
	private Drawable errorOverlayDrawable;

	private int backgroundColor;
	private boolean cropEnabled = false;
	private boolean fitSquare = false;
	private boolean sfwMode = false;
	private boolean drawTouching = false;
	private RoundedCornersDrawable cornersDrawable;

	private long lastClickTime;

	private final float[] workColorMatrix;
	private final ColorMatrix colorMatrix1;
	private final ColorMatrix colorMatrix2;

	public AttachmentView(Context context, AttributeSet attrs) {
		super(new ContextThemeWrapper(context, R.style.Theme_Gallery), attrs);
		setDrawingCacheEnabled(false);
		// Use old context to obtain background color.
		backgroundColor = ResourceUtils.getColor(context, R.attr.backgroundAttachment);
		if (C.API_LOLLIPOP) {
			workColorMatrix = new float[] {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};
			colorMatrix1 = new ColorMatrix(workColorMatrix);
			colorMatrix2 = new ColorMatrix(workColorMatrix);
		} else {
			workColorMatrix = null;
			colorMatrix1 = null;
			colorMatrix2 = null;
		}
	}

	public void setCropEnabled(boolean enabled) {
		cropEnabled = enabled;
	}

	public void setFitSquare(boolean fitSquare) {
		this.fitSquare = fitSquare;
	}

	public void setSfwMode(boolean sfwMode) {
		this.sfwMode = sfwMode;
	}

	public enum Overlay {NONE, SEVERAL, AUDIO, VIDEO, FILE, WARNING}

	private String key;
	private Bitmap bitmap;
	private boolean error;
	private Overlay overlay;
	private Drawable overlayDrawable;
	private boolean keepOverlayOnError;

	public void resetImage(String key) {
		resetImage(key, Overlay.NONE);
	}

	public void resetImage(String key, Overlay overlay) {
		boolean invalidate = false;
		if (!StringUtils.equals(this.key, key)) {
			this.key = key;
			if (bitmap != null || error) {
				bitmap = null;
				error = false;
				invalidate = true;
			}
		}
		if (overlay != this.overlay) {
			int overlayAttrId = 0;
			boolean keepOverlayOnError = false;
			switch (overlay) {
				case SEVERAL: {
					overlayAttrId = R.attr.attachmentSeveral;
					keepOverlayOnError = true;
					break;
				}
				case AUDIO: {
					overlayAttrId = R.attr.attachmentAudio;
					keepOverlayOnError = true;
					break;
				}
				case VIDEO: {
					overlayAttrId = R.attr.attachmentVideo;
					break;
				}
				case FILE: {
					overlayAttrId = R.attr.attachmentFile;
					keepOverlayOnError = true;
					break;
				}
				case WARNING: {
					overlayAttrId = R.attr.attachmentWarning;
					keepOverlayOnError = true;
					break;
				}
			}
			this.overlay = overlay;
			overlayDrawable = overlayAttrId != 0 ? ResourceUtils.getDrawable(getContext(), overlayAttrId, 0) : null;
			this.keepOverlayOnError = keepOverlayOnError;
			invalidate = true;
		}
		if (invalidate) {
			invalidate();
		}
	}

	public void handleLoadedImage(String key, Bitmap bitmap, boolean error, boolean instantly) {
		if (this.bitmap == null && StringUtils.equals(this.key, key)) {
			this.bitmap = bitmap;
			this.error = bitmap == null && error;
			imageApplyTime = instantly ? 0L : System.currentTimeMillis();
			invalidate();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				if (System.currentTimeMillis() - lastClickTime <= ViewConfiguration.getDoubleTapTimeout()) {
					event.setAction(MotionEvent.ACTION_CANCEL);
					return false;
				}
				break;
			}
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL: {
				lastClickTime = System.currentTimeMillis();
				break;
			}
		}
		return super.onTouchEvent(event);
	}

	@Override
	public void setBackgroundColor(int color) {
		backgroundColor = color;
	}

	public void setDrawTouching(boolean drawTouching) {
		this.drawTouching = drawTouching;
	}

	public void applyRoundedCorners(int backgroundColor) {
		float density = ResourceUtils.obtainDensity(this);
		int radius = (int) (2f * density + 0.5f);
		cornersDrawable = new RoundedCornersDrawable(radius);
		cornersDrawable.setColor(backgroundColor);
		if (getWidth() > 0) {
			updateCornersBounds(getWidth(), getHeight());
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (fitSquare) {
			int width = getMeasuredWidth();
			setMeasuredDimension(width, width);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		updateCornersBounds(right - left, bottom - top);
	}

	private void updateCornersBounds(int width, int height) {
		if (cornersDrawable != null) {
			cornersDrawable.setBounds(0, 0, width, height);
		}
	}

	private long imageApplyTime = 0L;

	@Override
	public void draw(Canvas canvas) {
		Interpolator interpolator = AnimationUtils.DECELERATE_INTERPOLATOR;
		canvas.drawColor(backgroundColor);
		Bitmap bitmap = this.bitmap != null && !this.bitmap.isRecycled() ? this.bitmap : null;
		boolean hasImage = bitmap != null;
		int vw = getWidth(), vh = getHeight();
		int dt = imageApplyTime > 0L ? (int) (System.currentTimeMillis() - imageApplyTime) : Integer.MAX_VALUE;
		float alpha = interpolator.getInterpolation(Math.min(dt / 200f, 1f));
		boolean invalidate = false;
		if (hasImage) {
			int bw = bitmap.getWidth(), bh = bitmap.getHeight();
			float scale, dx, dy;
			if (bw * vh > vw * bh ^ !cropEnabled) {
				scale = (float) vh / (float) bh;
				dx = (int) ((vw - bw * scale) * 0.5f + 0.5f);
				dy = 0f;
			} else {
				scale = (float) vw / (float) bw;
				dx = 0f;
				dy = (int) ((vh - bh * scale) * 0.5f + 0.5f);
			}
			source.set(0, 0, bw, bh);
			destination.set(dx, dy, dx + bw * scale, dy + bh * scale);
			if (bitmap.hasAlpha()) {
				int left = Math.max((int) (destination.left + 0.5f), 0);
				int top = Math.max((int) (destination.top + 0.5f), 0);
				int right = Math.min((int) (destination.right + 0.5f), vw);
				int bottom = Math.min((int) (destination.bottom + 0.5f), vh);
				if (tileDrawable == null) {
					tileDrawable = new TransparentTileDrawable(getContext(), false);
				}
				tileDrawable.setBounds(left, top, right, bottom);
				tileDrawable.draw(canvas);
			}
			bitmapPaint.setAlpha((int) (0xff * alpha));
			if (C.API_LOLLIPOP) {
				float contrast = interpolator.getInterpolation(Math.min(dt / 300f, 1f));
				float saturation = interpolator.getInterpolation(Math.min(dt / 400f, 1f));
				if (saturation < 1f) {
					float[] matrix = workColorMatrix;
					float contrastGain = 1f + 2f * (1f -  contrast);
					float contrastExtra = (1f - contrastGain) * 255f;
					matrix[0] = matrix[6] = matrix[12] = contrastGain;
					matrix[4] = matrix[9] = matrix[14] = contrastExtra;
					colorMatrix2.set(matrix);
					colorMatrix1.setSaturation(saturation);
					colorMatrix1.postConcat(colorMatrix2);
					bitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix1));
				} else {
					bitmapPaint.setColorFilter(null);
				}
				invalidate = saturation < 1f;
			} else {
				invalidate = alpha < 1f;
			}
			canvas.drawBitmap(bitmap, source, destination, bitmapPaint);
		}
		if (sfwMode) {
			canvas.drawColor(backgroundColor & 0x00ffffff | 0xe0000000);
		}
		Drawable overlayDrawable = this.overlayDrawable;
		if (error && !keepOverlayOnError) {
			if (errorOverlayDrawable == null) {
				errorOverlayDrawable = ResourceUtils.getDrawable(getContext(), R.attr.attachmentWarning, 0);
			}
			overlayDrawable = errorOverlayDrawable;
		}
		if (overlayDrawable != null) {
			if (hasImage && !sfwMode) {
				canvas.drawColor((int) (0x66 * alpha) << 24);
			}
			int dw = overlayDrawable.getIntrinsicWidth(), dh = overlayDrawable.getIntrinsicHeight();
			int left = (vw - dw) / 2, top = (vh - dh) / 2, right = left + dw, bottom = top + dh;
			overlayDrawable.setBounds(left, top, right, bottom);
			overlayDrawable.draw(canvas);
		}
		if (drawTouching) {
			super.draw(canvas);
		}
		if (cornersDrawable != null) {
			cornersDrawable.draw(canvas);
		}
		if (invalidate) {
			invalidate();
		}
	}
}