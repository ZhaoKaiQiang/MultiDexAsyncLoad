package com.socks.meituanmultidex;

import android.app.Application;
import android.util.Log;

import com.socks.meituanmultidex.Hack.AndroidHack;
import com.socks.meituanmultidex.Hack.SysHacks;
import com.socks.meituanmultidex.multidex.MultiDex;

import java.lang.reflect.Field;

/**
 * Created by zhaokaiqiang on 15/12/18.
 */
public class MeituanApplication extends Application {

    private static final String TAG = "MeituanApplication";
    private static boolean isDexAvailable = false;

    @Override
    public void onCreate() {
        super.onCreate();
        loadOtherDexFile();
    }

    public static void attachInstrumentation() {
        try {
            SysHacks.defineAndVerify();
            MeituanInstrumentation meiTuanInstrumentation = new MeituanInstrumentation();
            Object activityThread = AndroidHack.getActivityThread();
            Field mInstrumentation = activityThread.getClass().getDeclaredField("mInstrumentation");
            mInstrumentation.setAccessible(true);
            mInstrumentation.set(activityThread, meiTuanInstrumentation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadOtherDexFile() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MultiDex.install(MeituanApplication.this);
                isDexAvailable = true;
                Log.e(TAG, "-------------isDexAvailable = " + isDexAvailable);
            }
        }).start();
    }

//    public boolean inject(String libPath) {
//        boolean hasBaseDexClassLoader = true;
//        try {
//            Class.forName("dalvik.system.BaseDexClassLoader");
//        } catch (ClassNotFoundException e) {
//            hasBaseDexClassLoader = false;
//        }
//        if (hasBaseDexClassLoader) {
//            PathClassLoader pathClassLoader = (PathClassLoader) getClassLoader();
//            DexClassLoader dexClassLoader = new DexClassLoader(libPath, getDir("dex", MODE_PRIVATE).getAbsolutePath(), libPath, getClassLoader());
//            try {
//                Object dexElements = combineArray(getDexElements(getPathList(pathClassLoader)), getDexElements(getPathList(dexClassLoader)));
//                Object pathList = getPathList(pathClassLoader);
//                return setField(pathList, "dexElements", dexElements);
//            } catch (Throwable e) {
//                e.printStackTrace();
//                return false;
//            }
//        }
//        return false;
//    }
//
//    private static Object combineArray(Object oldDexElements, Object newDexElements) {
//
//        try {
//            Field lengthOldDex = oldDexElements.getClass().getDeclaredField("length");
//            Field lengthNewDex = newDexElements.getClass().getDeclaredField("length");
//
//            int lengthOld = (int) lengthOldDex.get(oldDexElements);
//            int lengthNew = (int) lengthNewDex.get(newDexElements);
//
//            System.arraycopy(lengthNewDex, lengthNew, oldDexElements, lengthOld, lengthNew);
//            return oldDexElements;
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    private static Object getDexElements(Object pathList) {
//
//        try {
//            Field field = pathList.getClass().getDeclaredField("dexElements");
//            field.setAccessible(true);
//            return field.get(pathList);
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//
//    private static Object getPathList(ClassLoader pathClassLoader) {
//
//        try {
//            Field field = pathClassLoader.getClass().getDeclaredField("pathList");
//            field.setAccessible(true);
//            return field.get(pathClassLoader);
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        return null;
//
//    }
//
//    private static boolean setField(Object pathList, String fieldName, Object dexElements) {
//
//        if (pathList == null) {
//            return false;
//        }
//
//        try {
//            Field field = pathList.getClass().getDeclaredField(fieldName);
//            field.setAccessible(true);
//            field.set(pathList, dexElements);
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//            return false;
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//            return false;
//        }
//        return true;
//    }
//
//
//    public static Field findField(Object object, String fieldName) {
//        Field field = null;
//        try {
//            field = object.getClass().getDeclaredField(fieldName);
//            field.setAccessible(true);
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        }
//        return field;
//    }

//    private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
//                                File optimizedDirectory)
//            throws IllegalArgumentException, IllegalAccessException,
//            NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
//
//        Field pathListField = MultiDex.findField(loader, "pathList");
//        Object dexPathList = pathListField.get(loader);
//        ArrayList<IOException> suppressedExceptions = new ArrayList<>();
//        MultiDex.expandFieldArray(dexPathList, "dexElements", MultiDex.V14.makeDexElements(dexPathList,
//                new ArrayList<>(additionalClassPathEntries), optimizedDirectory,
//                suppressedExceptions));
//        try {
//            if (suppressedExceptions.size() > 0) {
//                for (IOException e : suppressedExceptions) {
//                    Log.e(TAG, "Exception in makeDexElement", e);
//                }
//                Field suppressedExceptionsField =
//                        findField(loader, "dexElementsSuppressedExceptions");
//                IOException[] dexElementsSuppressedExceptions =
//                        (IOException[]) suppressedExceptionsField.get(loader);
//
//                if (dexElementsSuppressedExceptions == null) {
//                    dexElementsSuppressedExceptions =
//                            suppressedExceptions.toArray(
//                                    new IOException[suppressedExceptions.size()]);
//                } else {
//                    IOException[] combined =
//                            new IOException[suppressedExceptions.size() +
//                                    dexElementsSuppressedExceptions.length];
//                    suppressedExceptions.toArray(combined);
//                    System.arraycopy(dexElementsSuppressedExceptions, 0, combined,
//                            suppressedExceptions.size(), dexElementsSuppressedExceptions.length);
//                    dexElementsSuppressedExceptions = combined;
//                }
//
//                suppressedExceptionsField.set(loader, dexElementsSuppressedExceptions);
//            }
//        } catch (Exception e) {
//        }
//    }

    public static boolean isDexAvailable() {
        return isDexAvailable;
    }
}