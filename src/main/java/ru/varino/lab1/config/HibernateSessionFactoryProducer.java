package ru.varino.lab1.config;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.apache.commons.dbcp2.managed.BasicManagedDataSource;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import ru.varino.lab1.entity.MovieNameRegistry;

import java.sql.SQLException;

import static org.hibernate.cfg.JdbcSettings.DATASOURCE;

@ApplicationScoped
public class HibernateSessionFactoryProducer {
    @Resource(lookup = "java:/TransactionManager")
    private TransactionManager transactionManager;

    @Produces
    @ApplicationScoped
    public SessionFactory sessionFactory(BasicManagedDataSource pool) {
        Configuration configuration = new Configuration().configure()
                .addAnnotatedClass(MovieNameRegistry.class)
                .addResource("hibernate/Coordinates.hbm.xml")
                .addResource("hibernate/Location.hbm.xml")
                .addResource("hibernate/Person.hbm.xml")
                .addResource("hibernate/Movie.hbm.xml")
                .addResource("hibernate/AppUser.hbm.xml")
                .addResource("hibernate/ImportOperation.hbm.xml");
        configuration.getProperties().put(DATASOURCE, pool);
        return configuration.buildSessionFactory();
    }

    @Produces
    @Dependent
    BasicManagedDataSource connectionPool(TransactionSynchronizationRegistry transactionRegistry) {
        var pool = new BasicManagedDataSource();
        pool.setTransactionManager(transactionManager);
        pool.setTransactionSynchronizationRegistry(transactionRegistry);
        pool.setDriverClassName("org.postgresql.Driver");
        var environment = System.getenv();
        pool.setUrl("jdbc:postgresql://%s:%s/%s".formatted(
                environment.getOrDefault("POSTGRES_HOST", "postgres"),
                environment.getOrDefault("POSTGRES_PORT", "5432"),
                environment.getOrDefault("POSTGRES_DB", "postgres")));
        pool.setUsername(environment.getOrDefault("POSTGRES_USER", "admin"));
        pool.setPassword(environment.getOrDefault("POSTGRES_PASSWORD", "admin"));
        return pool;
    }

    void closeSessionFactory(@Disposes SessionFactory sessionFactory) {
        sessionFactory.close();
    }

    void closeConnectionPool(@Disposes BasicManagedDataSource pool) throws SQLException {
        pool.close();
    }
}
