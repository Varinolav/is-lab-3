package ru.varino.lab1.resource;

import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.transaction.RollbackException;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Providers;
import ru.varino.lab1.entity.Movie;
import ru.varino.lab1.service.HistoryService;
import ru.varino.lab1.service.ImportOrchestrator;
import ru.varino.lab1.service.MovieService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Path("/movies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MovieResource {

    @Inject
    private MovieService movieService;

    @Inject
    private ChangeEvents events;

    @Inject
    private ImportOrchestrator importOrchestrator;

    @Inject
    private HistoryService historyService;

    @Inject
    private SecurityContext security;

    @Context
    private Providers providers;

    @POST
    public Response create(@Valid Movie movie) {
        movieService.save(movie);
        events.publish();

        return Response.status(Response.Status.CREATED)
                .entity(movie)
                .build();
    }

    @GET
    public Response getMovies() {
        List<Movie> movies = movieService.findAll();
        return Response.ok(movies).build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importMovies(@FormParam("file") EntityPart file) throws IOException, RollbackException {
        String username = security.getCallerPrincipal().getName();
        if (file == null) {
            historyService.saveFailed(username);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Прикрепите JSON-файл в поле file."))
                    .build();
        }
        byte[] content;
        try (var input = file.getContent()) {
            content = input.readAllBytes();
        }
        List<Movie> movies;
        try {
            Movie[] parsed = readMovies(file, content);
            movies = parsed == null ? null : Arrays.asList(parsed);
        } catch (IOException | RuntimeException e) {
            historyService.saveFailed(username);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Не удалось прочитать файл. Файл должен содержать корректный JSON-массив фильмов."))
                    .build();
        }
        String name = file.getFileName().filter(value -> !value.isBlank()).orElse("import.json");
        int importedCount = importOrchestrator.initiateImport(movies, username, name, content);
        events.publish();
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("importedCount", importedCount))
                .build();
    }

    private Movie[] readMovies(EntityPart file, byte[] content) throws IOException {
        var annotations = new Annotation[0];
        var reader = providers.getMessageBodyReader(Movie[].class, Movie[].class, annotations, file.getMediaType());
        if (reader == null) {
            throw new BadRequestException();
        }
        return reader.readFrom(Movie[].class, Movie[].class, annotations, file.getMediaType(), file.getHeaders(),
                new ByteArrayInputStream(content));
    }

    @GET
    @Path("/{id}")
    public Response getMovie(@PathParam("id") Integer id) {
        Movie movie = movieService.findById(id);

        if (movie == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(movie).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Integer id, @Valid Movie movie) {
        Movie updatedMovie = movieService.update(id, movie);
        if (updatedMovie == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        events.publish();
        return Response.ok(updatedMovie).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Integer id) {
        if (!movieService.deleteById(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        events.publish();
        return Response.noContent().build();
    }
}
