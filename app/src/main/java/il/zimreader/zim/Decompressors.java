package il.zimreader.zim;

import com.github.luben.zstd.ZstdInputStream;

import io.airlift.compress.zstd.ZstdDecompressor;

import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** פענוח אשכולות. zstd: קודם הספרייה הנייטיבית, ואם היא לא נטענת במכשיר - מפענח Java טהור. */
final class Decompressors {
    private Decompressors() {}

    private static boolean nativeBroken;

    static byte[] xz(byte[] packed) throws IOException {
        return readAll(new XZInputStream(new ByteArrayInputStream(packed)), packed.length * 4);
    }

    static byte[] zstd(byte[] packed) throws IOException {
        if (!nativeBroken) {
            try {
                return readAll(new ZstdInputStream(new ByteArrayInputStream(packed)), packed.length * 4);
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
                byte[] r = new byte[n];
                System.arraycopy(out, 0, r, 0, n);
                return r;
            } catch (RuntimeException ex) {
                cap *= 2;
            }
        }
        throw new IOException("zstd decompress failed");
    }

    private static byte[] readAll(InputStream in, int hint) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, hint));
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
