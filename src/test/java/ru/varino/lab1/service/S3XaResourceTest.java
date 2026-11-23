package ru.varino.lab1.service;

import org.junit.jupiter.api.Test;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class S3XaResourceTest {
    private static final Xid XID = new Xid() {
        public int getFormatId() { return 17; }
        public byte[] getGlobalTransactionId() { return new byte[]{1, 2}; }
        public byte[] getBranchQualifier() { return new byte[]{3, 4}; }
    };

    @Test
    void commitKeepsTheFileUploadedDuringPrepare() throws XAException {
        var storage = new MemoryStorage();
        var resource = newBranch(storage);
        assertEquals(XAResource.XA_OK, resource.prepare(XID));
        resource.commit(XID, false);
        assertArrayEquals(new byte[]{9}, storage.objects.get("movies"));
        assertEquals(0, resource.recover(XAResource.TMSTARTRSCAN).length);
    }

    @Test
    void onePhaseCommitUploadsTheFile() throws XAException {
        var storage = new MemoryStorage();
        newBranch(storage).commit(XID, true);
        assertArrayEquals(new byte[]{9}, storage.objects.get("movies"));
    }

    @Test
    void rollbackDeletesPreparedFile() throws XAException {
        var storage = new MemoryStorage();
        var resource = newBranch(storage);
        resource.prepare(XID);
        resource.rollback(XID);
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void rollbackDeletesFileEvenIfUploadReplyIsLost() throws XAException {
        MemoryStorage storage = new MemoryStorage() {
            @Override
            void save(String key, byte[] content) {
                super.save(key, content);
                throw new IllegalStateException("No response from S3");
            }
        };
        var resource = newBranch(storage);
        assertEquals(XAException.XAER_RMFAIL,
                assertThrows(XAException.class, () -> resource.prepare(XID)).errorCode);

        resource.rollback(XID);
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void rollbackReportsStorageFailure() throws XAException {
        MemoryStorage storage = new MemoryStorage() {
            private boolean offline = true;

            @Override
            void delete(String key) {
                if (offline) {
                    offline = false;
                    throw new IllegalStateException("S3 is unavailable");
                }
                super.delete(key);
            }
        };
        var resource = newBranch(storage);
        resource.prepare(XID);

        assertEquals(XAException.XAER_RMFAIL,
                assertThrows(XAException.class, () -> resource.rollback(XID)).errorCode);
        resource.rollback(XID);
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void rollbackBeforePrepareDoesNotTouchStorage() throws XAException {
        var storage = new MemoryStorage() {
            @Override
            void save(String key, byte[] content) { fail("Unexpected write"); }

            @Override
            void delete(String key) { fail("Unexpected delete"); }
        };
        var resource = new S3XaResource(storage, "movies", new byte[]{9});
        resource.start(XID, XAResource.TMNOFLAGS);
        resource.end(XID, XAResource.TMFAIL);
        resource.rollback(XID);
    }

    private S3XaResource newBranch(MemoryStorage storage) throws XAException {
        var resource = new S3XaResource(storage, "movies", new byte[]{9});
        resource.start(XID, XAResource.TMNOFLAGS);
        resource.end(XID, XAResource.TMSUCCESS);
        return resource;
    }

    private static class MemoryStorage extends ImportFileStorage {
        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        void save(String key, byte[] content) { objects.put(key, content); }

        @Override
        void delete(String key) { objects.remove(key); }
    }
}
