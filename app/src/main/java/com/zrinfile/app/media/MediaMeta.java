package com.zrinfile.app.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 封面与歌词提取，按多种方式依次尝试。 */
public final class MediaMeta {

    public static class LyricLine {
        public final long time;
        public final String text;

        public LyricLine(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    private MediaMeta() {
    }

    /** 依次尝试：系统元数据、ID3 APIC、同目录封面图。 */
    public static Bitmap loadCover(File audio) {
        Bitmap bitmap = coverFromRetriever(audio);
        if (bitmap != null) return bitmap;
        byte[] raw = id3Picture(audio);
        if (raw != null) {
            try {
                bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                if (bitmap != null) return bitmap;
            } catch (Exception ignored) {
            }
        }
        return coverFromFolder(audio);
    }

    private static Bitmap coverFromRetriever(File audio) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(audio.getAbsolutePath());
            byte[] data = retriever.getEmbeddedPicture();
            if (data != null && data.length > 0) {
                return BitmapFactory.decodeByteArray(data, 0, data.length);
            }
        } catch (Exception ignored) {
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static Bitmap coverFromFolder(File audio) {
        File dir = audio.getParentFile();
        if (dir == null) return null;
        String base = audio.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String[] names = {"cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.png",
                "album.jpg", "album.png", "front.jpg", "front.png",
                base + ".jpg", base + ".jpeg", base + ".png"};
        for (String name : names) {
            File f = new File(dir, name);
            if (f.isFile() && f.length() > 0 && f.length() < 12L * 1024 * 1024) {
                Bitmap b = decodeScaled(f);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static Bitmap decodeScaled(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int scale = 1;
            while (o.outWidth / (scale * 2) >= 720 && o.outHeight / (scale * 2) >= 720) scale *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Exception e) {
            return null;
        }
    }

    /** ID3v2 中查找 APIC 图片。 */
    private static byte[] id3Picture(File f) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            byte[] header = new byte[10];
            if (raf.read(header) != 10) return null;
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;
            int version = header[3] & 0xFF;
            int size = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14)
                    | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);
            raf.seek(10);
            byte[] tag = new byte[Math.min(size, 4 * 1024 * 1024)];
            int read = 0;
            while (read < tag.length) {
                int n = raf.read(tag, read, tag.length - read);
                if (n <= 0) break;
                read += n;
            }
            if (read < 10) return null;
            int pos = 0;
            if ((header[5] & 0x40) != 0) {
                // extended header
                int ext = ((tag[0] & 0x7F) << 21) | ((tag[1] & 0x7F) << 14)
                        | ((tag[2] & 0x7F) << 7) | (tag[3] & 0x7F);
                pos = ext + 4;
            }
            while (pos + 10 < read) {
                String id = new String(tag, pos, 4, "ISO-8859-1");
                if (id.charAt(0) < 'A' || id.charAt(0) > 'Z') break;
                int frameSize;
                if (version >= 4) {
                    frameSize = ((tag[pos + 4] & 0x7F) << 21) | ((tag[pos + 5] & 0x7F) << 14)
                            | ((tag[pos + 6] & 0x7F) << 7) | (tag[pos + 7] & 0x7F);
                } else {
                    frameSize = ((tag[pos + 4] & 0xFF) << 24) | ((tag[pos + 5] & 0xFF) << 16)
                            | ((tag[pos + 6] & 0xFF) << 8) | (tag[pos + 7] & 0xFF);
                }
                if (frameSize <= 0 || pos + 10 + frameSize > read) break;
                if ("APIC".equals(id)) {
                    return parseApic(tag, pos + 10, frameSize);
                }
                pos += 10 + frameSize;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (raf != null) raf.close();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static byte[] parseApic(byte[] data, int offset, int length) {
        try {
            int pos = offset;
            int end = offset + length;
            pos++;
            while (pos < end && data[pos] != 0) pos++;
            pos++;
            pos++;
            int enc = data[offset] & 0xFF;
            if (enc == 1 || enc == 2) {
                while (pos + 1 < end && !(data[pos] == 0 && data[pos + 1] == 0)) pos += 2;
                pos += 2;
            } else {
                while (pos < end && data[pos] != 0) pos++;
                pos++;
            }
            if (pos >= end) return null;
            byte[] out = new byte[end - pos];
            System.arraycopy(data, pos, out, 0, out.length);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 优先读取同名 .lrc；没有则尝试 ID3 的 USLT。 */
    public static List<LyricLine> loadLyrics(File audio) {
        File dir = audio.getParentFile();
        if (dir != null) {
            String name = audio.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            File lrc = new File(dir, name + ".lrc");
            if (!lrc.isFile()) lrc = new File(dir, name + ".LRC");
            if (lrc.isFile()) {
                List<LyricLine> lines = parseLrc(readText(lrc));
                if (!lines.isEmpty()) return lines;
            }
        }
        String embedded = id3Lyrics(audio);
        if (embedded != null) {
            List<LyricLine> lines = parseLrc(embedded);
            if (!lines.isEmpty()) return lines;
        }
        return new ArrayList<>();
    }

    private static String readText(File f) {
        try {
            byte[] data = new byte[(int) Math.min(f.length(), 1024 * 1024)];
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            raf.readFully(data);
            raf.close();
            String utf8 = new String(data, "UTF-8");
            if (utf8.indexOf('\uFFFD') < 0) return utf8;
            return new String(data, "GBK");
        } catch (Exception e) {
            return "";
        }
    }

    private static List<LyricLine> parseLrc(String text) {
        List<LyricLine> list = new ArrayList<>();
        if (text == null) return list;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            int close = line.indexOf(']');
            if (line.charAt(0) != '[' || close < 0) continue;
            String timePart = line.substring(1, close);
            String content = line.substring(close + 1).trim();
            String[] pieces = timePart.split(":");
            if (pieces.length < 2) continue;
            try {
                int minutes = Integer.parseInt(pieces[0].trim());
                float seconds = Float.parseFloat(pieces[1].trim());
                long ms = (long) (minutes * 60000 + seconds * 1000);
                if (content.length() > 0) list.add(new LyricLine(ms, content));
            } catch (Exception ignored) {
            }
        }
        java.util.Collections.sort(list, new java.util.Comparator<LyricLine>() {
            @Override
            public int compare(LyricLine a, LyricLine b) {
                return Long.compare(a.time, b.time);
            }
        });
        return list;
    }

    private static String id3Lyrics(File f) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            byte[] header = new byte[10];
            if (raf.read(header) != 10) return null;
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;
            int version = header[3] & 0xFF;
            int size = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14)
                    | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);
            raf.seek(10);
            byte[] tag = new byte[Math.min(size, 2 * 1024 * 1024)];
            int read = 0;
            while (read < tag.length) {
                int n = raf.read(tag, read, tag.length - read);
                if (n <= 0) break;
                read += n;
            }
            int pos = 0;
            while (pos + 10 < read) {
                String id = new String(tag, pos, 4, "ISO-8859-1");
                if (id.charAt(0) < 'A' || id.charAt(0) > 'Z') break;
                int frameSize;
                if (version >= 4) {
                    frameSize = ((tag[pos + 4] & 0x7F) << 21) | ((tag[pos + 5] & 0x7F) << 14)
                            | ((tag[pos + 6] & 0x7F) << 7) | (tag[pos + 7] & 0x7F);
                } else {
                    frameSize = ((tag[pos + 4] & 0xFF) << 24) | ((tag[pos + 5] & 0xFF) << 16)
                            | ((tag[pos + 6] & 0xFF) << 8) | (tag[pos + 7] & 0xFF);
                }
                if (frameSize <= 0 || pos + 10 + frameSize > read) break;
                if ("USLT".equals(id)) {
                    int start = pos + 10;
                    int enc = tag[start] & 0xFF;
                    start += 4;
                    int end = pos + 10 + frameSize;
                    if (enc == 1 || enc == 2) {
                        while (start + 1 < end && !(tag[start] == 0 && tag[start + 1] == 0)) start += 2;
                        start += 2;
                        return new String(tag, start, end - start, "UTF-16LE");
                    }
                    while (start < end && tag[start] != 0) start++;
                    start++;
                    return new String(tag, start, end - start, enc == 0 ? "ISO-8859-1" : "UTF-8");
                }
                pos += 10 + frameSize;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (raf != null) raf.close();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String formatTime(long ms) {
        long total = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }
}
