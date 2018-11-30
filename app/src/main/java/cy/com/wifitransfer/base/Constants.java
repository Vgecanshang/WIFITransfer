package cy.com.wifitransfer.base;

import android.os.Environment;

import java.io.File;

/**
 * Created by cy on 2018/11/8.
 */
public class Constants {
    public static final int HTTP_PORT = 12345;
    public static final int HTTP_IMG_PORT = 54321;
    public static final String DIR_FILESHARE_IN_SDCARD = "WifiTransfer";
    /** 26(8.0系统)以上的截图路径 */
    public static final String DIR_SCREENSHOT_IN_SDCARD_26 = "/DCIM/Screenshots";
    /** 26及以下(7.0、5.0等)的截图路径 */
    public static final String DIR_SCREENSHOT_IN_SDCARD = "/Pictures/Screenshots";
    public static final int MSG_DIALOG_DISMISS = 0;
    public static final File DIR = new File(Environment.getExternalStorageDirectory() + File.separator + Constants.DIR_FILESHARE_IN_SDCARD);
    public static final File SCREENSHOT_DIR_26 = new File(Environment.getExternalStorageDirectory().getPath()+Constants.DIR_SCREENSHOT_IN_SDCARD_26);
    public static final File SCREENSHOT_DIR = new File(Environment.getExternalStorageDirectory().getPath()+Constants.DIR_SCREENSHOT_IN_SDCARD);
    public static final class RxBusEventType {
        public static final String POPUP_MENU_DIALOG_SHOW_DISMISS = "POPUP MENU DIALOG SHOW DISMISS";
        public static final String WIFI_CONNECT_CHANGE_EVENT = "WIFI CONNECT CHANGE EVENT";
        public static final String LOAD_FILE_LIST = "LOAD FILE LIST";
        public static final String FILE_SHARE_SERVICE_STATUS = "FILE_SHARE_SERVICE_STATUS";
    }
}
