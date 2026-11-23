package ru.varino.lab1.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

@ApplicationScoped
public class ImportFileStorage {
    @Resource(lookup = "java:/TransactionManager")
    private TransactionManager transactionManager;

    private S3Client client;
    private String bucket;

    @PostConstruct
    void init() {
        var environment = System.getenv();
        bucket = environment.getOrDefault("S3_BUCKET", "imports");
        client = S3Client.builder()
                .endpointOverride(URI.create(environment.getOrDefault("S3_ENDPOINT", "http://127.0.0.1:8333")))
                .region(Region.US_EAST_1)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30)))
                .forcePathStyle(true)
                .build();
    }

    public void saveInTransaction(String key, byte[] content) {
        var resource = new S3XaResource(this, key, content);
        try {
            if (!transactionManager.getTransaction().enlistResource(resource)) {
                throw new IllegalStateException("Narayana отклонил подключение S3 к транзакции.");
            }
        } catch (RollbackException | SystemException e) {
            throw new IllegalStateException("Не удалось подключить S3 к транзакции.", e);
        }
    }

    void save(String key, byte[] content) {
        client.putObject(request -> request.bucket(bucket).key(key), RequestBody.fromBytes(content));
    }

    public InputStream open(String key) {
        return client.getObject(request -> request.bucket(bucket).key(key));
    }

    void delete(String key) {
        client.deleteObject(request -> request.bucket(bucket).key(key));
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
