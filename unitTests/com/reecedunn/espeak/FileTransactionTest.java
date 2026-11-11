package com.reecedunn.espeak;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class FileTransactionTest {
    private File base, active, retained, journal;
    private static final String[] INTERRUPTIONS = {
        "ready", "backup:0", "activate:0", "backup:1", "activate:1"
    };
    private static final class InterruptedProcess extends Error { }

    @Before public void setup() throws Exception {
        base = Files.createTempDirectory("mespeak-transaction-").toFile();
        active = new File(base, "active");
        retained = new File(base, "retained");
        journal = new File(base, "journal");
        write(active, "old active");
        write(retained, "old retained");
    }
    @After public void cleanup() throws Exception { FileTransaction.remove(base); }

    private FileTransaction transaction(FileTransaction.Checkpoint callback) {
        return new FileTransaction(journal, callback, active, retained);
    }
    private void prepare(FileTransaction transaction) throws Exception {
        transaction.begin(active, retained);
        write(transaction.staged(0), "new active");
        write(transaction.staged(1), "new retained");
    }
    private static void write(File file, String value) throws Exception {
        FileTransaction.mkdir(file.getParentFile());
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
    private void assertOld() throws Exception {
        assertEquals("old active", read(active));
        assertEquals("old retained", read(retained));
    }

    @Test public void publishesBothCopiesAndRemovesJournal() throws Exception {
        FileTransaction transaction = transaction(null);
        prepare(transaction);
        transaction.commit();
        assertEquals("new active", read(active));
        assertEquals("new retained", read(retained));
        assertFalse(journal.exists());
    }

    @Test public void ordinaryFailuresRollBackEveryPublicationStage() throws Exception {
        for (String interruption : INTERRUPTIONS) {
            FileTransaction transaction = transaction(phase -> {
                if (interruption.equals(phase)) throw new IOException("Injected filesystem failure");
            });
            prepare(transaction);
            try { transaction.commit(); fail(interruption); } catch (IOException expected) { }
            assertOld();
            assertFalse(journal.exists());
        }
    }

    @Test public void startupRollsBackEveryUncommittedInterruption() throws Exception {
        for (String interruption : INTERRUPTIONS) {
            FileTransaction transaction = transaction(phase -> {
                if (interruption.equals(phase)) throw new InterruptedProcess();
            });
            prepare(transaction);
            try { transaction.commit(); fail(interruption); } catch (InterruptedProcess expected) { }
            assertTrue(transaction(null).recover());
            assertOld();
            assertFalse(transaction(null).recover());
        }
    }

    @Test public void startupKeepsCommittedCopies() throws Exception {
        FileTransaction transaction = transaction(phase -> {
            if ("committed".equals(phase)) throw new InterruptedProcess();
        });
        prepare(transaction);
        try { transaction.commit(); fail(); } catch (InterruptedProcess expected) { }
        assertTrue(transaction(null).recover());
        assertEquals("new active", read(active));
        assertEquals("new retained", read(retained));
        assertFalse(journal.exists());
    }

    @Test public void uncommittedFreshInstallRestoresAbsence() throws Exception {
        FileTransaction.remove(active);
        FileTransaction.remove(retained);
        FileTransaction transaction = transaction(phase -> {
            if ("activate:1".equals(phase)) throw new InterruptedProcess();
        });
        prepare(transaction);
        try { transaction.commit(); fail(); } catch (InterruptedProcess expected) { }
        assertTrue(transaction(null).recover());
        assertFalse(active.exists());
        assertFalse(retained.exists());
    }

    @Test public void stagingFailureLeavesOriginalsUntouched() throws Exception {
        FileTransaction transaction = transaction(null);
        transaction.begin(active, retained);
        write(transaction.staged(0), "incomplete stage");
        transaction.abortPrepared();
        assertOld();
        assertFalse(journal.exists());
    }

    private void interruptAfterActivation() throws Exception {
        FileTransaction transaction = transaction(phase -> {
            if ("activate:0".equals(phase)) throw new InterruptedProcess();
        });
        prepare(transaction);
        try { transaction.commit(); fail(); } catch (InterruptedProcess expected) { }
    }

    @Test public void failedRollbackDeleteKeepsBackupAndAllowsRetry() throws Exception {
        interruptAfterActivation();
        try {
            transaction(phase -> {
                if ("remove:active".equals(phase)) throw new IOException("Injected delete failure");
            }).recover();
            fail();
        } catch (IOException expected) { }
        assertEquals("old active", read(new File(journal, "old0")));
        assertTrue(journal.exists());
        transaction(null).recover();
        assertOld();
    }

    @Test public void failedRollbackRenameKeepsOnlyGoodCopyAndAllowsRetry() throws Exception {
        interruptAfterActivation();
        try {
            transaction(phase -> {
                if ("rename:old0:active".equals(phase)) throw new IOException("Injected rename failure");
            }).recover();
            fail();
        } catch (IOException expected) { }
        assertFalse(active.exists());
        assertEquals("old active", read(new File(journal, "old0")));
        transaction(null).recover();
        assertOld();
    }

    @Test public void failedCommittedCleanupDoesNotRollback() throws Exception {
        FileTransaction transaction = transaction(phase -> {
            if ("remove:old0".equals(phase)) throw new IOException("Injected cleanup failure");
        });
        prepare(transaction);
        transaction.commit();
        assertTrue(journal.exists());
        assertEquals("new active", read(active));
        transaction(null).recover();
        assertEquals("new active", read(active));
        assertEquals("new retained", read(retained));
    }

    @Test public void committedCleanupResumesAfterManifestWasRemoved() throws Exception {
        FileTransaction transaction = transaction(phase -> {
            if ("remove:committed".equals(phase)) throw new InterruptedProcess();
        });
        prepare(transaction);
        try { transaction.commit(); fail(); } catch (InterruptedProcess expected) { }
        assertFalse(new File(journal, "manifest").exists());
        assertTrue(new File(journal, "committed").exists());
        transaction(null).recover();
        assertEquals("new active", read(active));
    }

    @Test public void rollbackCleanupCanBeInterruptedAfterRestore() throws Exception {
        interruptAfterActivation();
        try {
            transaction(phase -> {
                if ("remove:manifest".equals(phase)) throw new InterruptedProcess();
            }).recover();
            fail();
        } catch (InterruptedProcess expected) { }
        assertOld();
        transaction(null).recover();
        assertOld();
        assertFalse(journal.exists());
    }

    @Test public void existingVersionMarkerDoesNotOverrideJournalDecision() throws Exception {
        FileTransaction.remove(active);
        FileTransaction.remove(retained);
        write(new File(active, "version"), "old version");
        write(new File(retained, "pl_dict"), "custom");
        FileTransaction transaction = transaction(phase -> {
            if ("activate:1".equals(phase)) throw new InterruptedProcess();
        });
        transaction.begin(active, retained);
        write(new File(transaction.staged(0), "version"), "new complete version");
        FileTransaction.mkdir(transaction.staged(1)); // preservation OFF
        try { transaction.commit(); fail(); } catch (InterruptedProcess expected) { }
        assertEquals("new complete version", read(new File(active, "version")));
        transaction(null).recover();
        assertEquals("old version", read(new File(active, "version")));
        assertEquals("custom", read(new File(retained, "pl_dict")));
    }
}
