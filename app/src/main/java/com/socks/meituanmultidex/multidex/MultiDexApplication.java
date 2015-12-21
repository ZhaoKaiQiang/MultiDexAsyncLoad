package com.socks.meituanmultidex.multidex;

import android.app.*;
import android.content.*;

public class MultiDexApplication extends Application
{
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(base);
        MultiDex.install((Context)this);
    }
}
