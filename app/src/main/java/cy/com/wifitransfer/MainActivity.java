package cy.com.wifitransfer;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import com.bumptech.glide.Glide;
import com.cy.cylibrary.CBaseActivity;
import com.cy.cylibrary.DynamicPermission.ApplyPermissionUtil;
import com.cy.cylibrary.DynamicPermission.PermissionUtil;
import com.cy.cylibrary.recycler.common.CommonAdapter;
import com.cy.cylibrary.recycler.common.base.ViewHolder;
import com.cy.cylibrary.utils.CLogger;
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
import io.reactivex.*;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
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
public class MainActivity extends CBaseActivity implements Animator.AnimatorListener {

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
    private CommonAdapter<TransferFile> filesAdapter ;
    private List<TransferFile> files = new ArrayList<>();
    private int[] itemBgRes = new int[]{R.drawable.admin_data_item_bg_1 , R.drawable.admin_data_item_bg_2 , R.drawable.admin_data_item_bg_3 , R.drawable.admin_data_item_bg_4};

    /** 需要安装的APK文件（适配8.0的安装） */
    private TransferFile installFile = null;
    private AppInstallReceiver appInstallReceiver = null;
    private SwitchCompat switch_btn;

    /** 三方动态申请权限工具类 */
    private PermissionUtil permissionUtil = null;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this);
        mToolbar.setLogo(R.mipmap.ic_logo);
        switch_btn  = mToolbar.findViewById(R.id.switch_btn);
        switch_btn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked){
                Toast.makeText(getApplicationContext() , "服务已开启...", Toast.LENGTH_SHORT).show();
                if(!WebService.isStarted()){
                    WebService.start(getActivity());
                }
                new PopupMenuDialog(getActivity()).builder().setCancelable(false).setCanceledOnTouchOutside(true).show();
                startRefresh();
            }else{
                Toast.makeText(getApplicationContext() , "服务已关闭...", Toast.LENGTH_SHORT).show();
                WebService.stop(getActivity());
                stopRefresh();
            }

        });
        setSupportActionBar(mToolbar);
        Timber.plant(new Timber.DebugTree());
        RxBus.get().register(getActivity());
        initRecyclerView();

        permissionUtil = new PermissionUtil(getFragmentActivity());
        permissionUtil.requestEach(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe( permission -> {

                    if(permission.name.equals(Manifest.permission.READ_EXTERNAL_STORAGE) && permission.granted){
                        //第一次安装时候 没有读写权限，导致没有读取本地的文件，则在获取权限成功后 需要重新读一遍
                        RxBus.get().post(Constants.RxBusEventType.LOAD_FILE_LIST, 0);
                    }

                    if(!permission.granted && permission.shouldShowRequestPermissionRationale){
                        Toast.makeText(getActivity() , "获取手机的文件的读取权限失败，请去设置中打开吧!" , Toast.LENGTH_SHORT).show();
                    }

                });

        registerAppInstallReceiver();
    }


    /**
     * 开始刷新动画
     *
     * 开始的时候，设置setIndeterminateDrawable和setProgressDrawable为定义的xml文件，即可开始转动。
     * 结束的时候，设置setIndeterminateDrawable和setProgressDrawable为固定的图片，即可停止转动。
     */
    public void startRefresh() {
        progress_bar.setVisibility(View.VISIBLE);
    }

    /**
     * 停止刷新动画
     */
    public void stopRefresh() {
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

    @OnClick(R.id.fab)
    public void onClick(View view) {
        new PopupMenuDialog(getActivity()).builder().setCancelable(false).setCanceledOnTouchOutside(true).show();
    }



    @Subscribe(tags = {@Tag(Constants.RxBusEventType.POPUP_MENU_DIALOG_SHOW_DISMISS)})
    public void onPopupMenuDialogDismiss(Integer type) {
        //弹窗消失
    }

    @Override
    public void onAnimationStart(Animator animation) {
        Log.d("WebService", "WebService MainActivity start.");
        //弹窗动画启动
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

        filesAdapter = new CommonAdapter<TransferFile>(getActivity() , R.layout.layout_book_item , files) {
            @Override
            protected void convert(ViewHolder holder, TransferFile transferFile, int position) {
                initViewItem(holder , transferFile , position);
            }
        };

        filesAdapter.setOnItemClickListener((view, holder, o, position) -> {
            Toast.makeText(getActivity() , "我是"+o.getName() , Toast.LENGTH_SHORT).show();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(filesAdapter);
//        mHeaderAndFooterWrapper = new HeaderAndFooterWrapper(filesAdapter);
//        //Error:HeaderAndFooterWrapper.addHeaderView后导致OnItemClick position错乱了
//        mHeaderAndFooterWrapper.addHeaderView(View.inflate(MainActivity.this , R.layout.layout_main_header , null));
//        recyclerView.setAdapter(mHeaderAndFooterWrapper);

    }

    @Subscribe(thread = EventThread.IO, tags = {@Tag(Constants.RxBusEventType.LOAD_FILE_LIST)})
    public void loadFileList(Integer type) {
        Timber.d("loadFileList:" + Thread.currentThread().getName());
        List<TransferFile> fileList = loadFileData();

        runOnUiThread(() -> {
            files.clear();
            files.addAll(fileList);
            filesAdapter.notifyDataSetChanged();
            if (files == null || files.size() <= 0) {
                mNoDataView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                mNoDataView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });


    }


    @Deprecated
    private void loadFileList() {
        //主动load本地的文件
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
//                filesAdapter.notifyDataSetChanged();
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                filesAdapter.notifyDataSetChanged();
//            }
//
//            @Override
//            public void onNext(List<TransferFile> filesList) {
//                files.clear();
//                files.addAll(filesList);
//            }
//        });
        Observable.create(new ObservableOnSubscribe<List<TransferFile>>() {
            @Override
            public void subscribe(ObservableEmitter<List<TransferFile>> emitter) throws Exception {
                List<TransferFile> tFiles = loadFileData();
                emitter.onNext(tFiles);
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<TransferFile>>() {

                    private Disposable disposable = null;

                    @Override
                    public void onSubscribe(Disposable d) {
                        disposable = d;
                    }

                    @Override
                    public void onNext(List<TransferFile> transferFiles) {
                        files.clear();
                        files.addAll(transferFiles);
                    }

                    @Override
                    public void onError(Throwable e) {
                        filesAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onComplete() {
                        filesAdapter.notifyDataSetChanged();
                        if (disposable != null) {
                            //解除订阅，Observer观察者不再接收上游事件
                            disposable.dispose();
                        }
                    }
                });

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
    public void initViewItem(ViewHolder holder , TransferFile file , int position){
        holder.getView(R.id.ll_item).setBackgroundResource(itemBgRes[position%itemBgRes.length]);
        holder.setText(R.id.tv_name, file.getName());
        holder.setText(R.id.tv_file_path, file.getPath());
        holder.setText(R.id.tv_size, file.getSize());
        holder.setVisible(R.id.tv_install , false);
        holder.setVisible(R.id.tv_uninstall , false);
        ImageView icon = holder.getView(R.id.iv_icon);
        if (file.getType() == FILE_TYPE_APK) {
            icon.setImageDrawable(file.getIcon());
            if (((ApkFile) file).isInstall()) {
                holder.getView(R.id.tv_uninstall).setTag(file);
                holder.setVisible(R.id.tv_install , false);
                holder.setVisible(R.id.tv_uninstall , true);
                holder.getView(R.id.tv_uninstall).setOnClickListener(v -> {
                    TransferFile transferFile = (TransferFile) v.getTag();
                    ApkUtil.unInstallApk(getActivity() , transferFile);
                });
            } else {
                holder.getView(R.id.tv_install).setTag(file);
                holder.setVisible(R.id.tv_install , true);
                holder.setVisible(R.id.tv_uninstall , false);
                holder.getView(R.id.tv_install).setOnClickListener(v -> {
                    TransferFile transferFile = (TransferFile) v.getTag();
                    installFile = transferFile;
                    permissionUtil.request(Manifest.permission.REQUEST_INSTALL_PACKAGES).subscribe( granted -> {
                        if(granted){
                            Toast.makeText(getActivity(), "获取安装应用权限成功...", Toast.LENGTH_LONG).show();
                            ApkUtil.installApk(getActivity(), installFile);
                        }else{
                            Toast.makeText(getActivity(), "获取安装应用权限失败，这将导致无法通过WIFITransfer安装应用!\n快去设置中打开吧!", Toast.LENGTH_LONG).show();
                        }
                    });

                });
            }
        }else if(file.getType() == FILE_TYPE_JPG || file.getType() == FILE_TYPE_PNG){
            //本地文件
            File imgFile = new File(file.getPath());
            //加载图片
            Glide.with(getActivity()).load(imgFile).into(icon);
            //在这里可以提供点击预览图片功能
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
            icon.setImageResource(R.drawable.ic_book_cover);
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
        WebService.stop(getActivity());
        if (mUnbinder != null) {
            mUnbinder.unbind();
        }
        RxBus.get().unregister(getActivity());
    }

    @Override
    protected boolean hadSetSystemStatus() {
        return true;
    }

    @Override
    protected int getStatusColor() {
        return R.color.colorPrimary;
    }
}
