package cy.com.wifitransfer;

import android.Manifest;
import android.animation.Animator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import com.bumptech.glide.Glide;
import com.cy.cylibrary.DynamicPermission.ApplyPermissionUtil;
import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;
import com.hwangjr.rxbus.thread.EventThread;
import cy.com.wifitransfer.base.Constants;
import cy.com.wifitransfer.bean.ApkFile;
import cy.com.wifitransfer.bean.BaseFile;
import cy.com.wifitransfer.bean.TransferFile;
import cy.com.wifitransfer.util.ApkUtil;
import cy.com.wifitransfer.view.PopupMenuDialog;
import timber.log.Timber;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.cy.cylibrary.DynamicPermission.ApplyPermissionUtil.TYPE_EXTERNAL_STORAGE;
import static com.cy.cylibrary.DynamicPermission.ApplyPermissionUtil.TYPE_REQUEST_INSTALL_PACKAGES;
import static cy.com.wifitransfer.bean.BaseFile.*;

/**
 * @author cy
 */
public class MainActivity extends AppCompatActivity implements Animator.AnimatorListener {

    Unbinder mUnbinder;
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.fab)
    FloatingActionButton mFab;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    @BindView(R.id.rl_no_data)
    View mNoDataView;
    @BindView(R.id.progress_bar)
    ProgressBar progress_bar;
    List<TransferFile> files = new ArrayList<>();
    FileAdapter fileAdapter;

    private ApplyPermissionUtil permissionUtil = null;//三方动态申请权限工具类
    /** 需要安装的APK文件（适配8.0的安装） */
    private  TransferFile installFile = null;

    private AppInstallReceiver appInstallReceiver = null;

    private SwitchCompat switch_btn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this);
        mToolbar.setLogo(R.mipmap.ic_logo);
        switch_btn  = mToolbar.findViewById(R.id.switch_btn);
        switch_btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked){
                    Toast.makeText(getApplicationContext() , "服务已开启...", Toast.LENGTH_LONG).show();
                    if(!WebService.isStarted()){
                        WebService.start(MainActivity.this);
                    }
                    new PopupMenuDialog(MainActivity.this).builder().setCancelable(false).setCanceledOnTouchOutside(true).show();
                    startRefresh();
                }else{
                    Toast.makeText(getApplicationContext() , "服务已关闭...", Toast.LENGTH_LONG).show();
                    WebService.stop(MainActivity.this);
                    stopRefresh();
                }

            }
        });
        setSupportActionBar(mToolbar);
        Timber.plant(new Timber.DebugTree());
        RxBus.get().register(this);
        initRecyclerView();

        permissionUtil = new ApplyPermissionUtil(MainActivity.this, requestPermissionsListener);
        permissionUtil.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, TYPE_EXTERNAL_STORAGE);

        registerAppInstallReceiver();


    }


    /**
     * 开始刷新动画
     *
     * 开始的时候，设置setIndeterminateDrawable和setProgressDrawable为定义的xml文件，即可开始转动。
     * 结束的时候，设置setIndeterminateDrawable和setProgressDrawable为固定的图片，即可停止转动。
     */
    public void startRefresh() {
//        progress_bar.setIndeterminateDrawable(getResources().getDrawable(
//                R.drawable.normal_loading_style));
//        progress_bar.setProgressDrawable(getResources().getDrawable(
//                R.drawable.normal_loading_style));
        progress_bar.setVisibility(View.VISIBLE);
    }

    /**
     * 停止刷新动画
     */
    public void stopRefresh() {
//        progress_bar.setIndeterminateDrawable(getResources().getDrawable(
//                R.drawable.icon_waiting));
//        progress_bar.setProgressDrawable(getResources().getDrawable(
//                R.drawable.icon_waiting));
        progress_bar.setVisibility(View.GONE);
    }

    /**
     * 在Android 8.0的平台上，应用不能对大部分的广播进行静态注册
     * 监听APK的安装和卸载 不能使用静态广播，只能使用动态广播
     * */
    private void registerAppInstallReceiver(){
        appInstallReceiver = new AppInstallReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addDataScheme("package");
        registerReceiver(appInstallReceiver , filter);
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
                        //第一次安装时候 没有读写权限，导致没有读取本地的文件，则在获取权限成功后 需要重新读一遍
                        RxBus.get().post(Constants.RxBusEventType.LOAD_FILE_LIST, 0);
                    } else {
                        Toast.makeText(MainActivity.this, "获取读写权限失败...", Toast.LENGTH_LONG).show();
                        finish();
                    }
                    break;
                case TYPE_REQUEST_INSTALL_PACKAGES:
                    if (b) {
                        Toast.makeText(MainActivity.this, "获取安装应用权限成功...", Toast.LENGTH_LONG).show();
                        ApkUtil.installApk(MainActivity.this, installFile);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        permissionUtil.listenerInstallPackagePermissionResult(requestCode , resultCode , data);

    }

    @OnClick(R.id.fab)
    public void onClick(View view) {
//        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFab, "translationY", 0, mFab.getHeight() * 2).setDuration(200L);
//        objectAnimator.setInterpolator(new AccelerateInterpolator());
//        objectAnimator.addListener(this);
//        objectAnimator.start();
        new PopupMenuDialog(MainActivity.this).builder().setCancelable(false).setCanceledOnTouchOutside(true).show();
    }



    @Subscribe(tags = {@Tag(Constants.RxBusEventType.POPUP_MENU_DIALOG_SHOW_DISMISS)})
    public void onPopupMenuDialogDismiss(Integer type) {
//        if (type == Constants.MSG_DIALOG_DISMISS) {
//            WebService.stop(this);
//            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFab, "translationY", mFab.getHeight() * 2, 0).setDuration(200L);
//            objectAnimator.setInterpolator(new AccelerateInterpolator());
//            objectAnimator.start();
//        }
    }

    @Override
    public void onAnimationStart(Animator animation) {
        Log.d("WebService", "WebService MainActivity start.");
//        if (!WebService.isStarted()){
//            WebService.start(this);
//        }
//        new PopupMenuDialog(this).builder().setCancelable(false)
//                .setCanceledOnTouchOutside(false).show();
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
    }

    @Subscribe(thread = EventThread.IO, tags = {@Tag(Constants.RxBusEventType.LOAD_FILE_LIST)})
    public void loadFileList(Integer type) {
        Timber.d("loadFileList:" + Thread.currentThread().getName());
        List<TransferFile> fileList = loadFileData();

        runOnUiThread(() -> {
            files.clear();
            files.addAll(fileList);
            fileAdapter.notifyDataSetChanged();

            if (files == null || files.size() <= 0) {
                mNoDataView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                mNoDataView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });


    }

//    @Deprecated
//    private void loadFileList() {
//        Observable.create(new Observable.OnSubscribe<List<TransferFile>>() {
//            @Override
//            public void call(Subscriber<? super List<TransferFile>> subscriber) {
//                List<TransferFile> tFiles = loadFileData();
//                subscriber.onNext(tFiles);
//                subscriber.onCompleted();
//            }
//        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<List<TransferFile>>() {
//            @Override
//            public void onCompleted() {
//                fileAdapter.notifyDataSetChanged();
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                fileAdapter.notifyDataSetChanged();
//            }
//
//            @Override
//            public void onNext(List<TransferFile> filesList) {
//                files.clear();
//                files.addAll(filesList);
//            }
//        });
//    }


    private int[] itemBgRes = new int[]{R.drawable.admin_data_item_bg_1 , R.drawable.admin_data_item_bg_2 , R.drawable.admin_data_item_bg_3 , R.drawable.admin_data_item_bg_4};
    public class FileAdapter extends RecyclerView.Adapter<FileAdapter.MyViewHolder> {

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            MyViewHolder holder = new MyViewHolder(LayoutInflater.from(
                    MainActivity.this).inflate(R.layout.layout_book_item, parent,
                    false));
            return holder;
        }

        @Override
        public void onBindViewHolder(final MyViewHolder holder, int position) {
            initViewItem(holder , position);
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

       public class MyViewHolder extends RecyclerView.ViewHolder {
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
                        ApkUtil.apkInfo((ApkFile) bean, f.getAbsolutePath(), MainActivity.this);
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

    /** 文件Item View的处理 */
    public void initViewItem(FileAdapter.MyViewHolder holder , int position){
        TransferFile file = files.get(position);
        holder.ll_item.setBackgroundResource(itemBgRes[position%itemBgRes.length]);
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
                    ApkUtil.unInstallApk(MainActivity.this , transferFile);
                });
            } else {
                holder.tvInstall.setTag(file);
                holder.tvInstall.setVisibility(View.VISIBLE);
                holder.tvUnInstall.setVisibility(View.GONE);
                holder.tvInstall.setOnClickListener(v -> {
                    TransferFile transferFile = (TransferFile) v.getTag();
                    installFile = transferFile;
                    permissionUtil.requestPermissions(new String[]{Manifest.permission.REQUEST_INSTALL_PACKAGES}, TYPE_REQUEST_INSTALL_PACKAGES);
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
        if(appInstallReceiver != null){
            unregisterReceiver(appInstallReceiver);
        }
        super.onDestroy();
        WebService.stop(this);
        if (mUnbinder != null) {
            mUnbinder.unbind();
        }
        RxBus.get().unregister(this);
    }
}
