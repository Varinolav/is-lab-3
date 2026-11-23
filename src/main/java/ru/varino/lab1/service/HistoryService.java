package ru.varino.lab1.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.varino.lab1.entity.ImportOperation;
import ru.varino.lab1.entity.ImportStatus;
import ru.varino.lab1.repository.ImportRepository;

import java.time.LocalDateTime;
import java.util.List;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@ApplicationScoped
public class HistoryService {
    @Inject
    private ImportRepository importRepository;

    @Transactional
    public void saveSuccess(String username, long count, String fileKey, String fileName) {
        ImportOperation operation = newOperation(username, ImportStatus.SUCCESS);
        operation.setCount(count);
        operation.setFileKey(fileKey);
        operation.setFileName(fileName);
        importRepository.save(operation);
    }

    @Transactional(REQUIRES_NEW)
    public void saveFailed(String username) {
        importRepository.save(newOperation(username, ImportStatus.FAILED));
    }

    @Transactional
    public List<ImportOperation> findForUser(String username, boolean isAdmin) {
        return isAdmin ? importRepository.findAll() : importRepository.findByUsername(username);
    }

    @Transactional
    public ImportOperation findForUser(Long id, String username, boolean isAdmin) {
        ImportOperation operation = importRepository.findById(id);
        return operation != null && (isAdmin || username.equals(operation.getUsername())) ? operation : null;
    }

    private ImportOperation newOperation(String username, ImportStatus status) {
        ImportOperation operation = new ImportOperation();
        operation.setUsername(username);
        operation.setStatus(status);
        operation.setCreationDate(LocalDateTime.now());
        return operation;
    }

}
