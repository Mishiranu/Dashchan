package com.mishiranu.dashchan.util;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import java.lang.reflect.Field;

public class DrawerToggle implements DrawerLayout.DrawerListener {
	private final Activity activity;
	private final DrawerLayout drawerLayout;

	private final ArrowDrawable arrowDrawable;
	private final SlideDrawable slideDrawable;
	private Drawable homeAsUpIndicator;

	public enum Mode {DISABLED, DRAWER, UP}

	private Mode mode = Mode.DISABLED;

	public DrawerToggle(Activity activity, Context toolbarContext, DrawerLayout drawerLayout) {
		this.activity = activity;
		this.drawerLayout = drawerLayout;
		Context context = toolbarContext != null ? toolbarContext : activity;
		if (C.API_LOLLIPOP) {
			arrowDrawable = new ArrowDrawable(context);
			slideDrawable = null;
		} else {
			arrowDrawable = null;
			homeAsUpIndicator = getThemeUpIndicatorObsolete();
			slideDrawable = new SlideDrawable(context);
		}
	}

	private static final int DRAWER_CLOSE_DURATION;

	static {
		int duration;
		try {
			Field baseSettleDurationField = ViewDragHelper.class.getDeclaredField("BASE_SETTLE_DURATION");
			baseSettleDurationField.setAccessible(true);
			duration = (int) baseSettleDurationField.get(null);
		} catch (Exception e) {
			// Library method, fix if needed
			throw new RuntimeException(e);
		}
		DRAWER_CLOSE_DURATION = duration;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void setDrawerIndicatorMode(Mode mode) {
		if (this.mode != mode) {
			this.mode = mode;
			ActionBar actionBar = activity.getActionBar();
			if (mode == Mode.DISABLED) {
				if (C.API_JELLY_BEAN_MR2) {
					actionBar.setHomeAsUpIndicator(null);
				}
				actionBar.setDisplayHomeAsUpEnabled(false);
			} else {
				actionBar.setDisplayHomeAsUpEnabled(true);
				if (C.API_LOLLIPOP) {
					actionBar.setHomeAsUpIndicator(arrowDrawable);
					boolean open = drawerLayout.isDrawerOpen(GravityCompat.START) && arrowDrawable.position == 1f;
					if (!open) {
						ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
						animator.setDuration(DRAWER_CLOSE_DURATION);
						animator.addUpdateListener(new StateArrowAnimatorListener(mode == Mode.DRAWER));
						animator.start();
					}
				} else {
					setActionBarUpIndicatorObsolete(mode == Mode.DRAWER ? slideDrawable : homeAsUpIndicator);
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void syncState() {
		if (mode != Mode.DISABLED) {
			if (C.API_LOLLIPOP) {
				arrowDrawable.setPosition(mode == Mode.UP || drawerLayout.isDrawerOpen(GravityCompat.START) ? 1f : 0f);
				activity.getActionBar().setHomeAsUpIndicator(arrowDrawable);
			} else {
				slideDrawable.setPosition(drawerLayout.isDrawerOpen(GravityCompat.START) ? 1f : 0f);
				setActionBarUpIndicatorObsolete(mode == Mode.DRAWER ? slideDrawable : homeAsUpIndicator);
			}
		}
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		if (item != null && item.getItemId() == android.R.id.home) {
			if (drawerLayout.getDrawerLockMode(GravityCompat.START) != DrawerLayout.LOCK_MODE_UNLOCKED) {
				return false;
			}
			if (mode == Mode.DRAWER) {
				if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
					drawerLayout.closeDrawer(GravityCompat.START);
				} else {
					drawerLayout.openDrawer(GravityCompat.START);
				}
				return true;
			} else if (mode == Mode.UP) {
				if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
					drawerLayout.closeDrawer(GravityCompat.START);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
		if (C.API_LOLLIPOP) {
			if (mode == Mode.DRAWER) {
				arrowDrawable.setPosition(slideOffset);
			}
		} else {
			float glyphOffset = slideDrawable.getPosition();
			if (slideOffset > 0.5f) {
				glyphOffset = Math.max(glyphOffset, Math.max(0.f, slideOffset - 0.5f) * 2);
			} else {
				glyphOffset = Math.min(glyphOffset, slideOffset * 2);
			}
			slideDrawable.setPosition(glyphOffset);
		}
	}

	@Override
	public void onDrawerOpened(@NonNull View drawerView) {
		if (C.API_LOLLIPOP) {
			if (mode == Mode.DRAWER) {
				arrowDrawable.setPosition(1f);
			}
		} else {
			slideDrawable.setPosition(1);
		}
	}

	@Override
	public void onDrawerClosed(@NonNull View drawerView) {
		if (C.API_LOLLIPOP) {
			if (mode == Mode.DRAWER) {
				arrowDrawable.setPosition(0f);
			}
		} else {
			slideDrawable.setPosition(0);
		}
	}

	@Override
	public void onDrawerStateChanged(int newState) {}

	private static final float ARROW_HEAD_ANGLE = (float) Math.toRadians(45);

	private class ArrowDrawable extends BaseDrawable {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();

		private final float barThickness;
		private final float topBottomArrowSize;
		private final float barSize;
		private final float middleArrowSize;
		private final float barGap;
		private final int size;

		private boolean verticalMirror = false;
		private float position;

		public ArrowDrawable(Context context) {
			paint.setAntiAlias(true);
			paint.setColor(ResourceUtils.getColor(context, android.R.attr.textColorPrimary));
			float density = ResourceUtils.obtainDensity(context);
			size = (int) (24f * density);
			barSize = 16f * density;
			topBottomArrowSize = 9.5f * density;
			barThickness = 2f * density;
			barGap = 3f * density;
			middleArrowSize = 13.6f * density;
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeJoin(Paint.Join.ROUND);
			paint.setStrokeCap(Paint.Cap.SQUARE);
			paint.setStrokeWidth(barThickness);
		}

		public void setPosition(float position) {
			position = Math.min(1f, Math.max(0f, position));
			if (position == 1f) {
				verticalMirror = true;
			} else if (position == 0f) {
				verticalMirror = false;
			}
			this.position = position;
			invalidateSelf();
		}

		@Override
		public int getIntrinsicWidth() {
			return size;
		}

		@Override
		public int getIntrinsicHeight() {
			return size;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			Rect bounds = getBounds();
			boolean rtl = isLayoutRtl();
			float position = this.position;
			float arrowSize = AnimationUtils.lerp(barSize, topBottomArrowSize, position);
			float middleBarSize = AnimationUtils.lerp(barSize, middleArrowSize, position);
			float middleBarCut = AnimationUtils.lerp(0f, barThickness / 2f, position);
			float rotation = AnimationUtils.lerp(0f, ARROW_HEAD_ANGLE, position);
			float canvasRotate = AnimationUtils.lerp(rtl ? 0f : -180f, rtl ? 180f : 0f, position);
			float topBottomBarOffset = AnimationUtils.lerp(barGap + barThickness, 0f, position);
			path.rewind();
			float arrowEdge = -middleBarSize / 2f + 0.5f;
			path.moveTo(arrowEdge + middleBarCut, 0f);
			path.rLineTo(middleBarSize - middleBarCut, 0f);
			float arrowWidth = Math.round(arrowSize * Math.cos(rotation));
			float arrowHeight = Math.round(arrowSize * Math.sin(rotation));
			path.moveTo(arrowEdge, topBottomBarOffset);
			path.rLineTo(arrowWidth, arrowHeight);
			path.moveTo(arrowEdge, -topBottomBarOffset);
			path.rLineTo(arrowWidth, -arrowHeight);
			path.moveTo(0f, 0f);
			path.close();
			canvas.save();
			canvas.rotate(canvasRotate * ((verticalMirror ^ rtl) ? -1f : 1f), bounds.centerX(), bounds.centerY());
			canvas.translate(bounds.centerX(), bounds.centerY());
			canvas.drawPath(path, paint);
			canvas.restore();
		}
	}

	private class StateArrowAnimatorListener implements ValueAnimator.AnimatorUpdateListener {
		private final boolean enable;

		public StateArrowAnimatorListener(boolean enable) {
			this.enable = enable;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			float value = (float) animation.getAnimatedValue();
			arrowDrawable.setPosition(enable ? 1f - value : value);
		}
	}

	private class SlideDrawable extends BaseDrawable {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final int size;

		private float position;

		private SlideDrawable(Context context) {
			paint.setColor(0xff979797);
			float density = ResourceUtils.obtainDensity(context);
			size = (int) (16f * density);
		}

		public void setPosition(float position) {
			this.position = position;
			invalidateSelf();
		}

		public float getPosition() {
			return position;
		}

		@Override
		public int getIntrinsicWidth() {
			return size;
		}

		@Override
		public int getIntrinsicHeight() {
			return size;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			Rect bounds = getBounds();
			canvas.save();
			canvas.translate(bounds.left, bounds.top);
			boolean rtl = isLayoutRtl();
			if (rtl) {
				canvas.translate(bounds.width(), 0);
				canvas.scale(-1, 1);
			}
			canvas.scale(bounds.width() / 48f, bounds.height() / 48f);
			canvas.translate(-16f * position, 0);
			canvas.drawRect(0, 4, 30, 12, paint);
			canvas.drawRect(0, 22, 30, 30, paint);
			canvas.drawRect(0, 40, 30, 48, paint);
			canvas.restore();
		}
	}

	private static final int[] THEME_ATTRS = new int[] {android.R.attr.homeAsUpIndicator};

	private Drawable getThemeUpIndicatorObsolete() {
		if (C.API_JELLY_BEAN_MR2) {
			TypedArray a = activity.getActionBar().getThemedContext().obtainStyledAttributes(null,
					THEME_ATTRS, android.R.attr.actionBarStyle, 0);
			Drawable result = a.getDrawable(0);
			a.recycle();
			return result;
		} else {
			TypedArray a = activity.obtainStyledAttributes(THEME_ATTRS);
			Drawable result = a.getDrawable(0);
			a.recycle();
			return result;
		}
	}

	private ImageView upIndicatorView;

	private void setActionBarUpIndicatorObsolete(Drawable upDrawable) {
		if (C.API_JELLY_BEAN_MR2) {
			activity.getActionBar().setHomeAsUpIndicator(upDrawable);
		} else {
			if (upIndicatorView == null) {
				View home = activity.findViewById(android.R.id.home);
				if (home == null) {
					return;
				}
				ViewGroup parent = (ViewGroup) home.getParent();
				int childCount = parent.getChildCount();
				if (childCount != 2) {
					return;
				}
				View first = parent.getChildAt(0);
				View second = parent.getChildAt(1);
				View up = first.getId() == android.R.id.home ? second : first;
				if (up instanceof ImageView) {
					upIndicatorView = (ImageView) up;
				}
			}
			if (upIndicatorView != null) {
				upIndicatorView.setImageDrawable(upDrawable);
			}
		}
	}

	private boolean isLayoutRtl() {
		return C.API_JELLY_BEAN_MR1 && activity.getWindow()
				.getDecorView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
	}
}
