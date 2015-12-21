package com.socks.meituanmultidex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import com.socks.meituanmultidex.activity.WaitingActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhaokaiqiang on 15/12/18.
 */
public class MeituanInstrumentation extends Instrumentation {

    private List<String> mByPassActivityClassNameList;

    public MeituanInstrumentation() {
        mByPassActivityClassNameList = new ArrayList<>();
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {

//        if (intent.getComponent() != null) {
//            className = intent.getComponent().getClassName();
//        }

        boolean shouldInterrupted = !MeituanApplication.isDexAvailable();
        if (mByPassActivityClassNameList.contains(className)) {
            shouldInterrupted = false;
        }
        if (shouldInterrupted) {
            className = WaitingActivity.class.getName();
        } else {
            mByPassActivityClassNameList.add(className);

        }
        return super.newActivity(cl, className, intent);
    }

//    @Override
//    public Activity newActivity(Class<?> clazz, Context context, IBinder token, Application application, Intent intent, ActivityInfo info, CharSequence title, Activity parent, String id, Object lastNonConfigurationInstance) throws InstantiationException, IllegalAccessException {
//        String className = "";
//        Activity newActivity;
//        if (intent.getComponent() != null) {
//            className = intent.getComponent().getClassName();
//        }
//
//        boolean shouldInterrupted = !MeituanApplication.isDexAvailable();
//        if (mByPassActivityClassNameList.contains(className)) {
//            shouldInterrupted = false;
//        }
//        if (shouldInterrupted) {
//            intent = new Intent(context, WaitingActivity.class);
//            newActivity = mBase.newActivity(clazz, context, token,
//                    application, intent, info, title, parent, id,
//                    lastNonConfigurationInstance);
//        } else {
//            mByPassActivityClassNameList.add(className);
//            newActivity = mBase.newActivity(clazz, context, token,
//                    application, intent, info, title, parent, id,
//                    lastNonConfigurationInstance);
//        }
//        return newActivity;
//    }

}
