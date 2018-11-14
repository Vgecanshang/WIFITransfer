package cy.com.wifitransfer.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;
import cy.com.wifitransfer.BuildConfig;
import cy.com.wifitransfer.bean.ApkFile;
import cy.com.wifitransfer.bean.TransferFile;

import java.io.File;

/**
 * Created by cy on 2018/11/14.
 */
public class ApkUtil {

    /** 安装APK （已兼容Android7.0及以上的安装   8.0的未测试） */
    public static void  installApk(Context context , TransferFile file) {

        ApkFile aFile = (ApkFile) file;
        File apkFile = new File(aFile.getPath());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri contentUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //兼容Android7.0及以上的 安装APP的
            contentUri = FileProvider.getUriForFile(context.getApplicationContext(), BuildConfig.APPLICATION_ID+".fileprovider", apkFile);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            contentUri = Uri.fromFile(apkFile);
        }
        intent.setDataAndType(contentUri,
                "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    /**
     * 卸载APK
     */
    public static void unInstallApk(Context context , TransferFile file) {
        ApkFile apkFile = (ApkFile) file;
        Intent uninstall_intent = new Intent();
        uninstall_intent.setAction(Intent.ACTION_DELETE);
        uninstall_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        uninstall_intent.setData(Uri.parse("package:" + apkFile.getPackageName()));
        context.startActivity(uninstall_intent);
    }


    /**
     * 获取apk包的信息：版本号，名称，图标等
     *
     * @param file 文件
     * @param path 路径
     * @param context
     */
    public static void apkInfo(ApkFile file , String path ,Context context) {

        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
            /* 必须加这两句，不然下面icon获取是default icon而不是应用包的icon */
            appInfo.sourceDir = path;
            appInfo.publicSourceDir = path;
            String appName = pm.getApplicationLabel(appInfo).toString();// 得到应用名
            String packageName = appInfo.packageName; // 得到包名
            String version = pkgInfo.versionName; // 得到版本信息
            /* icon1和icon2其实是一样的 */
            Drawable icon1 = pm.getApplicationIcon(appInfo);// 得到图标信息
            Drawable icon2 = appInfo.loadIcon(pm);
            file.setIcon(icon1);
            file.setName(appName+"(v" + version + ")");
            String pkgInfoStr = String.format("PackageName:%s, Vesion: %s, AppName: %s", packageName, version, appName);
            file.setInstall(isAppInstalled(context , packageName , version));
            file.setPackageName(packageName);
        }
    }


    /**
     * 判断APK文件是否已安装
     * @param context
     * @param packageName 包名
     * @param version 比较的版本号
     * @return
     */
    private static boolean isAppInstalled(Context context , String packageName , String version){
        PackageManager pm = context.getPackageManager();
        boolean installed =false;
        try{
            PackageInfo pkgInfo = pm.getPackageInfo(packageName,PackageManager.GET_ACTIVITIES);
            if(pkgInfo.versionName.equals(version)){
                installed = true;
            }else{
                //版本不一致也视为未安装
                installed = false;
            }

        }catch(PackageManager.NameNotFoundException e){
            installed =false;
        }
        return installed;
    }


}
