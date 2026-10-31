package il.zimreader.zim;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * קובץ ZIM שפוצל לחלקים (.zimaa, .zimab, ...) - נקרא כקובץ אחד רציף,
 * בלי לאחד אותם בפועל בדיסק.
 */
public final class SplitSource implements Closeable {
    private final List<RandomAccessFile> files = new ArrayList<RandomAccessFile>();
    private final List<FileChannel> parts = new ArrayList<FileChannel>();
    private final long[] starts;
    private final long total;

    public SplitSource(List<File> partFiles) throws IOException {
        starts = new long[partFiles.size()];
        long sum = 0;
        try {
            for (int i = 0; i < partFiles.size(); i++) {
                RandomAccessFile r = new RandomAccessFile(partFiles.get(i), "r");
                files.add(r);
                parts.add(r.getChannel());
                starts[i] = sum;
                sum += r.length();
            }
        } catch (IOException e) {
            close();
            throw e;
        }
        total = sum;
    }

    public long length() {
        return total;
    }

    /** כמו FileChannel.read(dst, position), גם כשהקריאה חוצה גבול בין חלקים. */
    public int read(ByteBuffer dst, long pos) throws IOException {
        if (pos >= total) return -1;
        int i = parts.size() - 1;
        while (i > 0 && starts[i] > pos) i--;
        long local = pos - starts[i];
        long left = (i + 1 < parts.size() ? starts[i + 1] : total) - pos;
        int oldLimit = dst.limit();
        if (dst.remaining() > left) dst.limit(dst.position() + (int) left);
        try {
            return parts.get(i).read(dst, local);
        } finally {
            dst.limit(oldLimit);
        }
    }

    @Override
    public void close() throws IOException {
        for (RandomAccessFile r : files) {
            try {
                r.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---------- זיהוי חלקים ----------

    /** האם השם הוא של החלק הראשון (.zimaa). */
    public static boolean isFirstPart(String name) {
        return name.toLowerCase().endsWith(".zimaa");
    }

    /** כל החלקים ברצף (aa, ab, ac, ...) החל מהחלק הראשון; עוצר בחלק החסר הראשון. */
    public static List<File> findParts(File first) {
        List<File> out = new ArrayList<File>();
        String name = first.getName();
        String base = name.substring(0, name.length() - 2);
        File dir = first.getAbsoluteFile().getParentFile();
        for (char a = 'a'; a <= 'z'; a++) {
            for (char b = 'a'; b <= 'z'; b++) {
                File f = new File(dir, base + a + b);
                if (!f.isFile()) return out;
                out.add(f);
            }
        }
        return out;
    }
}
