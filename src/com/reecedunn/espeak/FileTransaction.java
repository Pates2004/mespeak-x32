package com.reecedunn.espeak;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** Recoverable replacement of related private files/directories on one filesystem. */
final class FileTransaction {
    interface Checkpoint { void reached(String phase) throws IOException; }
    private final File root;
    private final File[] allowedRoots;
    private final Checkpoint checkpoint;
    private File[] targets;
    private boolean[] hadOriginal;

    FileTransaction(File root, File... allowedRoots) {
        this(root, null, allowedRoots);
    }

    FileTransaction(File root, Checkpoint checkpoint, File... allowedRoots) {
        this.root = root;
        this.allowedRoots = allowedRoots;
        this.checkpoint = checkpoint;
    }

    boolean isPending() { return root.exists(); }

    void begin(File... replacements) throws IOException {
        recover();
        mkdir(root);
        targets = replacements.clone();
        hadOriginal = new boolean[targets.length];
        for (int i = 0; i < targets.length; ++i) {
            validate(targets[i]);
            hadOriginal[i] = targets[i].exists();
        }
    }

    File staged(int index) { return new File(root, "new" + index); }
    private File backup(int index) { return new File(root, "old" + index); }
    private File marker(String name) { return new File(root, name); }

    void commit() throws IOException {
        final Properties manifest = new Properties();
        manifest.setProperty("count", Integer.toString(targets.length));
        for (int i = 0; i < targets.length; ++i) {
            if (!staged(i).exists()) throw new IOException("Missing staged replacement");
            manifest.setProperty("target" + i, targets[i].getCanonicalPath());
            manifest.setProperty("original" + i, Boolean.toString(hadOriginal[i]));
        }
        try (FileOutputStream output = new FileOutputStream(marker("manifest"))) {
            manifest.store(output, null);
            output.getFD().sync();
        }
        durableMarker("ready");
        try {
            signal("ready");
            for (int i = 0; i < targets.length; ++i) {
                if (hadOriginal[i]) rename(targets[i], backup(i));
                signal("backup:" + i);
                rename(staged(i), targets[i]);
                signal("activate:" + i);
            }
            durableMarker("committed");
            signal("committed");
        } catch (IOException failure) {
            try { recover(); } catch (IOException recovery) { failure.addSuppressed(recovery); }
            throw failure;
        }
        // Publication succeeded. Failed cleanup remains journalled for startup;
        // it must never cause a committed update to be rolled back.
        try { finishCommitted(); } catch (IOException ignored) { }
    }

    /** Returns true if an interrupted publication needed recovery. */
    boolean recover() throws IOException {
        if (!root.exists()) return false;
        final boolean committed = marker("committed").exists();
        final boolean ready = marker("ready").exists();
        if (!ready && !committed) {
            // No publication starts until ready is durable.
            final File[] entries = root.listFiles();
            if (entries == null) throw new IOException("Cannot inspect prepared update");
            for (File entry : entries) {
                if (entry.getName().startsWith("old")) {
                    throw new IOException("Unresolved update backup");
                }
            }
            discard(root);
            return false;
        }
        if (marker("manifest").isFile()) readManifest();
        else if (!committed) throw new IOException("Missing update manifest");
        if (committed) {
            finishCommitted();
            return true;
        }
        for (int i = targets.length - 1; i >= 0; --i) {
            if (backup(i).exists()) {
                // The old copy remains safe until the replacement is removed.
                discard(targets[i]);
                rename(backup(i), targets[i]);
            } else if (!hadOriginal[i]) {
                discard(targets[i]);
            } else if (!targets[i].exists()) {
                throw new IOException("Missing original update target");
            }
        }
        cleanup(false);
        return true;
    }

    void abortPrepared() throws IOException {
        recover();
    }

    private void readManifest() throws IOException {
        final Properties manifest = new Properties();
        try (FileInputStream input = new FileInputStream(marker("manifest"))) {
            manifest.load(input);
        }
        final int count;
        try { count = Integer.parseInt(manifest.getProperty("count", "0")); }
        catch (NumberFormatException e) { throw new IOException("Invalid update manifest", e); }
        if (count < 1 || count > 2) throw new IOException("Invalid update target count");
        targets = new File[count];
        hadOriginal = new boolean[count];
        for (int i = 0; i < count; ++i) {
            final String path = manifest.getProperty("target" + i);
            if (path == null) throw new IOException("Missing update target");
            targets[i] = new File(path);
            validate(targets[i]);
            hadOriginal[i] = Boolean.parseBoolean(manifest.getProperty("original" + i));
        }
    }

    private void finishCommitted() throws IOException {
        if (targets != null) {
            for (File target : targets) {
                if (!target.exists()) throw new IOException("Missing committed update target");
            }
        }
        cleanup(true);
    }

    private void cleanup(boolean committed) throws IOException {
        // Backups disappear before the commit marker. If cleanup is interrupted,
        // startup will still know that the new files won the transaction.
        final File[] entries = root.listFiles();
        if (entries == null) throw new IOException("Cannot inspect update journal");
        for (File entry : entries) {
            final String name = entry.getName();
            if (name.startsWith("old") || name.startsWith("new") || name.endsWith(".tmp")) discard(entry);
        }
        discard(marker("ready"));
        discard(marker("manifest"));
        if (committed) discard(marker("committed"));
        discard(root);
    }

    private void validate(File file) throws IOException {
        final String path = file.getCanonicalPath();
        for (File allowed : allowedRoots) {
            final String base = allowed.getCanonicalPath();
            if (path.equals(base) || path.startsWith(base + File.separator)) return;
        }
        throw new IOException("Unexpected private update target");
    }

    private void durableMarker(String name) throws IOException {
        final File temporary = marker(name + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(1);
            output.getFD().sync();
        }
        rename(temporary, marker(name));
    }

    private void rename(File source, File target) throws IOException {
        signal("rename:" + source.getName() + ":" + target.getName());
        move(source, target);
    }

    private void discard(File file) throws IOException {
        signal("remove:" + file.getName());
        remove(file);
    }

    private void signal(String phase) throws IOException {
        if (checkpoint != null) checkpoint.reached(phase);
    }

    static void mkdir(File file) throws IOException {
        if (!file.isDirectory() && !file.mkdirs()) throw new IOException("Cannot create directory");
    }

    static void move(File source, File target) throws IOException {
        mkdir(target.getParentFile());
        if (target.exists() || !source.renameTo(target)) throw new IOException("Cannot publish update");
    }

    static void remove(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot inspect directory");
            for (File child : children) remove(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("Cannot remove update file");
    }
}
