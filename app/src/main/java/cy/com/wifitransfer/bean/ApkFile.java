package cy.com.wifitransfer.bean;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

/**
 * Created by cy on 2018/11/9.
 */
public class ApkFile extends TransferFile {
    private String packageName;
    private boolean install = false;

    public boolean isInstall() {
        return install;
    }

    public void setInstall(boolean install) {
        this.install = install;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /**
     * 获取apk包的信息：版本号，名称，图标等
     *
     * @param file
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
            file.setName(appName+"(" + version + ")");
            String pkgInfoStr = String.format("PackageName:%s, Vesion: %s, AppName: %s", packageName, version, appName);
            file.setInstall(isAppInstalled(context , packageName , version));
            file.setPackageName(packageName);
        }
    }


    /**
     * 判断APK文件是否已安装
     * @param context
     * @param packageName
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
