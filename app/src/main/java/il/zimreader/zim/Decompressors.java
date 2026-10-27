package il.zimreader.zim;

import com.github.luben.zstd.ZstdInputStream;

import io.airlift.compress.zstd.ZstdDecompressor;

import org.tukaani.xz.LZMA2InputStream;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * פענוח אשכולות בזיכרון מינימלי (מכשירים ישנים עם ערימה קטנה):
 * - הקצאה מדויקת של הפלט (גודל האשכול נקרא מטבלת ה-offsets שבתחילתו, בלי כפל זיכרון).
 * - xz: דילוג על מעטפת ה-xz ופענוח LZMA2 עם מילון קטן (קבצים שנדחסו עם מילון של 64MB לא יפילו את המכשיר).
 * - zstd: קודם הספרייה הנייטיבית, ואם היא לא נטענת במכשיר - מפענח Java טהור.
 */
final class Decompressors {
    private Decompressors() {}

    private static boolean nativeBroken;
    private static final int MAX_CLUSTER = 192 * 1024 * 1024;
    private static final int SMALL_DICT = 4 * 1024 * 1024;

    // ---------- xz ----------

    static byte[] xz(byte[] packed, boolean ext) throws IOException {
        int dict = lzma2DictSize(packed);
        if (dict > SMALL_DICT) {
            // קודם מילון קטן; אם האשכול גדול ממנו והפענוח נכשל - מילון מלא
            try {
                return readExact(rawLzma2(packed, SMALL_DICT), ext);
            } catch (IOException e) {
                // ממשיכים למילון המלא
            } catch (RuntimeException e) {
                // כנ"ל
            }
        }
        InputStream in = dict > 0 ? rawLzma2(packed, dict) : new XZInputStream(new ByteArrayInputStream(packed));
        return readExact(in, ext);
    }

    /** גודל המילון מכותרת בלוק ה-xz הראשון; 0 אם לא ניתן לנתח (אז משתמשים בקורא xz הרגיל). */
    private static int lzma2DictSize(byte[] p) {
        try {
            if (p.length < 20 || (p[0] & 0xFF) != 0xFD || p[1] != 0x37 || p[2] != 0x7A) return 0;
            int hs = ((p[12] & 0xFF) + 1) * 4;
            int flags = p[13] & 0xFF;
            if ((flags & 0x03) != 0) return 0; // יותר ממסנן אחד
            int i = 14;
            if ((flags & 0x40) != 0) i = skipVarint(p, i);
            if ((flags & 0x80) != 0) i = skipVarint(p, i);
            if ((p[i] & 0xFF) != 0x21) return 0; // לא LZMA2
            i++;
            if ((p[i] & 0xFF) != 1) return 0;
            i++;
            int prop = p[i] & 0xFF;
            if (prop > 40) return 0;
            if (prop == 40) return Integer.MAX_VALUE;
            return (2 | (prop & 1)) << (prop / 2 + 11);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int skipVarint(byte[] p, int i) {
        while ((p[i] & 0x80) != 0) i++;
        return i + 1;
    }

    private static InputStream rawLzma2(byte[] packed, int dict) throws IOException {
        int hs = ((packed[12] & 0xFF) + 1) * 4;
        int off = 12 + hs;
        int d = Math.max(4096, Math.min(dict, 1 << 30));
        return new LZMA2InputStream(new ByteArrayInputStream(packed, off, packed.length - off), d);
    }

    // ---------- zstd ----------

    static byte[] zstd(byte[] packed, boolean ext) throws IOException {
        if (!nativeBroken) {
            try {
                return readExact(new ZstdInputStream(new ByteArrayInputStream(packed)), ext);
            } catch (LinkageError err) {
                nativeBroken = true;
            }
        }
        return zstdPureJava(packed);
    }

    private static byte[] zstdPureJava(byte[] packed) throws IOException {
        ZstdDecompressor d = new ZstdDecompressor();
        int cap = Math.max(1 << 20, packed.length * 6);
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                byte[] out = new byte[cap];
                int n = d.decompress(packed, 0, packed.length, out, 0, out.length);
                if (n == out.length) return out;
                byte[] r = new byte[n];
                System.arraycopy(out, 0, r, 0, n);
                return r;
            } catch (RuntimeException ex) {
                cap *= 2;
            }
        }
        throw new IOException("zstd decompress failed");
    }

    // ---------- קריאה מדויקת ----------

    /**
     * אשכול דחוס מתחיל בטבלת offsets (4 או 8 בתים לכל רשומה), והאחרון בה הוא הגודל הכולל.
     * קוראים את הטבלה, מקצים בדיוק את הגודל הנדרש וממלאים.
     */
    private static byte[] readExact(InputStream in, boolean ext) throws IOException {
        try {
            int osz = ext ? 8 : 4;
            byte[] first = new byte[osz];
            readFully(in, first, 0, osz);
            long firstOff = le(first, osz);
            if (firstOff < osz || firstOff % osz != 0 || firstOff > 64L * 1024 * 1024) throw new IOException("bad cluster");
            byte[] table = new byte[(int) firstOff];
            System.arraycopy(first, 0, table, 0, osz);
            readFully(in, table, osz, table.length - osz);
            long total = le(table, osz, table.length - osz);
            if (total < firstOff || total > MAX_CLUSTER) throw new IOException("bad cluster size");
            byte[] data = new byte[(int) total];
            System.arraycopy(table, 0, data, 0, table.length);
            readFully(in, data, table.length, data.length - table.length);
            return data;
        } finally {
            in.close();
        }
    }

    private static long le(byte[] b, int osz) {
        return le(b, osz, 0);
    }

    private static long le(byte[] b, int osz, int at) {
        long v = 0;
        for (int i = osz - 1; i >= 0; i--) v = (v << 8) | (b[at + i] & 0xFF);
        return v;
    }

    private static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(b, off, len);
            if (n < 0) throw new IOException("unexpected end of cluster");
            off += n;
            len -= n;
        }
    }
}
