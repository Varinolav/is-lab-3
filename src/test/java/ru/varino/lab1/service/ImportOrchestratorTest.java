package ru.varino.lab1.service;

import jakarta.transaction.RollbackException;
import org.junit.jupiter.api.Test;
import ru.varino.lab1.entity.Movie;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ImportOrchestratorTest {
    private static final byte[] CONTENT = {1, 2};

    @Test
    void successfulImportPassesFileAndDoesNotRecordFailure() throws Exception {
        var failures = new AtomicInteger();
        var orchestrator = orchestrator(new ImportService() {
            @Override
            public int importMovies(List<Movie> movies, String username, String fileName, byte[] content) {
                assertEquals("alice", username);
                assertEquals("movies.json", fileName);
                assertSame(CONTENT, content);
                return 2;
            }
        }, new HistoryService() {
            @Override
            public void saveFailed(String username) { failures.incrementAndGet(); }
        });
        assertEquals(2, orchestrator.initiateImport(List.of(), "alice", "movies.json", CONTENT));
        assertEquals(0, failures.get());
    }

    @Test
    void serviceFailureIsRecordedAndRethrown() throws Exception {
        var failure = new RuntimeException("Ошибка фиксации транзакции");
        var failures = new AtomicInteger();
        var orchestrator = orchestrator(failingImport(failure), new HistoryService() {
            @Override
            public void saveFailed(String username) {
                assertEquals("alice", username);
                failures.incrementAndGet();
            }
        });
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> orchestrator.initiateImport(List.of(), "alice", "movies.json", CONTENT)));
        assertEquals(1, failures.get());
    }

    @Test
    void xaRollbackIsRecordedAndRethrown() throws Exception {
        var rollback = new RollbackException("S3 недоступно");
        var failures = new AtomicInteger();
        var orchestrator = orchestrator(new ImportService() {
            @Override
            public int importMovies(List<Movie> movies, String username, String fileName, byte[] content)
                    throws RollbackException {
                throw rollback;
            }
        }, new HistoryService() {
            @Override
            public void saveFailed(String username) { failures.incrementAndGet(); }
        });
        assertSame(rollback, assertThrows(RollbackException.class,
                () -> orchestrator.initiateImport(List.of(), "alice", "movies.json", CONTENT)));
        assertEquals(1, failures.get());
    }

    @Test
    void historyFailurePropagatesToCaller() throws Exception {
        var original = new RuntimeException("Ошибка импорта");
        var historyFailure = new RuntimeException("История недоступна");
        var orchestrator = orchestrator(failingImport(original), new HistoryService() {
            @Override
            public void saveFailed(String username) { throw historyFailure; }
        });
        assertSame(historyFailure, assertThrows(RuntimeException.class,
                () -> orchestrator.initiateImport(List.of(), "alice", "movies.json", CONTENT)));
    }

    private ImportService failingImport(RuntimeException failure) {
        return new ImportService() {
            @Override
            public int importMovies(List<Movie> movies, String username, String fileName, byte[] content) {
                throw failure;
            }
        };
    }

    private ImportOrchestrator orchestrator(ImportService service, HistoryService history) throws Exception {
        var orchestrator = new ImportOrchestrator();
        var imports = ImportOrchestrator.class.getDeclaredField("importService");
        imports.setAccessible(true);
        imports.set(orchestrator, service);
        var histories = ImportOrchestrator.class.getDeclaredField("historyService");
        histories.setAccessible(true);
        histories.set(orchestrator, history);
        return orchestrator;
    }
}
