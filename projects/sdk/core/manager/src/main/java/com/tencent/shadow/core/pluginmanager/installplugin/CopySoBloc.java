package com.tencent.shadow.core.pluginmanager.installplugin;


import android.text.TextUtils;

import com.tencent.commonsdk.zip.QZipInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;


public class CopySoBloc {

    private static ConcurrentHashMap<String, Object> sLocks = new ConcurrentHashMap<>();

    public static File copySo(File root, File apkFile, String UUID, String partKey, String abi) throws InstallPluginException {
        String key = UUID + "_" + partKey;
        Object lock = sLocks.get(key);
        if (lock == null) {
            lock = new Object();
            sLocks.put(key, lock);
        }
        File libRoot = new File(root, "lib");
        File soDir = new File(libRoot, UUID + "_lib");
        File copiedTagFile = new File(soDir, key + "_copied");
        String filter = "lib/" + abi+"/";
        synchronized (lock) {

            if (TextUtils.isEmpty(abi) || copiedTagFile.exists()) {
                return soDir;
            }

            //如果so目录存在但是个文件，不是目录，那超出预料了。删除了也不一定能工作正常。
            if (soDir.exists() && soDir.isFile()) {
                throw new InstallPluginException("soDir=" + soDir.getAbsolutePath() + "已存在，但它是个文件，不敢贸然删除");
            }

            //创建so目录
            soDir.mkdirs();

            ZipEntry zipEntry = null;
            QZipInputStream zipInputStream = null;
            try {
                zipInputStream = new QZipInputStream(new FileInputStream(apkFile));
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    if (zipEntry.getName().startsWith(filter)) {
                        BufferedOutputStream output = null;
                        try {
                            File file = new File(soDir, zipEntry.getName());
                            File parent = file.getParentFile();
                            if (!parent.exists()) {
                                parent.mkdirs();
                            }
                            output = new BufferedOutputStream(
                                    new FileOutputStream(file));
                            BufferedInputStream input = new BufferedInputStream(zipInputStream);
                            byte b[] = new byte[8192];
                            int n;
                            while ((n = input.read(b, 0, 8192)) >= 0) {
                                output.write(b, 0, n);
                            }
                        } finally {
                            zipInputStream.closeEntry();
                            if (output != null) {
                                output.close();
                            }
                        }
                    }
                }

                // 外边创建完成标记
                try {
                    copiedTagFile.createNewFile();
                } catch (IOException e) {
                    throw new InstallPluginException("创建so复制完毕 创建tag文件失败：" + copiedTagFile.getAbsolutePath(), e);
                }

            } catch (Exception e) {
                throw new InstallPluginException("解压so 失败 apkFile:" + apkFile.getAbsolutePath() + " abi:" + abi, e);
            } finally {
                try {
                    if (zipInputStream != null) {
                        zipInputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        return soDir;
    }


}
