package com.seafile.seadroid2.ui.star;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.chad.library.adapter4.BaseQuickAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.SupportAccountManager;
import com.seafile.seadroid2.bus.BusHelper;
import com.seafile.seadroid2.config.Constants;
import com.seafile.seadroid2.databinding.LayoutFrameSwipeRvBinding;
import com.seafile.seadroid2.enums.FileReturnActionEnum;
import com.seafile.seadroid2.framework.datastore.DataManager;
import com.seafile.seadroid2.framework.db.entities.RepoModel;
import com.seafile.seadroid2.framework.db.entities.StarredModel;
import com.seafile.seadroid2.framework.model.ResultModel;
import com.seafile.seadroid2.framework.util.Toasts;
import com.seafile.seadroid2.framework.util.Utils;
import com.seafile.seadroid2.ui.WidgetUtils;
import com.seafile.seadroid2.ui.base.fragment.BaseFragmentWithVM;
import com.seafile.seadroid2.ui.bottomsheetmenu.BottomSheetHelper;
import com.seafile.seadroid2.ui.bottomsheetmenu.BottomSheetMenuFragment;
import com.seafile.seadroid2.ui.bottomsheetmenu.OnMenuClickListener;
import com.seafile.seadroid2.ui.dialog_fragment.BottomSheetPasswordDialogFragment;
import com.seafile.seadroid2.ui.dialog_fragment.listener.OnResultListener;
import com.seafile.seadroid2.ui.file.FileActivity;
import com.seafile.seadroid2.ui.main.MainActivity;
import com.seafile.seadroid2.ui.main.MainViewModel;
import com.seafile.seadroid2.ui.markdown.MarkdownActivity;
import com.seafile.seadroid2.ui.media.image.CarouselImagePreviewActivity;
import com.seafile.seadroid2.ui.media.player.CustomExoVideoPlayerActivity;
import com.seafile.seadroid2.ui.sdoc.SDocWebViewActivity;
import com.seafile.seadroid2.view.TipsViews;

import java.io.File;
import java.util.List;

import io.reactivex.functions.Consumer;
import kotlin.Pair;

public class StarredQuickFragment extends BaseFragmentWithVM<StarredViewModel> {
    private MainViewModel mainViewModel;
    private LayoutFrameSwipeRvBinding binding;
    private StarredAdapter adapter;

    public static StarredQuickFragment newInstance() {
        Bundle args = new Bundle();
        StarredQuickFragment fragment = new StarredQuickFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutFrameSwipeRvBinding.inflate(inflater, container, false);
        binding.swipeRefreshLayout.setOnRefreshListener(this::reload);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        registerResultLauncher();

        initAdapter();

        initViewModel();
    }

    @Override
    public void onFirstResume() {
        super.onFirstResume();
        reload();
    }

    private boolean isForce = false;

    @Override
    public void onOtherResume() {
        super.onOtherResume();

        if (isForce) {
            reload();
            isForce = false;
        }
    }

    private void initAdapter() {
        adapter = new StarredAdapter();
        TextView tipView = TipsViews.getTipTextView(requireContext());
        tipView.setText(R.string.no_starred_file);
        tipView.setOnClickListener(v -> reload());
        adapter.setStateView(tipView);
        adapter.setStateViewEnable(false);

        adapter.setOnItemClickListener((baseQuickAdapter, view, i) -> {

            StarredModel starredModel = adapter.getItems().get(i);
            navTo(starredModel);

        });

        adapter.addOnItemChildClickListener(R.id.expandable_toggle_button, new BaseQuickAdapter.OnItemChildClickListener<StarredModel>() {
            @Override
            public void onItemClick(@NonNull BaseQuickAdapter<StarredModel, ?> baseQuickAdapter, @NonNull View view, int i) {
                showBottomSheet(adapter.getItems().get(i));
            }
        });

        binding.rv.setAdapter(createAdapterHelper(adapter).getAdapter());
    }

    private void showErrorTip(SeafException seafException) {
        adapter.submitList(null);
        TextView tipView = TipsViews.getTipTextView(requireContext());
        tipView.setText(R.string.error_when_load_starred);
        tipView.setOnClickListener(v -> reload());
        adapter.setStateView(tipView);
        adapter.setStateViewEnable(true);
    }


    private void initViewModel() {
        getViewModel().getRefreshLiveData().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                binding.swipeRefreshLayout.setRefreshing(aBoolean);
            }
        });

        getViewModel().getSecondRefreshLiveData().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                showLoadingDialog(aBoolean);
            }
        });

        getViewModel().getSeafExceptionLiveData().observe(getViewLifecycleOwner(), new Observer<SeafException>() {
            @Override
            public void onChanged(SeafException seafException) {
                showErrorTip(seafException);
            }
        });

        getViewModel().getListLiveData().observe(getViewLifecycleOwner(), new Observer<List<StarredModel>>() {
            @Override
            public void onChanged(List<StarredModel> starredModels) {
                adapter.setStateViewEnable(true);

                adapter.notifyDataChanged(starredModels);
            }
        });

        getViewModel().getUnStarredResultLiveData().observe(getViewLifecycleOwner(), new Observer<Pair<String, ResultModel>>() {
            @Override
            public void onChanged(Pair<String, ResultModel> pair) {
                if (pair.getSecond().success) {
                    Toasts.show(R.string.success);

                    mainViewModel.getOnForceRefreshRepoListLiveData().setValue(true);

                    reload();
                }
            }
        });

        BusHelper.getCustomBundleObserver().observe(getViewLifecycleOwner(), new Observer<Bundle>() {
            @Override
            public void onChanged(Bundle bundle) {
                if (bundle == null) {
                    return;
                }

                isForce = bundle.containsKey(StarredQuickFragment.class.getSimpleName());
            }
        });
    }

    private void reload() {
        adapter.setStateViewEnable(false);
        getViewModel().loadData();
    }

    private void showBottomSheet(StarredModel model) {
        BottomSheetMenuFragment.Builder builder = BottomSheetHelper.buildSheet(requireActivity(), R.menu.bottom_sheet_unstarred, new OnMenuClickListener() {
            @Override
            public void onMenuClick(MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.nav_to) {
                    MainActivity.navToThis(requireContext(), model.repo_id, model.repo_name, model.path, model.is_dir);
                } else if (menuItem.getItemId() == R.id.unstar) {
                    getViewModel().unStarItem(model.repo_id, model.path);
                }
            }
        });

        if (model.deleted) {
            builder.removeMenu(R.id.nav_to);
        }

        builder.show(getChildFragmentManager(), StarredQuickFragment.class.getSimpleName());
    }

    private void navTo(StarredModel starredModel) {
        if (!starredModel.deleted) {
            decryptRepo(starredModel);
        } else if (starredModel.isRepo()) {
            Toasts.show(getString(R.string.library_not_found));
        } else if (starredModel.is_dir) {
            Toasts.show(getString(R.string.op_exception_folder_deleted, starredModel.obj_name));
        } else {
            Toasts.show(getString(R.string.file_not_found, starredModel.obj_name));
        }
    }

    private void showPasswordDialogCallback(String repo_id, String repo_name, OnResultListener<RepoModel> resultListener) {
        BottomSheetPasswordDialogFragment dialogFragment = BottomSheetPasswordDialogFragment.newInstance(repo_id, repo_name);
        dialogFragment.setResultListener(resultListener);
        dialogFragment.show(getChildFragmentManager(), BottomSheetPasswordDialogFragment.class.getSimpleName());
    }

    private void decryptRepo(StarredModel model) {
        if (model.repo_encrypted) {
            getViewModel().decryptRepo(model.repo_id, new Consumer<String>() {
                @Override
                public void accept(String i) throws Exception {
                    if (TextUtils.equals(i, "need-to-re-enter-password")) {
                        showPasswordDialogCallback(model.repo_id, model.repo_name, new OnResultListener<RepoModel>() {
                            @Override
                            public void onResultData(RepoModel repoModel) {
                                if (repoModel != null) {
                                    open(model);
                                }
                            }
                        });
                    } else if (TextUtils.equals(i, "done")) {
                        open(model);
                    } else {
                        getViewModel().remoteVerify(model.repo_id, i, new Consumer<ResultModel>() {
                            @Override
                            public void accept(ResultModel r) throws Exception {
                                if (r.success) {
                                    open(model);
                                } else {
                                    Toasts.show(r.error_msg);
                                    showPasswordDialogCallback(model.repo_id, model.repo_name, new OnResultListener<RepoModel>() {
                                        @Override
                                        public void onResultData(RepoModel repoModel) {
                                            if (repoModel != null) {
                                                open(model);
                                            }
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
            });
        } else {
            open(model);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void open(StarredModel model) {
        if (model.is_dir) {
            MainActivity.navToThis(requireContext(), model.repo_id, model.repo_name, model.path, model.is_dir);

        } else if (Utils.isViewableImage(model.obj_name)) {
            Intent getIntent = CarouselImagePreviewActivity.startThisFromStarred(requireContext(), model);
            imagePreviewActivityLauncher.launch(getIntent);

        } else if (model.obj_name.endsWith(Constants.Format.DOT_SDOC)) {
            SDocWebViewActivity.openSdoc(getContext(), model.repo_name, model.repo_id, model.path, model.obj_name);

        } else if (model.obj_name.endsWith(Constants.Format.DOT_DRAW) || model.obj_name.endsWith(Constants.Format.DOT_EXDRAW)) {
            SDocWebViewActivity.openDraw(getContext(), model.repo_name, model.repo_id, model.path, model.obj_name);

        } else if (Utils.isVideoFile(model.obj_name)) {
            checkRemoteAndFileCache(model, new Consumer<File>() {
                @Override
                public void accept(File localFile) throws Exception {
                    if (localFile != null) {
                        CustomExoVideoPlayerActivity.startThis(getContext(), model.obj_name, model.repo_id, model.path);
                    } else {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                        builder.setItems(R.array.video_download_array, (dialog, which) -> {
                            if (which == 0) {
                                CustomExoVideoPlayerActivity.startThis(getContext(), model.obj_name, model.repo_id, model.path);
                            } else if (which == 1) {
                                Intent intent = FileActivity.startFromStarred(requireContext(), model, FileReturnActionEnum.DOWNLOAD_VIDEO);
                                fileActivityLauncher.launch(intent);
                            }
                        }).show();
                    }
                }
            });
        } else if (Utils.isTextMimeType(model.obj_name)) {
            openWith(model, FileReturnActionEnum.OPEN_TEXT_MIME);
        } else {
            //Open with another app
            openWith(model, FileReturnActionEnum.OPEN_WITH);
        }
    }

    private void checkRemoteAndFileCache(StarredModel model, Consumer<File> consumer) {
        getViewModel().checkRemoteAndOpen(model.repo_id, model.path, new Consumer<String>() {
            @Override
            public void accept(String fileId) {
                File local = getLocalDestinationFile(model.repo_id, model.repo_name, model.path);
                if (consumer != null) {
                    try {
                        boolean r = !TextUtils.isEmpty(fileId) && local.exists();
                        if (r) {
                            consumer.accept(local);
                        } else {
                            consumer.accept(null);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    private void openWith(StarredModel model, FileReturnActionEnum actionEnum) {
        checkRemoteAndFileCache(model, new Consumer<File>() {
            @Override
            public void accept(File localFile) throws Exception {
                if (localFile != null) {
                    if (TextUtils.equals(FileReturnActionEnum.OPEN_WITH.name(), actionEnum.name())) {
                        WidgetUtils.openWith(requireContext(), localFile);

                    } else if (TextUtils.equals(FileReturnActionEnum.OPEN_TEXT_MIME.name(), actionEnum.name())) {
                        MarkdownActivity.start(requireContext(), localFile.getAbsolutePath(), model.repo_id, model.path);

                    }
                } else {
                    Intent intent = FileActivity.startFromStarred(requireContext(), model, actionEnum);
                    fileActivityLauncher.launch(intent);
                }
            }
        });

    }

    private File getLocalDestinationFile(String repoId, String repoName, String fullPathInRepo) {
        Account account = SupportAccountManager.getInstance().getCurrentAccount();
        return DataManager.getLocalFileCachePath(account, repoId, repoName, fullPathInRepo);
    }

    private ActivityResultLauncher<Intent> imagePreviewActivityLauncher;
    private ActivityResultLauncher<Intent> fileActivityLauncher;

    private void registerResultLauncher() {

        imagePreviewActivityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult o) {
                if (o.getResultCode() != Activity.RESULT_OK) {
                    return;
                }

                reload();
            }
        });

        fileActivityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult o) {
                if (o.getResultCode() != Activity.RESULT_OK) {
                    return;
                }

                Intent data = o.getData();
                if (o.getData() == null) {
                    return;
                }

                String action = data.getStringExtra("action");
                String repoId = data.getStringExtra("repo_id");
                String targetFile = data.getStringExtra("target_file");
                String localFullPath = data.getStringExtra("destination_path");
                boolean isUpdateWhenFileExists = data.getBooleanExtra("is_update", false);

                if (TextUtils.isEmpty(localFullPath)) {
                    return;
                }

                if (isUpdateWhenFileExists) {
                    Toasts.show(R.string.download_finished);
                }

                File destinationFile = new File(localFullPath);

                if (TextUtils.equals(FileReturnActionEnum.EXPORT.name(), action)) {

                } else if (TextUtils.equals(FileReturnActionEnum.SHARE.name(), action)) {

                } else if (TextUtils.equals(FileReturnActionEnum.DOWNLOAD_VIDEO.name(), action)) {

                } else if (TextUtils.equals(FileReturnActionEnum.OPEN_WITH.name(), action)) {

                    WidgetUtils.openWith(requireContext(), destinationFile);
                } else if (TextUtils.equals(FileReturnActionEnum.OPEN_TEXT_MIME.name(), action)) {

                    MarkdownActivity.start(requireContext(), localFullPath, repoId, targetFile);
                }
            }
        });
    }

}

