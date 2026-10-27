package il.zimreader;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import il.zimreader.zim.ZimFile;

/** רשימת הספרים (קבצי ZIM) שנמצאו באחסון, וקבצים פתוחים. */
public final class Library {
    private Library() {}

    public static final class Book {
        public String path;
        public String name;
        public long size;
    }

    private static final List<Book> books = new ArrayList<Book>();
    private static final Map<String, ZimFile> open = new HashMap<String, ZimFile>();
    private static final Map<String, String> idToPath = new HashMap<String, String>();
    private static final Map<String, String> names = new HashMap<String, String>();

    public static synchronized List<Book> list() {
        return new ArrayList<Book>(books);
    }

    /** טוען את הרשימה השמורה (בלי סריקה), רק קבצים שעדיין קיימים. */
    public static synchronized void setFromCache(List<Book> cached) {
        books.clear();
        for (Book b : cached) {
            if (new File(b.path).exists()) {
                books.add(b);
                names.put(b.path, b.name);
            }
        }
    }

    public static synchronized String idOf(String path) {
        String id = Integer.toHexString(path.hashCode());
        idToPath.put(id, path);
        return id;
    }

    public static synchronized String pathOfId(String id) {
        return idToPath.get(id);
    }

    public static synchronized String nameOf(String path) {
        String n = names.get(path);
        if (n != null) return n;
        String f = new File(path).getName();
        int dot = f.lastIndexOf('.');
        return dot > 0 ? f.substring(0, dot) : f;
    }

    private static Context appCtx;

    public static void init(Context c) {
        appCtx = c.getApplicationContext();
    }

    /** קובץ שנפתח מאפליקציה אחרת (content://) - קריאה ישירה דרך מתאר הקובץ. */
    private static ZimFile openContent(String path) throws IOException {
        if (appCtx == null) throw new IOException("no context");
        final ParcelFileDescriptor pfd = appCtx.getContentResolver().openFileDescriptor(Uri.parse(path), "r");
        if (pfd == null) throw new IOException("cannot open " + path);
        final FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
        ZimFile z = new ZimFile(in.getChannel(), pfd.getStatSize(), new Closeable() {
            @Override
            public void close() throws IOException {
                in.close();
                pfd.close();
            }
        });
        String t = z.metadata("Title");
        if (t != null && t.trim().length() > 0) names.put(path, t.trim());
        return z;
    }

    public static synchronized ZimFile open(String path) throws IOException {
        ZimFile z = open.get(path);
        if (z == null) {
            z = path.startsWith("content://") ? openContent(path) : new ZimFile(new File(path));
            // משאירים עד 3 קבצים פתוחים
            if (open.size() >= 3) {
                String victim = open.keySet().iterator().next();
                try {
                    open.remove(victim).close();
                } catch (IOException ignored) {
                }
            }
            open.put(path, z);
        }
        return z;
    }

    /** סריקה חוסמת - להריץ ב-thread רקע. */
    public static void scan() {
        List<File> roots = new ArrayList<File>();
        roots.add(Environment.getExternalStorageDirectory());
        File storage = new File("/storage");
        File[] kids = storage.listFiles();
        if (kids != null) Collections.addAll(roots, kids);
        roots.add(new File("/mnt"));
        roots.add(new File("/sdcard"));

        Set<String> seenDirs = new HashSet<String>();
        Set<String> seenFiles = new HashSet<String>();
        List<File> found = new ArrayList<File>();
        for (File r : roots) walk(r, 0, seenDirs, seenFiles, found);

        List<Book> result = new ArrayList<Book>();
        for (File f : found) {
            Book b = new Book();
            b.path = f.getAbsolutePath();
            b.size = f.length();
            b.name = titleOf(f);
            result.add(b);
        }
        Collections.sort(result, new Comparator<Book>() {
            @Override
            public int compare(Book a, Book b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        synchronized (Library.class) {
            books.clear();
            books.addAll(result);
            for (Book b : result) names.put(b.path, b.name);
        }
    }

    private static void walk(File dir, int depth, Set<String> seenDirs, Set<String> seenFiles, List<File> out) {
        if (depth > 6 || dir == null) return;
        String canon;
        try {
            canon = dir.getCanonicalPath();
        } catch (IOException e) {
            return;
        }
        if (!seenDirs.add(canon)) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String n = k.getName();
            if (k.isDirectory()) {
                if (n.startsWith(".") || n.equals("Android") || n.equals("proc") || n.equals("sys")) continue;
                walk(k, depth + 1, seenDirs, seenFiles, out);
            } else if (n.toLowerCase().endsWith(".zim") && k.length() > 1000) {
                try {
                    if (seenFiles.add(k.getCanonicalPath()) && hasZimMagic(k)) out.add(k);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean hasZimMagic(File f) {
        RandomAccessFile r = null;
        try {
            r = new RandomAccessFile(f, "r");
            byte[] b = new byte[4];
            r.readFully(b);
            return (b[0] & 0xFF) == 0x5A && (b[1] & 0xFF) == 0x49 && (b[2] & 0xFF) == 0x4D && (b[3] & 0xFF) == 0x04;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (r != null) r.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String titleOf(File f) {
        ZimFile z = null;
        try {
            z = new ZimFile(f);
            String t = z.metadata("Title");
            if (t != null && t.trim().length() > 0) return t.trim();
        } catch (Throwable ignored) {
        } finally {
            if (z != null) {
                try {
                    z.close();
                } catch (IOException ignored) {
                }
            }
        }
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
