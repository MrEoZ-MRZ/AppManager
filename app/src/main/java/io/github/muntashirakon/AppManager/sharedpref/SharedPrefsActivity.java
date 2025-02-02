// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;

public class SharedPrefsActivity extends BaseActivity implements
        SearchView.OnQueryTextListener, EditPrefItemFragment.InterfaceCommunicator {
    public static final String EXTRA_PREF_LOCATION = "loc";
    public static final String EXTRA_PREF_LABEL = "label";  // Optional

    public static final int REASONABLE_STR_SIZE = 200;

    private SharedPrefsListingAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private SharedPrefsViewModel mViewModel;
    private boolean writeAndExit = false;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_shared_prefs);
        setSupportActionBar(findViewById(R.id.toolbar));
        String sharedPrefFile = getIntent().getStringExtra(EXTRA_PREF_LOCATION);
        String appLabel = getIntent().getStringExtra(EXTRA_PREF_LABEL);
        if (sharedPrefFile == null) {
            finish();
            return;
        }
        mViewModel = new ViewModelProvider(this).get(SharedPrefsViewModel.class);
        mViewModel.setSharedPrefsFile(new Path(getApplicationContext(), new ProxyFile(sharedPrefFile)));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(appLabel);
            actionBar.setSubtitle(mViewModel.getSharedPrefFilename());
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(actionBar, this);
        }
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        ListView listView = findViewById(android.R.id.list);
        listView.setTextFilterEnabled(true);
        listView.setDividerHeight(0);
        listView.setEmptyView(findViewById(android.R.id.empty));
        mAdapter = new SharedPrefsListingAdapter(this);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            EditPrefItemFragment.PrefItem prefItem = new EditPrefItemFragment.PrefItem();
            prefItem.keyName = mAdapter.getItem(position);
            prefItem.keyValue = mViewModel.getValue(prefItem.keyName);
            EditPrefItemFragment dialogFragment = new EditPrefItemFragment();
            Bundle args = new Bundle();
            args.putParcelable(EditPrefItemFragment.ARG_PREF_ITEM, prefItem);
            args.putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_EDIT);
            dialogFragment.setArguments(args);
            dialogFragment.show(getSupportFragmentManager(), EditPrefItemFragment.TAG);
        });
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(v -> {
            DialogFragment dialogFragment = new EditPrefItemFragment();
            Bundle args = new Bundle();
            args.putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_CREATE);
            dialogFragment.setArguments(args);
            dialogFragment.show(getSupportFragmentManager(), EditPrefItemFragment.TAG);
        });
        mViewModel.getSharedPrefsMapLiveData().observe(this, sharedPrefsMap -> {
            mProgressIndicator.hide();
            mAdapter.setDefaultList(sharedPrefsMap);
        });
        mViewModel.getSharedPrefsSavedLiveData().observe(this, saved -> {
            if (saved) {
                UIUtils.displayShortToast(R.string.saved_successfully);
                if (writeAndExit) {
                    finish();
                    writeAndExit = false;
                }
            } else {
                UIUtils.displayShortToast(R.string.saving_failed);
            }
        });
        mViewModel.getSharedPrefsDeletedLiveData().observe(this, deleted -> {
            if (deleted) {
                UIUtils.displayShortToast(R.string.deleted_successfully);
                finish();
            } else {
                UIUtils.displayShortToast(R.string.deletion_failed);
            }
        });
        mViewModel.getSharedPrefsModifiedLiveData().observe(this, modified -> {
            if (modified) {
                if (actionBar != null) {
                    actionBar.setTitle("* " + mViewModel.getSharedPrefFilename());
                }
            } else {
                if (actionBar != null) {
                    actionBar.setTitle(mViewModel.getSharedPrefFilename());
                }
            }
        });
        mViewModel.loadSharedPrefs();
    }

    @Override
    public void onBackPressed() {
        if (mViewModel.isModified()) {
            displayExitPrompt();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_shared_prefs_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void sendInfo(@EditPrefItemFragment.Mode int mode, EditPrefItemFragment.PrefItem prefItem) {
        if (prefItem != null) {
            switch (mode) {
                case EditPrefItemFragment.MODE_CREATE:
                case EditPrefItemFragment.MODE_EDIT:
                    mViewModel.add(prefItem.keyName, prefItem.keyValue);
                    break;
                case EditPrefItemFragment.MODE_DELETE:
                    mViewModel.remove(prefItem.keyName);
                    break;
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (mViewModel.isModified()) {
                displayExitPrompt();
            } else finish();
        } else if (id == R.id.action_discard) {
            finish();
        } else if (id == R.id.action_delete) {
            mViewModel.deleteSharedPrefFile();
        } else if (id == R.id.action_save) {
            mViewModel.writeSharedPrefs();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null && !TextUtils.isEmpty(mAdapter.mConstraint)) {
            mAdapter.getFilter().filter(mAdapter.mConstraint);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mAdapter != null) mAdapter.getFilter().filter(newText.toLowerCase(Locale.ROOT));
        return true;
    }

    private void displayExitPrompt() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exit_confirmation)
                .setMessage(R.string.file_modified_are_you_sure)
                .setCancelable(false)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> finish())
                .setNeutralButton(R.string.save_and_exit, (dialog, which) -> {
                    writeAndExit = true;
                    mViewModel.writeSharedPrefs();
                })
                .show();
    }

    static class SharedPrefsListingAdapter extends BaseAdapter implements Filterable {
        private final LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private String[] mDefaultList;
        private String[] mAdapterList;
        private Map<String, Object> mAdapterMap;

        private final int mColorTransparent;
        private final int mColorSemiTransparent;
        private final int mColorRed;

        static class ViewHolder {
            TextView itemName;
            TextView itemValue;
        }

        SharedPrefsListingAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(@NonNull Map<String, Object> list) {
            mDefaultList = list.keySet().toArray(new String[0]);
            mAdapterList = mDefaultList;
            mAdapterMap = list;
            if (!TextUtils.isEmpty(mConstraint)) {
                getFilter().filter(mConstraint);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @Override
        public String getItem(int position) {
            return mAdapterList[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_shared_pref, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.itemName = convertView.findViewById(R.id.item_title);
                viewHolder.itemValue = convertView.findViewById(R.id.item_subtitle);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            String prefName = mAdapterList[position];
            if (mConstraint != null && prefName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                viewHolder.itemName.setText(UIUtils.getHighlightedText(prefName, mConstraint, mColorRed));
            } else {
                viewHolder.itemName.setText(prefName);
            }
            Object value = mAdapterMap.get(prefName);
            String strValue = (value != null) ? value.toString() : "";
            viewHolder.itemValue.setText(strValue.length() > REASONABLE_STR_SIZE ?
                    strValue.substring(0, REASONABLE_STR_SIZE) : strValue);
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.length);
                        for (String item : mDefaultList) {
                            if (item.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list.toArray(new String[0]);
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            mAdapterList = (String[]) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
    }
}