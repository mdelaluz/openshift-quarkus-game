package io.github.psehgaft.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/game")
@Produces(MediaType.APPLICATION_JSON)
public class GameResource {
    private final List<Score> scores = new ArrayList<>();

    public record Info(String name, String message, int boardSize, List<String> controls) {}
    public record Score(String player, int points) {}

    @GET
    public Info info() {
        return new Info("Quarkus Snake", "¡Come las frutas y evita chocar!", 20,
                List.of("Flechas o WASD: mover", "Espacio: pausa", "R: reiniciar"));
    }

    @GET
    @Path("/scores")
    public synchronized List<Score> scores() {
        return List.copyOf(scores);
    }

    @POST
    @Path("/scores")
    @Consumes(MediaType.APPLICATION_JSON)
    public synchronized Response save(Score score) {
        if (score == null || score.player() == null || score.player().isBlank()
                || score.player().strip().length() > 20 || score.points() < 0 || score.points() > 400) {
            throw new WebApplicationException("Nombre (1-20 caracteres) y puntos (0-400) requeridos", 400);
        }
        Score accepted = new Score(score.player().strip(), score.points());
        scores.add(accepted);
        scores.sort(Comparator.comparingInt(Score::points).reversed());
        if (scores.size() > 10) scores.subList(10, scores.size()).clear();
        return Response.status(Response.Status.CREATED).entity(accepted).build();
    }
}
