package ru.varino.lab1.service;

import org.jboss.tm.XAResourceWrapper;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

final class S3XaResource implements XAResourceWrapper {
    private final ImportFileStorage storage;
    private final String fileKey;
    private final byte[] content;
    private Xid currentXid;
    private boolean uploadAttempted;

    S3XaResource(ImportFileStorage storage, String fileKey, byte[] content) {
        this.storage = storage;
        this.fileKey = fileKey;
        this.content = content;
    }

    @Override
    public void start(Xid xid, int flags) throws XAException {
        if (flags == TMNOFLAGS && currentXid == null && xid != null) {
            currentXid = xid;
            return;
        }
        if ((flags == TMJOIN || flags == TMRESUME) && currentXid != null && currentXid.equals(xid)) {
            return;
        }
        throw new XAException(XAException.XAER_PROTO);
    }

    @Override
    public void end(Xid xid, int flags) throws XAException {
        verifyBranch(xid);
    }

    @Override
    public int prepare(Xid xid) throws XAException {
        verifyBranch(xid);
        uploadFile();
        return XA_OK;
    }

    @Override
    public void commit(Xid xid, boolean onePhase) throws XAException {
        verifyBranch(xid);
        if (onePhase) {
            uploadFile();
        }
    }

    @Override
    public void rollback(Xid xid) throws XAException {
        verifyBranch(xid);
        if (uploadAttempted) {
            inStorage(() -> storage.delete(fileKey));
            uploadAttempted = false;
        }
    }

    private void uploadFile() throws XAException {
        uploadAttempted = true;
        inStorage(() -> storage.save(fileKey, content));
    }

    @Override
    public Xid[] recover(int flag) {
        return new Xid[0];
    }

    @Override
    public void forget(Xid xid) throws XAException {
        throw new XAException(XAException.XAER_NOTA);
    }

    @Override
    public boolean isSameRM(XAResource other) {
        return this == other;
    }

    @Override
    public int getTransactionTimeout() {
        return 0;
    }

    @Override
    public boolean setTransactionTimeout(int seconds) {
        return false;
    }

    @Override
    public XAResource getResource() {
        return this;
    }

    @Override
    public String getProductName() {
        return "S3";
    }

    @Override
    public String getProductVersion() {
        return "1";
    }

    @Override
    public String getJndiName() {
        return "java:/Lab3ImportFiles";
    }

    private void verifyBranch(Xid xid) throws XAException {
        if (currentXid == null || !currentXid.equals(xid)) {
            throw new XAException(XAException.XAER_NOTA);
        }
    }

    private static void inStorage(Runnable operation) throws XAException {
        try {
            operation.run();
        } catch (RuntimeException e) {
            var failure = new XAException(XAException.XAER_RMFAIL);
            failure.initCause(e);
            throw failure;
        }
    }
}
