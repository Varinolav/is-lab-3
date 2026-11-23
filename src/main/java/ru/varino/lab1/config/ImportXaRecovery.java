package ru.varino.lab1.config;

import com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule;
import com.arjuna.ats.jta.recovery.XAResourceRecoveryHelper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.postgresql.xa.PGXADataSource;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import java.sql.SQLException;
import java.util.concurrent.Callable;

@ApplicationScoped
public class ImportXaRecovery {
    @Inject
    private PGXADataSource postgres;

    private XARecoveryModule recovery;
    private XAResourceRecoveryHelper postgresHelper;
    private XAConnection recoveryConnection;

    public void register(@Observes @Initialized(ApplicationScoped.class) Object event) {
        recovery = XARecoveryModule.getRegisteredXARecoveryModule();
        if (recovery == null) {
            throw new IllegalStateException("Модуль восстановления Narayana XA недоступен.");
        }
        postgresHelper = helper(this::postgresResource);
        recovery.addXAResourceRecoveryHelper(postgresHelper);
    }

    private XAResource postgresResource() throws SQLException {
        if (recoveryConnection != null) {
            XAConnection previous = recoveryConnection;
            recoveryConnection = null;
            previous.close();
        }
        recoveryConnection = postgres.getXAConnection();
        return recoveryConnection.getXAResource();
    }

    @PreDestroy
    void unregister() {
        if (recovery != null) {
            recovery.removeXAResourceRecoveryHelper(postgresHelper);
        }
        if (recoveryConnection != null) {
            try {
                recoveryConnection.close();
            } catch (SQLException e) {
                throw new IllegalStateException("Не удалось закрыть соединение восстановления XA.", e);
            }
        }
    }

    private static XAResourceRecoveryHelper helper(Callable<XAResource> source) {
        return new XAResourceRecoveryHelper() {
            @Override
            public boolean initialise(String properties) {
                return true;
            }

            @Override
            public XAResource[] getXAResources() throws Exception {
                return new XAResource[]{source.call()};
            }
        };
    }
}
