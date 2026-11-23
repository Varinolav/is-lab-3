package ru.varino.lab1.resource;

import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.hibernate.SessionFactory;

@Path("/cache-statistics")
@Produces(MediaType.APPLICATION_JSON)
public class CacheStatisticsResource {
    @Inject
    private SessionFactory sessionFactory;

    @Inject
    private SecurityContext security;

    public record Settings(@NotNull Boolean enabled) {}

    @GET
    public Settings getSettings() {
        requireAdmin();
        return new Settings(sessionFactory.getStatistics().isStatisticsEnabled());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Settings setSettings(@NotNull @Valid Settings settings) {
        requireAdmin();
        sessionFactory.getStatistics().setStatisticsEnabled(settings.enabled());
        return settings;
    }

    private void requireAdmin() {
        if (!security.isCallerInRole("ADMIN")) {
            throw new ForbiddenException();
        }
    }
}
