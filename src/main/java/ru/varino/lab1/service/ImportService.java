package ru.varino.lab1.service;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import ru.varino.lab1.entity.Movie;
import ru.varino.lab1.entity.Person;
import ru.varino.lab1.exception.ImportValidationException;
import ru.varino.lab1.interceptor.LogL2CacheStatistics;
import ru.varino.lab1.repository.MovieRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@LogL2CacheStatistics
@Transactional
public class ImportService {
    @Inject
    private Validator validator;

    @Inject
    private MovieRepository movieRepository;

    @Inject
    private HistoryService historyService;

    @Inject
    private ImportFileStorage fileStorage;

    @Inject
    private MovieNameService movieNames;

    public int importMovies(List<Movie> movies, String username, String fileName, byte[] content)
            throws RollbackException {
        if (movies == null || movies.isEmpty()) {
            throw new ImportValidationException("Файл должен содержать непустой JSON-массив фильмов.");
        }
        for (int i = 0; i < movies.size(); i++) {
            Movie movie = movies.get(i);
            String prefix = "movies[" + i + "]";
            if (movie == null) {
                throw new ImportValidationException(prefix + ": запись фильма не должна быть null.");
            }
            var violations = validator.validate(movie);
            if (!violations.isEmpty()) {
                String errors = violations.stream()
                        .map(v -> prefix + "." + v.getPropertyPath() + ": " + v.getMessage())
                        .sorted().collect(Collectors.joining("; "));
                throw new ImportValidationException(errors);
            }
            if (movie.getId() != null || movie.getCoordinates().getId() != null
                    || hasIds(movie.getDirector()) || hasIds(movie.getScreenwriter())
                    || hasIds(movie.getOperator())) {
                throw new ImportValidationException(prefix + ": поля id у фильма и вложенных объектов должны отсутствовать.");
            }
        }
        movieRepository.lockMovieNames();
        movieNames.check(movies, null);
        for (Movie movie : movies) {
            movieRepository.saveImported(movie);
        }
        String fileKey = UUID.randomUUID().toString();
        historyService.saveSuccess(username, movies.size(), fileKey, fileName);
        fileStorage.saveInTransaction(fileKey, content);
        return movies.size();
    }


    private boolean hasIds(Person person) {
        return person != null && (person.getId() != null || person.getLocation().getId() != null);
    }

}
