package ru.varino.lab1.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.Cache;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.action.internal.BulkOperationCleanupAction;
import org.hibernate.engine.spi.SessionImplementor;
import ru.varino.lab1.entity.Movie;
import ru.varino.lab1.entity.MovieGenre;

import java.util.List;

@ApplicationScoped
public class MovieOperationRepository {

    @Inject
    private SessionFactory sessionFactory;

    private Session session() {
        return sessionFactory.getCurrentSession();
    }

    private Cache getCache() { return sessionFactory.getCache(); }


    public Integer deleteOneByGenre(MovieGenre genre) {
        Integer id = session().createNativeQuery("SELECT lab_delete_one_movie_by_genre(:genre)", Integer.class)
                .setParameter("genre", genre.name())
                .getSingleResult();
        getCache().evictEntityData(Movie.class);
        return id;
    }

    public long sumGoldenPalms() {
        Long sum = session().createNativeQuery("SELECT lab_sum_golden_palms()", Long.class)
                .getSingleResult();
        return sum;
    }

    public long countGenreBefore(MovieGenre genre) {
        Long count = session().createNativeQuery("SELECT lab_count_movies_genre_before(:genre)", Long.class)
                .setParameter("genre", genre.name())
                .getSingleResult();
        return count;
    }

    public List<Movie> moviesWithoutOscars() {
        return session().createNativeQuery("SELECT * FROM lab_movies_without_oscars()", Movie.class)
                .getResultList();
    }

    public int addOscarToRMovies() {
        Session session = session();
        session.flush();
        Integer count = session.createNativeQuery("SELECT lab_add_oscar_to_r_movies()", Integer.class)
                .getSingleResult();
        getCache().evictEntityData(Movie.class);
        return count;
    }
}
