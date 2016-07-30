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

package com.mishiranu.dashchan.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.app.MainApplication;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.IOUtils;

public class WebViewBitmapDecoder extends WebViewClient
{
	private static final int MESSAGE_INIT_WEB_VIEW = 1;
	private static final int MESSAGE_MEASURE_PICTURE = 2;
	private static final int MESSAGE_CHECK_PICTURE_SIZE = 3;
	
	private static final Handler HANDLER = new Handler(Looper.getMainLooper(), new Callback());
	
	private final FileHolder mFileHolder;
	private final int mSampleSize;
	private final CountDownLatch mLatch = new CountDownLatch(1);
	
	private volatile Bitmap mBitmap;
	
	private WebView mWebView;
	
	private WebViewBitmapDecoder(FileHolder fileHolder, BitmapFactory.Options options) throws IOException
	{
		mFileHolder = fileHolder;
		mSampleSize = options != null ? Math.max(options.inSampleSize, 1) : 1;
		if (!fileHolder.isImage()) throw new IOException();
		HANDLER.obtainMessage(MESSAGE_INIT_WEB_VIEW, this).sendToTarget();
		try
		{
			mLatch.await();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new InterruptedIOException();
		}
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, String url)
	{
		if (url.startsWith("http://") || url.startsWith("https://"))
		{
			if (url.endsWith("//127.0.0.1/image.jpeg"))
			{
				InputStream inputStream = null;
				try
				{
					inputStream = mFileHolder.openInputStream();
					return new WebResourceResponse(mFileHolder.getImageType() == FileHolder.ImageType.IMAGE_SVG
							? "image/svg+xml" : "image/jpeg", null, inputStream);
				}
				catch (IOException e)
				{
					IOUtils.close(inputStream);
				}
			}
			return new WebResourceResponse("text/html", "UTF-8", null);
		}
		else return null;
	}

	private boolean mPageFinished = false;
	
	@Override
	public void onPageFinished(WebView view, String url)
	{
		super.onPageFinished(view, url);
		mPageFinished = true;
		notifyExtract(view);
	}
	
	@SuppressWarnings("deprecation")
	private final WebView.PictureListener mPictureListener = (view, picture) ->
	{
		if (mPageFinished) notifyExtract(view);
	};
	
	private void notifyExtract(WebView view)
	{
		HANDLER.removeMessages(MESSAGE_MEASURE_PICTURE);
		HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_MEASURE_PICTURE, new Object[] {this, view}), 1000);
	}
	
	private boolean mMeasured = false;
	
	private void measurePicture(WebView view)
	{
		if (!mMeasured)
		{
			mMeasured = true;
			mWebView = view;
			view.loadUrl("javascript:calculateSize();");
		}
	}
	
	private void countDownAndDestroy(WebView view)
	{
		mLatch.countDown();
		view.destroy();
	}
	
	private void checkPictureSize(int width, int height)
	{
		if (width > 0 && height > 0)
		{
			if (mWebView.getWidth() <= 0 || mWebView.getHeight() <= 0)
			{
				mMeasured = false;
				mWebView.layout(0, 0, width, height);
				mWebView.reload();
			}
			else
			{
				if (mWebView.getWidth() != width || mWebView.getHeight() != height) countDownAndDestroy(mWebView); else
				{
					try
					{
						mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
						mWebView.draw(new Canvas(mBitmap));
					}
					catch (OutOfMemoryError e)
					{
						mBitmap = null;
					}
					finally
					{
						countDownAndDestroy(mWebView);
					}
				}
			}
		}
		else countDownAndDestroy(mWebView);
		mWebView = null;
	}
	
	private static class Callback implements Handler.Callback
	{
		@SuppressWarnings("deprecation")
		@SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
		@Override
		public boolean handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case MESSAGE_INIT_WEB_VIEW:
				{
					WebViewBitmapDecoder decoder = (WebViewBitmapDecoder) msg.obj;
					int width = decoder.mFileHolder.getImageWidth();
					int height = decoder.mFileHolder.getImageHeight();
					int rotation = decoder.mFileHolder.getRotation();
					if (rotation == 90 || rotation == 270) width = height ^ width ^ (height = width); // Swap
					width /= decoder.mSampleSize;
					height /= decoder.mSampleSize;
					WebView webView = new WebView(MainApplication.getInstance());
					WebSettings settings = webView.getSettings();
					settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
					settings.setAppCacheEnabled(false);
					settings.setJavaScriptEnabled(true);
					webView.setInitialScale(100 / decoder.mSampleSize);
					webView.setWebViewClient(decoder);
					webView.setBackgroundColor(Color.TRANSPARENT);
					webView.setPictureListener(decoder.mPictureListener);
					webView.addJavascriptInterface(decoder, "jsi");
					if (width > 0 && height > 0) webView.layout(0, 0, width, height);
					webView.loadData("<!DOCTYPE html><html><head><script type=\"text/javascript\">"
							+ "function calculateSize() {jsi.onCalculateSize(document.body.children[0].naturalWidth, "
							+ "document.body.children[0].naturalHeight);}</script></head>"
							+ "<body style=\"margin: 0\"><img src=\"http://127.0.0.1/image.jpeg\" /></body></html>",
							"text/html", "UTF-8");
					return true;
				}
				case MESSAGE_MEASURE_PICTURE:
				{
					Object[] data = (Object[]) msg.obj;
					WebViewBitmapDecoder decoder = (WebViewBitmapDecoder) data[0];
					WebView webView = (WebView) data[1];
					decoder.measurePicture(webView);
					return true;
				}
				case MESSAGE_CHECK_PICTURE_SIZE:
				{
					Object[] data = (Object[]) msg.obj;
					WebViewBitmapDecoder decoder = (WebViewBitmapDecoder) data[0];
					int width = (int) data[1];
					int height = (int) data[2];
					decoder.checkPictureSize(width, height);
					return true;
				}
			}
			return false;
		}
	}
	
	@SuppressWarnings("unused")
	@JavascriptInterface
	public void onCalculateSize(int width, int height)
	{
		width /= mSampleSize;
		height /= mSampleSize;
		HANDLER.obtainMessage(MESSAGE_CHECK_PICTURE_SIZE, new Object[] {this, width, height}).sendToTarget();
	}
	
	public static Bitmap loadBitmap(FileHolder fileHolder, BitmapFactory.Options options)
	{
		if (C.WEB_VIEW_BITMAP_DECODER_SUPPORTED && !MainApplication.getInstance().isLowRam())
		{
			WebViewBitmapDecoder decoder;
			try
			{
				decoder = new WebViewBitmapDecoder(fileHolder, options);
			}
			catch (IOException e)
			{
				return null;
			}
			return decoder.mBitmap;
		}
		return null;
	}
}