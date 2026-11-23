package ru.varino.lab1.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.RollbackException;
import ru.varino.lab1.entity.Movie;

import java.util.List;

@ApplicationScoped
public class ImportOrchestrator {
    @Inject
    private ImportService importService;

    @Inject
    private HistoryService historyService;

    public int initiateImport(List<Movie> movies, String username, String fileName, byte[] content)
            throws RollbackException {
        try {
            return importService.importMovies(movies, username, fileName, content);
        } catch (RollbackException | RuntimeException failure) {
            historyService.saveFailed(username);
            throw failure;
        }
    }
}
