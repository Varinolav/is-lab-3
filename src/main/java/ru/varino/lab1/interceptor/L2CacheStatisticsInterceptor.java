package ru.varino.lab1.interceptor;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.hibernate.SessionFactory;

import java.util.logging.Logger;

@Interceptor
@LogL2CacheStatistics
@Priority(Interceptor.Priority.APPLICATION)
public class L2CacheStatisticsInterceptor {
    private static final Logger LOG = Logger.getLogger(L2CacheStatisticsInterceptor.class.getName());

    @Inject
    private SessionFactory sessionFactory;

    @AroundInvoke
    Object logStatistics(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } finally {
            var statistics = sessionFactory.getStatistics();
            if (statistics.isStatisticsEnabled()) {
                LOG.info(() -> "L2 статистика кеша: hits=%d, misses=%d".formatted(
                        statistics.getSecondLevelCacheHitCount(), statistics.getSecondLevelCacheMissCount()));
            }
        }
    }
}
