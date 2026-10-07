package com.reecedunn.espeak;

import java.util.concurrent.atomic.AtomicLong;

/** Coordination for the process-global core and the data files it reads. */
final class CoreState {
    // Data writers acquire CheckVoiceData's monitor before this lock. Core work
    // must never acquire that monitor while holding LOCK. Metadata reads keep
    // their separate initialization lock and do not wait for spoken audio.
    static final Object LOCK = new Object();
    static final AtomicLong revision = new AtomicLong();
    // Accessed only under LOCK; the revision also detects a concurrent stop.
    static String selectedVoice;
    static long selectedRevision = -1;

    private CoreState() { }

    static void invalidateVoice() {
        // Stop is called from another thread while synthesis owns LOCK.
        revision.incrementAndGet();
    }
}
