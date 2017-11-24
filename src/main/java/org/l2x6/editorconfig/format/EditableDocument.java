package org.l2x6.editorconfig.format;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.l2x6.editorconfig.core.FormatException;
import org.l2x6.editorconfig.core.LineReader;
import org.l2x6.editorconfig.core.Resource;

public class EditableDocument extends Resource implements CharSequence {

    private static final Pattern EOL_MATCHER = Pattern.compile("$", Pattern.MULTILINE);

    private int hashCodeLoaded;

    StringBuilder text;

    /**
     * Primarily testing only.
     *
     * @param file
     * @param encoding
     * @param text
     */
    public EditableDocument(Path file, Charset encoding, String text) {
        super(file, encoding);
        this.text = new StringBuilder(text);
        this.hashCodeLoaded = text.hashCode();
    }

    public EditableDocument(Path file, Charset encoding) {
        super(file, encoding);
    }

    public String asString() {
        ensureReadSilent();
        return text.toString();
    }

    public boolean changed() {
        final int len = text.length();
        int hash = 0;
        for (int i = 0; i < len; i++) {
            // h = 31 * h + val[i];
            hash = 31 * hash + text.charAt(i);
        }
        return hash != this.hashCodeLoaded;
    }

    @Override
    public char charAt(int index) {
        ensureReadSilent();
        return text.charAt(index);
    }

    public void delete(int start, int end) {
        ensureReadSilent();
        text.delete(start, end);
    }

    private void ensureRead() throws IOException {
        if (text == null) {
            Reader r = null;
            try {
                r = super.openReader();
                int hash = 0;
                StringBuilder sb = new StringBuilder(256);
                char[] cbuf = new char[1024];
                int len;
                while ((len = r.read(cbuf)) >= 0) {
                    sb.append(cbuf, 0, len);
                    for (int i = 0; i < len; i++) {
                        hash = 31 * hash + cbuf[i];
                    }
                }
                this.text = sb;
                this.hashCodeLoaded = hash;
            } finally {
                if (r != null) {
                    r.close();
                }
            }
        }
    }

    private void ensureReadSilent() {
        try {
            ensureRead();
        } catch (IOException e) {
            throw new FormatException("Could not read " + file, e);
        }
    }

    public int findLineStart(int lineNumber) {
        ensureReadSilent();
        if (lineNumber == 1) {
            return 0;
        } else {
            int currentLine = 2;
            Matcher m = EOL_MATCHER.matcher(text);
            while (m.find()) {
                if (currentLine == lineNumber) {
                    int end = m.end();
                    final int len = text.length();
                    if (end < len) {
                        switch (text.charAt(end)) {
                            case '\n':
                                return end + 1;
                            case '\r':
                                end++;
                                if (end < len && text.charAt(end) == '\n') {
                                    return end + 1;
                                } else {
                                    return end;
                                }
                        }
                    }
                    return end;
                }
                if (currentLine > lineNumber) {
                    throw new IndexOutOfBoundsException("No such line " + lineNumber);
                }
                currentLine++;
            }
            throw new IndexOutOfBoundsException("No such line " + lineNumber);
        }
    }

    public void insert(int offset, CharSequence s) {
        ensureReadSilent();
        text.insert(offset, s);
    }

    @Override
    public int length() {
        ensureReadSilent();
        return text.length();
    }

    @Override
    public Reader openReader() throws IOException {
        ensureRead();
        return LineReader.of(text);
    }

    public void replace(int start, int end, String str) {
        ensureReadSilent();
        text.replace(start, end, str);
    }

    public void store() throws IOException {
        Writer w = null;
        try {
            w = Files.newBufferedWriter(file, encoding);
            char[] cbuf = new char[1024];
            int len = text.length();
            int i = 0;
            while (i < len) {
                final int srcEnd = Math.min(len, i + cbuf.length);
                text.getChars(i, srcEnd, cbuf, 0);
                w.write(cbuf, 0, srcEnd - i);
                i += srcEnd - i;
            }
        } finally {
            if (w != null) {
                w.close();
            }
        }

    }

    @Override
    public CharSequence subSequence(int start, int end) {
        ensureReadSilent();
        return text.subSequence(start, end);
    }

}
