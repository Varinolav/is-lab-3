package ru.varino.lab1.resource;

import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import ru.varino.lab1.entity.ImportStatus;
import ru.varino.lab1.service.HistoryService;
import ru.varino.lab1.service.ImportFileStorage;

import java.time.LocalDateTime;
import java.util.List;

@Path("/imports")
@Produces(MediaType.APPLICATION_JSON)
public class ImportHistoryResource {
    @Inject
    private HistoryService historyService;

    @Inject
    private ImportFileStorage fileStorage;

    @Inject
    private SecurityContext security;

    public record HistoryEntry(Long id, String username, ImportStatus status, LocalDateTime creationDate,
                               Long count, String fileName) {
    }

    @GET
    public List<HistoryEntry> history() {
        return historyService.findForUser(security.getCallerPrincipal().getName(),
                        security.isCallerInRole("ADMIN"))
                .stream()
                .map(operation -> new HistoryEntry(operation.getId(), operation.getUsername(), operation.getStatus(),
                        operation.getCreationDate(), operation.getCount(),
                        operation.hasDownload() ? operation.getFileName() : null))
                .toList();
    }

    @GET
    @Path("/{id}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("id") Long id) {
        var operation = historyService.findForUser(id, security.getCallerPrincipal().getName(),
                security.isCallerInRole("ADMIN"));
        if (operation == null || !operation.hasDownload()) {
            throw new NotFoundException();
        }

        StreamingOutput content = output -> {
            try (var input = fileStorage.open(operation.getFileKey())) {
                input.transferTo(output);
            }
        };
        return Response.ok(content)
                .header("Content-Disposition", "attachment")
                .header("Cache-Control", "no-store")
                .build();
    }
}
