package com.socks.meituanmultidex.multidex;

import java.io.*;
import java.util.zip.*;

final class ZipUtil
{
    private static final int ENDHDR = 22;
    private static final int ENDSIG = 101010256;
    private static final int BUFFER_SIZE = 16384;
    
    static long getZipCrc(final File apk) throws IOException {
        final RandomAccessFile raf = new RandomAccessFile(apk, "r");
        try {
            final CentralDirectory dir = findCentralDirectory(raf);
            return computeCrcOfCentralDir(raf, dir);
        }
        finally {
            raf.close();
        }
    }
    
    static CentralDirectory findCentralDirectory(final RandomAccessFile raf) throws IOException, ZipException {
        long scanOffset = raf.length() - 22L;
        if (scanOffset < 0L) {
            throw new ZipException("File too short to be a zip file: " + raf.length());
        }
        long stopOffset = scanOffset - 65536L;
        if (stopOffset < 0L) {
            stopOffset = 0L;
        }
        final int endSig = Integer.reverseBytes(101010256);
        do {
            raf.seek(scanOffset);
            if (raf.readInt() == endSig) {
                raf.skipBytes(2);
                raf.skipBytes(2);
                raf.skipBytes(2);
                raf.skipBytes(2);
                final CentralDirectory dir = new CentralDirectory();
                dir.size = (Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL);
                dir.offset = (Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL);
                return dir;
            }
            --scanOffset;
        } while (scanOffset >= stopOffset);
        throw new ZipException("End Of Central Directory signature not found");
    }
    
    static long computeCrcOfCentralDir(final RandomAccessFile raf, final CentralDirectory dir) throws IOException {
        final CRC32 crc = new CRC32();
        long stillToRead = dir.size;
        raf.seek(dir.offset);
        int length;
        byte[] buffer;
        for (length = (int)Math.min(16384L, stillToRead), buffer = new byte[16384], length = raf.read(buffer, 0, length); length != -1; length = (int)Math.min(16384L, stillToRead), length = raf.read(buffer, 0, length)) {
            crc.update(buffer, 0, length);
            stillToRead -= length;
            if (stillToRead == 0L) {
                break;
            }
        }
        return crc.getValue();
    }
    
    static class CentralDirectory
    {
        long offset;
        long size;
    }
}
