package com.mishiranu.dashchan.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class InstanceDialog extends DialogFragment {
	public interface Provider {
		Context getContext();
		FragmentActivity getActivity();
		FragmentManager getFragmentManager();
		Fragment getParentFragment();
		LifecycleOwner getLifecycleOwner();
		<T extends ViewModel> T getViewModel(Class<T> modelClass);
		Dialog createDismissDialog();
		void dismiss();
	}

	public interface Factory {
		Dialog createDialog(Provider provider);
	}

	public static class InstanceViewModel extends ViewModel {
		private Factory factory;

		@Override
		protected void onCleared() {
			factory = null;
		}
	}

	private Factory initFactory;

	public InstanceDialog() {}

	public InstanceDialog(FragmentManager fragmentManager, String tag, Factory factory) {
		if (!fragmentManager.isStateSaved()) {
			this.initFactory = factory;
			if (tag != null) {
				InstanceDialog oldDialog = (InstanceDialog) fragmentManager.findFragmentByTag(tag);
				if (oldDialog != null) {
					oldDialog.dismiss();
				}
			}
			show(fragmentManager, tag);
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		InstanceViewModel viewModel = new ViewModelProvider(this).get(InstanceViewModel.class);
		if (initFactory != null) {
			viewModel.factory = initFactory;
			initFactory = null;
		}
		return viewModel.factory != null ? viewModel.factory.createDialog(provider)
				: new DismissDialog(requireContext());
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (getDialog() instanceof DismissDialog) {
			dismiss();
		}
	}

	private static class DismissDialog extends Dialog {
		public DismissDialog(@NonNull Context context) {
			super(context);
		}

		@Override
		public void show() {}
	}

	private final Provider provider = new Provider() {
		@Override
		public Context getContext() {
			return requireContext();
		}

		@Override
		public FragmentActivity getActivity() {
			return requireActivity();
		}

		@Override
		public FragmentManager getFragmentManager() {
			return getParentFragmentManager();
		}

		@Override
		public Fragment getParentFragment() {
			return InstanceDialog.this.getParentFragment();
		}

		@Override
		public LifecycleOwner getLifecycleOwner() {
			return InstanceDialog.this;
		}

		@Override
		public <T extends ViewModel> T getViewModel(Class<T> modelClass) {
			return new ViewModelProvider(InstanceDialog.this).get(modelClass);
		}

		@Override
		public Dialog createDismissDialog() {
			return new DismissDialog(requireContext());
		}

		@Override
		public void dismiss() {
			InstanceDialog.this.dismiss();
		}
	};
}
