// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreActivity;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModel;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public abstract class BaseActivity extends AppCompatActivity {
    public static final String TAG = BaseActivity.class.getSimpleName();

    @Nullable
    private AlertDialog mAlertDialog;
    @Nullable
    private SecurityAndOpsViewModel mViewModel;

    private final ActivityResultLauncher<Intent> mKeyStoreActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // Need authentication and/or verify mode of operation
                ensureSecurityAndModeOfOp();
            });
    private final ActivityResultLauncher<Intent> mAuthActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Success
                    handleSecurityAndModeOfOp();
                } else {
                    // Authentication failed
                    finishAndRemoveTask();
                }
            });

    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        getWindow().getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        if (Ops.isAuthenticated()) {
            Log.d(TAG, "Already authenticated.");
            onAuthenticated(savedInstanceState);
            return;
        }
        // Run authentication
        mViewModel = new ViewModelProvider(this).get(SecurityAndOpsViewModel.class);
        mAlertDialog = UIUtils.getProgressDialog(this, getString(R.string.initializing));
        Log.d(TAG, "Waiting to be authenticated.");
        mViewModel.authenticationStatus().observe(this, status -> {
            switch (status) {
                case Ops.STATUS_SUCCESS:
                case Ops.STATUS_FAILED:
                    Log.d(TAG, "Authentication completed.");
                    mViewModel.setAuthenticating(false);
                    if (mAlertDialog != null) mAlertDialog.dismiss();
                    Ops.setAuthenticated(true);
                    onAuthenticated(savedInstanceState);
                    return;
                case Ops.STATUS_DISPLAY_WIRELESS_DEBUGGING:
                    Log.d(TAG, "Request wireless debugging.");
                    mAlertDialog = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mViewModel.autoConnectAdb(Ops.STATUS_DISPLAY_PAIRING);
                        return;
                    } // fall-through
                case Ops.STATUS_DISPLAY_PAIRING:
                    Log.d(TAG, "Display pairing dialog.");
                    mAlertDialog = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.connectWirelessDebugging(this, mViewModel);
                        return;
                    } // fall-through
                case Ops.STATUS_DISPLAY_CONNECT:
                    Log.d(TAG, "Display connect dialog.");
                    mAlertDialog = null;
                    Ops.connectAdbInput(this, mViewModel);
            }
        });
        if (!mViewModel.isAuthenticating()) {
            mViewModel.setAuthenticating(true);
            authenticate();
        }
    }

    protected abstract void onAuthenticated(@Nullable Bundle savedInstanceState);

    protected boolean displaySplashScreen() {
        return true;
    }

    @CallSuper
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @CallSuper
    @Override
    protected void onStart() {
        super.onStart();
        if (mViewModel != null && mViewModel.isAuthenticating() && mAlertDialog != null) {
            mAlertDialog.show();
        }
    }

    @CallSuper
    @Override
    protected void onStop() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        super.onStop();
    }

    private void authenticate() {
        // Check KeyStore
        if (KeyStoreManager.hasKeyStorePassword()) {
            // We already have a working keystore password.
            // Only need authentication and/or verify mode of operation.
            ensureSecurityAndModeOfOp();
            return;
        }
        Intent keyStoreIntent = new Intent(this, KeyStoreActivity.class)
                .putExtra(KeyStoreActivity.EXTRA_KS, true);
        mKeyStoreActivity.launch(keyStoreIntent);
    }

    private void ensureSecurityAndModeOfOp() {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL)) {
            // No security enabled
            handleSecurityAndModeOfOp();
            return;
        }
        Log.d(TAG, "Security enabled.");
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
            // Screen lock enabled
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.unlock_app_manager), null);
            mAuthActivity.launch(intent);
        } else {
            // Screen lock disabled
            UIUtils.displayLongToast(R.string.screen_lock_not_enabled);
            finishAndRemoveTask();
        }
    }

    private void handleSecurityAndModeOfOp() {
        // Authentication was successful
        Log.d(TAG, "Authenticated");
        // Set mode of operation
        Objects.requireNonNull(mViewModel).setModeOfOps();
    }
}
