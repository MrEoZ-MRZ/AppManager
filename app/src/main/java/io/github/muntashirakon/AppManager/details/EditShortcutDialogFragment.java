// SPDX-License-Identifier: ISC AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;

// Copyright 2017 Adam M. Szalkowski
public class EditShortcutDialogFragment extends DialogFragment {
    static final String ARG_ACTIVITY_INFO = "activityInfo";
    static final String TAG = "EditShortcutDialogFragment";

    private ActivityInfo mActivityInfo;
    private PackageManager mPackageManager;
    private EditText textName;
    private EditText textIcon;
    private ImageView imageIcon;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final FragmentActivity activity = requireActivity();
        mActivityInfo = requireArguments().getParcelable(ARG_ACTIVITY_INFO);
        mPackageManager = activity.getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_shortcut, null);
        final String activityName = (String) mActivityInfo.loadLabel(mPackageManager);
        textName = view.findViewById(R.id.shortcut_name);
        textName.setText(activityName);
        textIcon = view.findViewById(R.id.insert_icon);
        ComponentName activityComponent = new ComponentName(mActivityInfo.packageName, mActivityInfo.name);
        try {
            String activityIconResourceName = mPackageManager.getResourcesForActivity(activityComponent)
                    .getResourceName(mActivityInfo.getIconResource());
            textIcon.setText(activityIconResourceName);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {}

        textIcon.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                imageIcon.setImageDrawable(getIcon(s.toString()));
            }
        });

        imageIcon = view.findViewById(R.id.insert_icon_btn);
        imageIcon.setImageDrawable(mActivityInfo.loadIcon(mPackageManager));
        imageIcon.setOnClickListener(v -> {
            IconPickerDialogFragment dialog = new IconPickerDialogFragment();
            dialog.attachIconPickerListener(icon -> {
                textIcon.setText(icon.name);
                imageIcon.setImageDrawable(icon.loadIcon(mPackageManager));
            });
            dialog.show(getParentFragmentManager(), IconPickerDialogFragment.TAG);
        });

        return new MaterialAlertDialogBuilder(activity)
                .setTitle(mActivityInfo.loadLabel(mPackageManager))
                .setView(view)
                .setIcon(mActivityInfo.loadIcon(mPackageManager))
                .setPositiveButton(R.string.create_shortcut, (dialog, which) -> {
                    String newActivityName = textName.getText().toString();
                    if (newActivityName.length() == 0) newActivityName = activityName;

                    Drawable icon = null;
                    try {
                        final String iconResourceString = textIcon.getText().toString();
                        final String pack = iconResourceString.substring(0, iconResourceString.indexOf(':'));
                        final String type = iconResourceString.substring(iconResourceString.indexOf(':') + 1, iconResourceString.indexOf('/'));
                        final String name = iconResourceString.substring(iconResourceString.indexOf('/') + 1);

                        Resources resources = mPackageManager.getResourcesForApplication(pack);
                        int iconResource = resources.getIdentifier(name, type, pack);
                        if (iconResource != 0) {
                            icon = ResourcesCompat.getDrawable(resources, iconResource, activity.getTheme());
                        } else {
                            Toast.makeText(activity, R.string.error_invalid_icon_resource, Toast.LENGTH_LONG).show();
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        Toast.makeText(activity, R.string.error_invalid_icon_resource, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), R.string.error_invalid_icon_format, Toast.LENGTH_LONG).show();
                    }
                    if (icon == null) {
                        icon = mPackageManager.getDefaultActivityIcon();
                    }
                    LauncherIconCreator.createLauncherIcon(activity, mActivityInfo, newActivityName, icon);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                }).create();
    }

    private Drawable getIcon(String icon_resource_string) {
        try {
            String pack = icon_resource_string.substring(0, icon_resource_string.indexOf(':'));
            String type = icon_resource_string.substring(icon_resource_string.indexOf(':') + 1, icon_resource_string.indexOf('/'));
            String name = icon_resource_string.substring(icon_resource_string.indexOf('/') + 1);
            Resources res = mPackageManager.getResourcesForApplication(pack);
            return ResourcesCompat.getDrawable(res, res.getIdentifier(name, type, pack),
                    getActivity() == null ? null : getActivity().getTheme());
        } catch (Exception e) {
            return mPackageManager.getDefaultActivityIcon();
        }

    }
}
