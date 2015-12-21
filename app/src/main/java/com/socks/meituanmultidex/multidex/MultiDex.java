package com.socks.meituanmultidex.multidex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;

public final class MultiDex {
    static final String TAG = "MultiDex";
    private static final String OLD_SECONDARY_FOLDER_NAME = "secondary-dexes";
    private static final String SECONDARY_FOLDER_NAME;
    private static final int MAX_SUPPORTED_SDK_VERSION = 20;
    private static final int MIN_SDK_VERSION = 4;
    private static final int VM_WITH_MULTIDEX_VERSION_MAJOR = 2;
    private static final int VM_WITH_MULTIDEX_VERSION_MINOR = 1;
    private static final Set<String> installedApk;
    private static final boolean IS_VM_MULTIDEX_CAPABLE;

    static {
        SECONDARY_FOLDER_NAME = "code_cache" + File.separator + "secondary-dexes";
        installedApk = new HashSet<>();
        IS_VM_MULTIDEX_CAPABLE = isVMMultidexCapable(System.getProperty("java.vm.version"));
    }

    public static void install(final Context context) {
        Log.i("MultiDex", "install");
        if (MultiDex.IS_VM_MULTIDEX_CAPABLE) {
            Log.i("MultiDex", "VM has multidex support, MultiDex support library is disabled.");
            return;
        }
        if (Build.VERSION.SDK_INT < 4) {
            throw new RuntimeException("Multi dex installation failed. SDK " + Build.VERSION.SDK_INT + " is unsupported. Min SDK version is " + 4 + ".");
        }
        try {
            final ApplicationInfo applicationInfo = getApplicationInfo(context);
            if (applicationInfo == null) {
                return;
            }
            synchronized (MultiDex.installedApk) {
                final String apkPath = applicationInfo.sourceDir;
                if (MultiDex.installedApk.contains(apkPath)) {
                    return;
                }
                MultiDex.installedApk.add(apkPath);
                if (Build.VERSION.SDK_INT > 20) {
                    Log.w("MultiDex", "MultiDex is not guaranteed to work in SDK version " + Build.VERSION.SDK_INT + ": SDK version higher than " + 20 + " should be backed by " + "runtime with built-in multidex capabilty but it's not the " + "case here: java.vm.version=\"" + System.getProperty("java.vm.version") + "\"");
                }
                ClassLoader loader;
                try {
                    loader = context.getClassLoader();
                } catch (RuntimeException e) {
                    Log.w("MultiDex", "Failure while trying to obtain Context class loader. Must be running in test mode. Skip patching.", (Throwable) e);
                    return;
                }
                if (loader == null) {
                    Log.e("MultiDex", "Context class loader is null. Must be running in test mode. Skip patching.");
                    return;
                }
                try {
                    clearOldDexDir(context);
                } catch (Throwable t) {
                    Log.w("MultiDex", "Something went wrong when trying to clear old MultiDex extraction, continuing without cleaning.", t);
                }
                final File dexDir = new File(applicationInfo.dataDir, MultiDex.SECONDARY_FOLDER_NAME);
                List<File> files = MultiDexExtractor.load(context, applicationInfo, dexDir, false);
                if (checkValidZipFiles(files)) {
                    installSecondaryDexes(loader, dexDir, files);
                } else {
                    Log.w("MultiDex", "Files were not valid zip files.  Forcing a reload.");
                    files = MultiDexExtractor.load(context, applicationInfo, dexDir, true);
                    if (!checkValidZipFiles(files)) {
                        throw new RuntimeException("Zip files were not valid.");
                    }
                    installSecondaryDexes(loader, dexDir, files);
                }
            }
        } catch (Exception e2) {
            Log.e("MultiDex", "Multidex installation failure", (Throwable) e2);
            throw new RuntimeException("Multi dex installation failed (" + e2.getMessage() + ").");
        }
        Log.i("MultiDex", "install done");
    }

    public static ApplicationInfo getApplicationInfo(final Context context) throws PackageManager.NameNotFoundException {
        PackageManager pm;
        String packageName;
        try {
            pm = context.getPackageManager();
            packageName = context.getPackageName();
        } catch (RuntimeException e) {
            Log.w("MultiDex", "Failure while trying to obtain ApplicationInfo from Context. Must be running in test mode. Skip patching.", (Throwable) e);
            return null;
        }
        if (pm == null || packageName == null) {
            return null;
        }
        final ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 128);
        return applicationInfo;
    }

    static boolean isVMMultidexCapable(final String versionString) {
        boolean isMultidexCapable = false;
        if (versionString != null) {
            final Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?").matcher(versionString);
            if (matcher.matches()) {
                try {
                    final int major = Integer.parseInt(matcher.group(1));
                    final int minor = Integer.parseInt(matcher.group(2));
                    isMultidexCapable = (major > 2 || (major == 2 && minor >= 1));
                } catch (NumberFormatException ex) {
                }
            }
        }
        Log.i("MultiDex", "VM with version " + versionString + (isMultidexCapable ? " has multidex support" : " does not have multidex support"));
        return isMultidexCapable;
    }

    private static void installSecondaryDexes(final ClassLoader loader, final File dexDir, final List<File> files) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
        if (!files.isEmpty()) {
            if (Build.VERSION.SDK_INT >= 19) {
                V19.install(loader, files, dexDir);
            } else if (Build.VERSION.SDK_INT >= 14) {
                V14.install(loader, files, dexDir);
            } else {
                V4.install(loader, files);
            }
        }
    }

    public static boolean checkValidZipFiles(final List<File> files) {
        for (final File file : files) {
            if (!MultiDexExtractor.verifyZipFile(file)) {
                return false;
            }
        }
        return true;
    }

    public static Field findField(final Object instance, final String name) throws NoSuchFieldException {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(name);
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
                continue;
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    public static Method findMethod(final Object instance, final String name, final Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                final Method method = clazz.getDeclaredMethod(name, parameterTypes);
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                return method;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
                continue;
            }
        }
        throw new NoSuchMethodException("Method " + name + " with parameters " + Arrays.asList(parameterTypes) + " not found in " + instance.getClass());
    }

    public static void expandFieldArray(final Object instance, final String fieldName, final Object[] extraElements) throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        final Field jlrField = findField(instance, fieldName);
        final Object[] original = (Object[]) jlrField.get(instance);
        final Object[] combined = (Object[]) Array.newInstance(original.getClass().getComponentType(), original.length + extraElements.length);
        System.arraycopy(original, 0, combined, 0, original.length);
        System.arraycopy(extraElements, 0, combined, original.length, extraElements.length);
        jlrField.set(instance, combined);
    }

    private static void clearOldDexDir(final Context context) throws Exception {
        final File dexDir = new File(context.getFilesDir(), "secondary-dexes");
        if (dexDir.isDirectory()) {
            Log.i("MultiDex", "Clearing old secondary dex dir (" + dexDir.getPath() + ").");
            final File[] files = dexDir.listFiles();
            if (files == null) {
                Log.w("MultiDex", "Failed to list secondary dex dir content (" + dexDir.getPath() + ").");
                return;
            }
            for (final File oldFile : files) {
                Log.i("MultiDex", "Trying to delete old file " + oldFile.getPath() + " of size " + oldFile.length());
                if (!oldFile.delete()) {
                    Log.w("MultiDex", "Failed to delete old file " + oldFile.getPath());
                } else {
                    Log.i("MultiDex", "Deleted old file " + oldFile.getPath());
                }
            }
            if (!dexDir.delete()) {
                Log.w("MultiDex", "Failed to delete secondary dex dir " + dexDir.getPath());
            } else {
                Log.i("MultiDex", "Deleted old secondary dex dir " + dexDir.getPath());
            }
        }
    }

    public static final class V19 {
        public static void install(final ClassLoader loader, final List<File> additionalClassPathEntries, final File optimizedDirectory) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            final Field pathListField = findField(loader, "pathList");
            final Object dexPathList = pathListField.get(loader);
            final ArrayList<IOException> suppressedExceptions = new ArrayList<>();
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList, new ArrayList<>(additionalClassPathEntries), optimizedDirectory, suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                for (final IOException e : suppressedExceptions) {
                    Log.w("MultiDex", "Exception in makeDexElement", e);
                }
                final Field suppressedExceptionsField = findField(loader, "dexElementsSuppressedExceptions");
                IOException[] dexElementsSuppressedExceptions = (IOException[]) suppressedExceptionsField.get(loader);
                if (dexElementsSuppressedExceptions == null) {
                    dexElementsSuppressedExceptions = suppressedExceptions.toArray(new IOException[suppressedExceptions.size()]);
                } else {
                    final IOException[] combined = new IOException[suppressedExceptions.size() + dexElementsSuppressedExceptions.length];
                    suppressedExceptions.toArray(combined);
                    System.arraycopy(dexElementsSuppressedExceptions, 0, combined, suppressedExceptions.size(), dexElementsSuppressedExceptions.length);
                    dexElementsSuppressedExceptions = combined;
                }
                suppressedExceptionsField.set(loader, dexElementsSuppressedExceptions);
            }
        }

        public static Object[] makeDexElements(final Object dexPathList, final ArrayList<File> files, final File optimizedDirectory, final ArrayList<IOException> suppressedExceptions) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            final Method makeDexElements = findMethod(dexPathList, "makeDexElements", (Class<?>[]) new Class[]{ArrayList.class, File.class, ArrayList.class});
            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
        }
    }

    public static final class V14 {
        public static void install(final ClassLoader loader, final List<File> additionalClassPathEntries, final File optimizedDirectory) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            final Field pathListField = findField(loader, "pathList");
            final Object dexPathList = pathListField.get(loader);
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList, new ArrayList<>(additionalClassPathEntries), optimizedDirectory));
        }

        public static Object[] makeDexElements(final Object dexPathList, final ArrayList<File> files, final File optimizedDirectory) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            final Method makeDexElements = findMethod(dexPathList, "makeDexElements", (Class<?>[]) new Class[]{ArrayList.class, File.class});
            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
        }
    }

    public static final class V4 {
        public static void install(final ClassLoader loader, final List<File> additionalClassPathEntries) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, IOException {
            final int extraSize = additionalClassPathEntries.size();
            final Field pathField = findField(loader, "path");
            final StringBuilder path = new StringBuilder((String) pathField.get(loader));
            final String[] extraPaths = new String[extraSize];
            final File[] extraFiles = new File[extraSize];
            final ZipFile[] extraZips = new ZipFile[extraSize];
            final DexFile[] extraDexs = new DexFile[extraSize];
            final ListIterator<File> iterator = additionalClassPathEntries.listIterator();
            while (iterator.hasNext()) {
                final File additionalEntry = iterator.next();
                final String entryPath = additionalEntry.getAbsolutePath();
                path.append(':').append(entryPath);
                final int index = iterator.previousIndex();
                extraPaths[index] = entryPath;
                extraFiles[index] = additionalEntry;
                extraZips[index] = new ZipFile(additionalEntry);
                extraDexs[index] = DexFile.loadDex(entryPath, entryPath + ".dex", 0);
            }
            pathField.set(loader, path.toString());
            expandFieldArray(loader, "mPaths", extraPaths);
            expandFieldArray(loader, "mFiles", extraFiles);
            expandFieldArray(loader, "mZips", extraZips);
            expandFieldArray(loader, "mDexs", extraDexs);
        }
    }
}
