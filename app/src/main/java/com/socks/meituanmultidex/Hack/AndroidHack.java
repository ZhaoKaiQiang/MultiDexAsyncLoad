package com.socks.meituanmultidex.Hack;

import android.os.Handler;
import android.os.Looper;

/**
 * Created by zhaokaiqiang on 15/12/19.
 */
public class AndroidHack {

    private static Object _sActivityThread;

    public static Object getActivityThread() throws Exception {
        if (_sActivityThread == null) {
            if (Thread.currentThread().getId() == Looper.getMainLooper().getThread().getId()) {
                _sActivityThread = SysHacks.ActivityThread_currentActivityThread.invoke(null, new Object[0]);
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                synchronized (SysHacks.ActivityThread_currentActivityThread) {
                    handler.post(new ActivityThreadGetter());
                    SysHacks.ActivityThread_currentActivityThread.wait();
                }
            }
        }
        return _sActivityThread;
    }

    public static class ActivityThreadGetter implements Runnable {
        public ActivityThreadGetter() {
        }

        public void run() {
            try {
                _sActivityThread = SysHacks.ActivityThread_currentActivityThread.invoke(SysHacks.ActivityThread.getmClass(), new Object[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            synchronized (SysHacks.ActivityThread_currentActivityThread) {
                SysHacks.ActivityThread_currentActivityThread.notify();
            }
        }
    }

}
