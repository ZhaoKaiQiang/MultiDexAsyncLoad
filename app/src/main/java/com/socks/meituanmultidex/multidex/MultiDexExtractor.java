package com.socks.meituanmultidex.multidex;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class MultiDexExtractor
{
    private static final String TAG = "MultiDex";
    private static final String DEX_PREFIX = "classes";
    private static final String DEX_SUFFIX = ".dex";
    private static final String EXTRACTED_NAME_EXT = ".classes";
    private static final String EXTRACTED_SUFFIX = ".zip";
    private static final int MAX_EXTRACT_ATTEMPTS = 3;
    private static final String PREFS_FILE = "multidex.version";
    private static final String KEY_TIME_STAMP = "timestamp";
    private static final String KEY_CRC = "crc";
    private static final String KEY_DEX_NUMBER = "dex.number";
    private static final int BUFFER_SIZE = 16384;
    private static final long NO_VALUE = -1L;
    private static Method sApplyMethod;
    
    static List<File> load(final Context context, final ApplicationInfo applicationInfo, final File dexDir, final boolean forceReload) throws IOException {
        Log.i("MultiDex", "MultiDexExtractor.load(" + applicationInfo.sourceDir + ", " + forceReload + ")");
        final File sourceApk = new File(applicationInfo.sourceDir);
        final long currentCrc = getZipCrc(sourceApk);
        List<File> files;
        if (!forceReload && !isModified(context, sourceApk, currentCrc)) {
            try {
                files = loadExistingExtractions(context, sourceApk, dexDir);
            }
            catch (IOException ioe) {
                Log.w("MultiDex", "Failed to reload existing extracted secondary dex files, falling back to fresh extraction", (Throwable)ioe);
                files = performExtractions(sourceApk, dexDir);
                putStoredApkInfo(context, getTimeStamp(sourceApk), currentCrc, files.size() + 1);
            }
        }
        else {
            Log.i("MultiDex", "Detected that extraction must be performed.");
            files = performExtractions(sourceApk, dexDir);
            putStoredApkInfo(context, getTimeStamp(sourceApk), currentCrc, files.size() + 1);
        }
        Log.i("MultiDex", "load found " + files.size() + " secondary dex files");
        return files;
    }
    
    private static List<File> loadExistingExtractions(final Context context, final File sourceApk, final File dexDir) throws IOException {
        Log.i("MultiDex", "loading existing secondary dex files");
        final String extractedFilePrefix = sourceApk.getName() + ".classes";
        final int totalDexNumber = getMultiDexPreferences(context).getInt("dex.number", 1);
        final List<File> files = new ArrayList<File>(totalDexNumber);
        for (int secondaryNumber = 2; secondaryNumber <= totalDexNumber; ++secondaryNumber) {
            final String fileName = extractedFilePrefix + secondaryNumber + ".zip";
            final File extractedFile = new File(dexDir, fileName);
            if (!extractedFile.isFile()) {
                throw new IOException("Missing extracted secondary dex file '" + extractedFile.getPath() + "'");
            }
            files.add(extractedFile);
            if (!verifyZipFile(extractedFile)) {
                Log.i("MultiDex", "Invalid zip file: " + extractedFile);
                throw new IOException("Invalid ZIP file.");
            }
        }
        return files;
    }
    
    private static boolean isModified(final Context context, final File archive, final long currentCrc) {
        final SharedPreferences prefs = getMultiDexPreferences(context);
        return prefs.getLong("timestamp", -1L) != getTimeStamp(archive) || prefs.getLong("crc", -1L) != currentCrc;
    }
    
    private static long getTimeStamp(final File archive) {
        long timeStamp = archive.lastModified();
        if (timeStamp == -1L) {
            --timeStamp;
        }
        return timeStamp;
    }
    
    private static long getZipCrc(final File archive) throws IOException {
        long computedValue = ZipUtil.getZipCrc(archive);
        if (computedValue == -1L) {
            --computedValue;
        }
        return computedValue;
    }
    
    private static List<File> performExtractions(final File sourceApk, final File dexDir) throws IOException {
        final String extractedFilePrefix = sourceApk.getName() + ".classes";
        prepareDexDir(dexDir, extractedFilePrefix);
        final List<File> files = new ArrayList<File>();
        final ZipFile apk = new ZipFile(sourceApk);
        try {
            int secondaryNumber = 2;
            for (ZipEntry dexFile = apk.getEntry("classes" + secondaryNumber + ".dex"); dexFile != null; dexFile = apk.getEntry("classes" + secondaryNumber + ".dex")) {
                final String fileName = extractedFilePrefix + secondaryNumber + ".zip";
                final File extractedFile = new File(dexDir, fileName);
                files.add(extractedFile);
                Log.i("MultiDex", "Extraction is needed for file " + extractedFile);
                int numAttempts = 0;
                boolean isExtractionSuccessful = false;
                while (numAttempts < 3 && !isExtractionSuccessful) {
                    ++numAttempts;
                    extract(apk, dexFile, extractedFile, extractedFilePrefix);
                    isExtractionSuccessful = verifyZipFile(extractedFile);
                    Log.i("MultiDex", "Extraction " + (isExtractionSuccessful ? "success" : "failed") + " - length " + extractedFile.getAbsolutePath() + ": " + extractedFile.length());
                    if (!isExtractionSuccessful) {
                        extractedFile.delete();
                        if (!extractedFile.exists()) {
                            continue;
                        }
                        Log.w("MultiDex", "Failed to delete corrupted secondary dex '" + extractedFile.getPath() + "'");
                    }
                }
                if (!isExtractionSuccessful) {
                    throw new IOException("Could not create zip file " + extractedFile.getAbsolutePath() + " for secondary dex (" + secondaryNumber + ")");
                }
                ++secondaryNumber;
            }
        }
        finally {
            try {
                apk.close();
            }
            catch (IOException e) {
                Log.w("MultiDex", "Failed to close resource", (Throwable)e);
            }
        }
        return files;
    }
    
    private static void putStoredApkInfo(final Context context, final long timeStamp, final long crc, final int totalDexNumber) {
        final SharedPreferences prefs = getMultiDexPreferences(context);
        final SharedPreferences.Editor edit = prefs.edit();
        edit.putLong("timestamp", timeStamp);
        edit.putLong("crc", crc);
        edit.putInt("dex.number", totalDexNumber);
        apply(edit);
    }
    
    private static SharedPreferences getMultiDexPreferences(final Context context) {
        return context.getSharedPreferences("multidex.version", (Build.VERSION.SDK_INT < 11) ? 0 : 4);
    }
    
    private static void prepareDexDir(final File dexDir, final String extractedFilePrefix) throws IOException {
        final File cache = dexDir.getParentFile();
        mkdirChecked(cache);
        mkdirChecked(dexDir);
        final FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return !pathname.getName().startsWith(extractedFilePrefix);
            }
        };
        final File[] files = dexDir.listFiles(filter);
        if (files == null) {
            Log.w("MultiDex", "Failed to list secondary dex dir content (" + dexDir.getPath() + ").");
            return;
        }
        for (final File oldFile : files) {
            Log.i("MultiDex", "Trying to delete old file " + oldFile.getPath() + " of size " + oldFile.length());
            if (!oldFile.delete()) {
                Log.w("MultiDex", "Failed to delete old file " + oldFile.getPath());
            }
            else {
                Log.i("MultiDex", "Deleted old file " + oldFile.getPath());
            }
        }
    }
    
    private static void mkdirChecked(final File dir) throws IOException {
        dir.mkdir();
        if (!dir.isDirectory()) {
            final File parent = dir.getParentFile();
            if (parent == null) {
                Log.e("MultiDex", "Failed to create dir " + dir.getPath() + ". Parent file is null.");
            }
            else {
                Log.e("MultiDex", "Failed to create dir " + dir.getPath() + ". parent file is a dir " + parent.isDirectory() + ", a file " + parent.isFile() + ", exists " + parent.exists() + ", readable " + parent.canRead() + ", writable " + parent.canWrite());
            }
            throw new IOException("Failed to create cache directory " + dir.getPath());
        }
    }
    
    private static void extract(final ZipFile apk, final ZipEntry dexFile, final File extractTo, final String extractedFilePrefix) throws IOException, FileNotFoundException {
        final InputStream in = apk.getInputStream(dexFile);
        ZipOutputStream out = null;
        final File tmp = File.createTempFile(extractedFilePrefix, ".zip", extractTo.getParentFile());
        Log.i("MultiDex", "Extracting " + tmp.getPath());
        try {
            out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
            try {
                final ZipEntry classesDex = new ZipEntry("classes.dex");
                classesDex.setTime(dexFile.getTime());
                out.putNextEntry(classesDex);
                final byte[] buffer = new byte[16384];
                for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                    out.write(buffer, 0, length);
                }
                out.closeEntry();
            }
            finally {
                out.close();
            }
            Log.i("MultiDex", "Renaming to " + extractTo.getPath());
            if (!tmp.renameTo(extractTo)) {
                throw new IOException("Failed to rename \"" + tmp.getAbsolutePath() + "\" to \"" + extractTo.getAbsolutePath() + "\"");
            }
        }
        finally {
            closeQuietly(in);
            tmp.delete();
        }
    }
    
    static boolean verifyZipFile(final File file) {
        try {
            final ZipFile zipFile = new ZipFile(file);
            try {
                zipFile.close();
                return true;
            }
            catch (IOException e) {
                Log.w("MultiDex", "Failed to close zip file: " + file.getAbsolutePath());
            }
        }
        catch (ZipException ex) {
            Log.w("MultiDex", "File " + file.getAbsolutePath() + " is not a valid zip file.", (Throwable)ex);
        }
        catch (IOException ex2) {
            Log.w("MultiDex", "Got an IOException trying to open zip file: " + file.getAbsolutePath(), (Throwable)ex2);
        }
        return false;
    }
    
    private static void closeQuietly(final Closeable closeable) {
        try {
            closeable.close();
        }
        catch (IOException e) {
            Log.w("MultiDex", "Failed to close resource", (Throwable)e);
        }
    }
    
    private static void apply(final SharedPreferences.Editor editor) {
        if (MultiDexExtractor.sApplyMethod != null) {
            try {
                MultiDexExtractor.sApplyMethod.invoke(editor, new Object[0]);
                return;
            }
            catch (InvocationTargetException unused) {}
            catch (IllegalAccessException ex) {}
        }
        editor.commit();
    }
    
    static {
        try {
            final Class<?> cls = SharedPreferences.Editor.class;
            MultiDexExtractor.sApplyMethod = cls.getMethod("apply", (Class<?>[])new Class[0]);
        }
        catch (NoSuchMethodException unused) {
            MultiDexExtractor.sApplyMethod = null;
        }
    }
}
