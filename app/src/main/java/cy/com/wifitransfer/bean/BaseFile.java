package cy.com.wifitransfer.bean;

import java.io.File;
import java.text.DecimalFormat;

/**
 * Created by cy on 2018/11/9.
 */
public abstract class BaseFile {

    /** APK文件*/
    public static final int FILE_TYPE_APK  = 1;
    /** JPG文件*/
    public static final int FILE_TYPE_JPG  = 2;
    /** PNG文件*/
    public static final int FILE_TYPE_PNG  = 3;
    /** text文件*/
    public static final int FILE_TYPE_TXT  = 4;

    /** 文件名 */
    public String name;
    /** 文件大小（字节） */
    public long size;
    /** 文件路径 */
    public String path;
    /** 文件类型 */
    public int type;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSize() {
        int GB = 1024 * 1024 * 1024;//定义GB的计算常量
        int MB = 1024 * 1024;//定义MB的计算常量
        int KB = 1024;//定义KB的计算常量
        DecimalFormat df = new DecimalFormat("0.00");//格式化小数
        String resultSize = "";
        if (size / GB >= 1) {
            //如果当前Byte的值大于等于1GB
            resultSize = df.format(size / (float) GB) + "GB";
        } else if (size / MB >= 1) {
            //如果当前Byte的值大于等于1MB
            resultSize = df.format(size / (float) MB) + "MB";
        } else if (size / KB >= 1) {
            //如果当前Byte的值大于等于1KB
            resultSize = df.format(size / (float) KB) + "KB";
        } else {
            resultSize = size + "B   ";
        }

        return resultSize;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    /**
     * 检查文件的类型
     * @param file
     * @return
     */
    public static int checkFilterType(File file){
        if(file.getName().endsWith(".apk")){
            return FILE_TYPE_APK;
        }else if(file.getName().endsWith(".txt")){
            return FILE_TYPE_TXT;
        }else if(file.getName().endsWith(".jpg")){
            return FILE_TYPE_JPG;
        }else if(file.getName().endsWith(".png")){
            return FILE_TYPE_PNG;
        }else {
            return -1;
        }
    }

}
