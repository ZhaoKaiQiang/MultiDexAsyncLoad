package com.socks.meituanmultidex.framework;


import com.socks.meituanmultidex.log.Logger;
import com.socks.meituanmultidex.log.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Created by yb.wang on 14/12/31.
 * Bundle接口实现类，管理Bundle的生命周期。
 * meta文件存储BundleId，Location等
 */
public final class BundleImpl implements Bundle {
    static final Logger log;
    final File bundleDir;
    final String location;
    final long bundleID;
    int state;
    //是否dex优化
    volatile boolean isOpt;

    static {
        log = LoggerFactory.getLogcatLogger("BundleImpl");
    }

    BundleImpl(File bundleDir) throws Exception {
        this.isOpt = false;


        DataInputStream dataInputStream = new DataInputStream(new FileInputStream(new File(bundleDir, "meta")));
        this.bundleID = dataInputStream.readLong();
        this.location = dataInputStream.readUTF();

        dataInputStream.close();


        this.bundleDir = bundleDir;

    }

    @Override
    public long getBundleId() {
        return this.bundleID;
    }

    @Override
    public String getLocation() {
        return this.location;
    }


    @Override
    public int getState() {
        return this.state;
    }

    @Override
    public void update(InputStream inputStream) throws BundleException {

    }

    void updateMetadata() {
        File file = new File(this.bundleDir, "meta");
        DataOutputStream dataOutputStream;
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            dataOutputStream = new DataOutputStream(fileOutputStream);
            dataOutputStream.writeLong(this.bundleID);
            dataOutputStream.writeUTF(this.location);

            dataOutputStream.flush();
            fileOutputStream.getFD().sync();
            if (dataOutputStream != null)
                try {
                    dataOutputStream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
        } catch (Throwable e) {
            log.log("Could not save meta data " + file.getAbsolutePath(), Logger.LogLevel.ERROR, e);
        }

    }

    public String toString() {
        return "Bundle [" + this.bundleID + "]: " + this.location;
    }

}
