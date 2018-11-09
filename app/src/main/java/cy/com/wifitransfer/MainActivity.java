package cy.com.wifitransfer;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.widget.Toast;
import butterknife.*;
import com.bumptech.glide.Glide;
import com.cy.cylibrary.DynamicPermission.ApplyPermissionUtil;
import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;
import com.hwangjr.rxbus.thread.EventThread;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cy.com.wifitransfer.base.Constants;
import cy.com.wifitransfer.bean.ApkFile;
import cy.com.wifitransfer.bean.BaseFile;
import cy.com.wifitransfer.bean.TransferFile;
import cy.com.wifitransfer.view.PopupMenuDialog;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

import static com.cy.cylibrary.DynamicPermission.ApplyPermissionUtil.TYPE_EXTERNAL_STORAGE;
import static cy.com.wifitransfer.bean.BaseFile.FILE_TYPE_APK;
import static cy.com.wifitransfer.bean.BaseFile.FILE_TYPE_JPG;
import static cy.com.wifitransfer.bean.BaseFile.FILE_TYPE_PNG;

public class MainActivity extends AppCompatActivity implements Animator.AnimatorListener {

    Unbinder mUnbinder;
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.fab)
    FloatingActionButton mFab;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;

    List<TransferFile> files = new ArrayList<>();
    FileAdapter fileAdapter;

    private ApplyPermissionUtil permissionUtil = null;//三方动态申请权限工具类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this);
        mToolbar.setLogo(R.mipmap.ic_launcher);
        setSupportActionBar(mToolbar);
        Timber.plant(new Timber.DebugTree());
        RxBus.get().register(this);
        initRecyclerView();

        permissionUtil = new ApplyPermissionUtil(MainActivity.this, requestPermissionsListener);
        permissionUtil.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, TYPE_EXTERNAL_STORAGE);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionUtil.listenerRequestPermissionsResult(requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    ApplyPermissionUtil.RequestPermissionsListener requestPermissionsListener = new ApplyPermissionUtil.RequestPermissionsListener() {
        @Override
        public void getRequestPermissionResult(boolean b, int i) {
            switch (i) {
                case TYPE_EXTERNAL_STORAGE:
                    if (b) {
                        Toast.makeText(MainActivity.this, "获取文件读取权限成功...", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "获取读写权限失败...", Toast.LENGTH_LONG).show();
                        finish();
                    }
                    break;
                default:
                    break;
            }
        }
    };


    @OnClick(R.id.fab)
    public void onClick(View view) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFab, "translationY", 0, mFab.getHeight() * 2).setDuration(200L);
        objectAnimator.setInterpolator(new AccelerateInterpolator());
        objectAnimator.addListener(this);
        objectAnimator.start();
    }



    @Subscribe(tags = {@Tag(Constants.RxBusEventType.POPUP_MENU_DIALOG_SHOW_DISMISS)})
    public void onPopupMenuDialogDismiss(Integer type) {
        if (type == Constants.MSG_DIALOG_DISMISS) {
            WebService.stop(this);
            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFab, "translationY", mFab.getHeight() * 2, 0).setDuration(200L);
            objectAnimator.setInterpolator(new AccelerateInterpolator());
            objectAnimator.start();
        }
    }

    @Override
    public void onAnimationStart(Animator animation) {
        Log.d("WebService", "WebService MainActivity start.");
        WebService.start(this);
        new PopupMenuDialog(this).builder().setCancelable(false)
                .setCanceledOnTouchOutside(false).show();
    }

    @Override
    public void onAnimationEnd(Animator animation) {
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    private void initRecyclerView() {
        fileAdapter = new FileAdapter();
        recyclerView.setHasFixedSize(true);
//        mBookList.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fileAdapter);
        RxBus.get().post(Constants.RxBusEventType.LOAD_FILE_LIST, 0);
    }

    @Subscribe(thread = EventThread.IO, tags = {@Tag(Constants.RxBusEventType.LOAD_FILE_LIST)})
    public void loadFileList(Integer type) {
        Timber.d("loadFileList:" + Thread.currentThread().getName());
        List<TransferFile> fileList = loadFileData();
        ;
        runOnUiThread(() -> {
            files.clear();
            files.addAll(fileList);
            fileAdapter.notifyDataSetChanged();
        });
    }

    @Deprecated
    private void loadFileList() {
        Observable.create(new Observable.OnSubscribe<List<TransferFile>>() {
            @Override
            public void call(Subscriber<? super List<TransferFile>> subscriber) {
                List<TransferFile> tFiles = loadFileData();
                subscriber.onNext(tFiles);
                subscriber.onCompleted();
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<List<TransferFile>>() {
            @Override
            public void onCompleted() {
                fileAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Throwable e) {
                fileAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNext(List<TransferFile> filesList) {
                files.clear();
                files.addAll(filesList);
            }
        });
    }

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.MyViewHolder> {

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            MyViewHolder holder = new MyViewHolder(LayoutInflater.from(
                    MainActivity.this).inflate(R.layout.layout_book_item, parent,
                    false));
            return holder;
        }

        @Override
        public void onBindViewHolder(final MyViewHolder holder, int position) {
            TransferFile file = files.get(position);
            holder.tvName.setText(file.getName());
            holder.tvFilePath.setText(file.getPath());
            holder.tvSize.setText(file.getSize());
            holder.tvInstall.setVisibility(View.GONE);
            holder.tvUnInstall.setVisibility(View.GONE);
            if (file.getType() == FILE_TYPE_APK) {
                holder.ivIcon.setImageDrawable(file.getIcon());
                if (((ApkFile) file).isInstall()) {
                    holder.tvUnInstall.setTag(file);
                    holder.tvInstall.setVisibility(View.GONE);
                    holder.tvUnInstall.setVisibility(View.VISIBLE);
                    holder.tvUnInstall.setOnClickListener(v -> {
                        TransferFile transferFile = (TransferFile) v.getTag();
                        unInstallApk(transferFile);
                    });
                } else {
                    holder.tvInstall.setTag(file);
                    holder.tvInstall.setVisibility(View.VISIBLE);
                    holder.tvUnInstall.setVisibility(View.GONE);
                    holder.tvInstall.setOnClickListener(v -> {
                        TransferFile transferFile = (TransferFile) v.getTag();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            boolean b = getPackageManager().canRequestPackageInstalls();
                            if (b) {
                                installApk(transferFile);
                            } else {
                                permissionUtil.requestPermissions(new String[]{Manifest.permission.REQUEST_INSTALL_PACKAGES}, TYPE_EXTERNAL_STORAGE);
                            }
                        } else {
                            installApk(transferFile);
                        }


                    });
                }
            }else if(file.getType() == FILE_TYPE_JPG || file.getType() == FILE_TYPE_PNG){
                //本地文件
                File imgFile = new File(file.getPath());
                //加载图片
                Glide.with(MainActivity.this).load(imgFile).into(holder.ivIcon);
//                holder.ll_item.setOnClickListener( v -> {
//                    //getUrl()获取文件目录，例如返回值为/storage/sdcard1/MIUI/music/mp3_hd/单色冰淇凌_单色凌.mp3  
//                    File parentFlie = new File(imgFile.getParent());
//                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//                    intent.setDataAndType(Uri.fromFile(parentFlie), "*/*");
//                    intent.addCategory(Intent.CATEGORY_OPENABLE);
//                    startActivity(intent);
//
//                });

            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_book_cover);
            }


        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class MyViewHolder extends RecyclerView.ViewHolder {
            LinearLayout ll_item;
            ImageView ivIcon;
            TextView tvName;
            TextView tvSize;
            TextView tvFilePath;
            TextView tvInstall;
            TextView tvUnInstall;

            public MyViewHolder(View view) {
                super(view);
                ll_item = view.findViewById(R.id.ll_item);
                ivIcon = view.findViewById(R.id.iv_icon);
                tvName = view.findViewById(R.id.tv_name);
                tvSize = view.findViewById(R.id.tv_size);
                tvFilePath = view.findViewById(R.id.tv_file_path);
                tvInstall = view.findViewById(R.id.tv_install);
                tvUnInstall = view.findViewById(R.id.tv_uninstall);
            }
        }
    }

    /**
     * 加载文件列表
     */
    private List<TransferFile> loadFileData() {
        List<TransferFile> transferFiles = new ArrayList<>();
        File dir = Constants.DIR;
        if (dir.exists() && dir.isDirectory()) {
            File[] localFiles = dir.listFiles();
            if (localFiles != null) {
                for (File f : localFiles) {
                    TransferFile bean = new TransferFile();
                    int type = BaseFile.checkFilterType(f);
                    if (type == FILE_TYPE_APK) {
                        bean = new ApkFile();
                        ApkFile.apkInfo((ApkFile) bean, f.getAbsolutePath(), MainActivity.this);
                    } else {
                        bean.setName(f.getName());
                    }
                    bean.setSize(f.length());
                    bean.setPath(f.getAbsolutePath());
                    bean.setType(type);
                    transferFiles.add(bean);
                }
            }
        }
        return transferFiles;
    }

    /** 安装APK （已兼容Android7.0及以上的安装   8.0的未测试） */
    private void installApk(TransferFile file) {
        ApkFile aFile = (ApkFile) file;
        File apkFile = new File(aFile.getPath());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri contentUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //兼容Android7.0及以上的 安装APP的
            contentUri = FileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID+".fileprovider", apkFile);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            contentUri = Uri.fromFile(apkFile);
        }
        intent.setDataAndType(contentUri,
                "application/vnd.android.package-archive");
        startActivity(intent);
    }

    /**
     * 卸载APK
     */
    private void unInstallApk(TransferFile file) {
        ApkFile apkFile = (ApkFile) file;
        Intent uninstall_intent = new Intent();
        uninstall_intent.setAction(Intent.ACTION_DELETE);
        uninstall_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        uninstall_intent.setData(Uri.parse("package:" + apkFile.getPackageName()));
        startActivity(uninstall_intent);
    }


    /**
     * APK安装和卸载的广播
     */
    public static class AppInstallReceiver extends BroadcastReceiver {
        private final static String TAG = "AppInstallReceiver";


        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData().getSchemeSpecificPart();
            switch (intent.getAction()) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    Log.i(TAG, "收到卸载广播，包名为 " + packageName);
                    break;
                case Intent.ACTION_MY_PACKAGE_REPLACED:
                    Log.i(TAG, "收到App更新广播，包名为 " + packageName);
                    break;
                case Intent.ACTION_PACKAGE_ADDED:
                    Log.i(TAG, "收到App安装广播，包名为 " + packageName);
                    break;
                default:
                    break;
            }
            RxBus.get().post(Constants.RxBusEventType.LOAD_FILE_LIST, 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebService.stop(this);
        if (mUnbinder != null) {
            mUnbinder.unbind();
        }
        RxBus.get().unregister(this);
    }
}
