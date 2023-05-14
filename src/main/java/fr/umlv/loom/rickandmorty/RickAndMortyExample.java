package fr.umlv.loom.rickandmorty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.umlv.loom.structured.AsyncScope;
import jdk.incubator.concurrent.StructuredTaskScope;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

public class RickAndMortyExample {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Episode(String name, Set<URI> characters) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Character(String name) {}

    private static Set<URI> characterOfEpisode(int episodeId) throws IOException {
        try(var httpClient = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://rickandmortyapi.com/api/episode/" + episodeId))
                    .GET()
                    .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                throw new IOException(e);
                //throw (InterruptedIOException) new InterruptedIOException().initCause(e);
            }

            var objectMapper = new ObjectMapper();
            var episode = objectMapper.readValue(response.body(), Episode.class);
            return episode.characters();
        }
    }

    private static Character character(URI uri) throws IOException {
        try(var httpClient = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

            var objectMapper = new ObjectMapper();
            return objectMapper.readValue(response.body(), Character.class);
        }
    }

    private static void time(Callable<?> callable) {
        Object result;
        var start = System.currentTimeMillis();
        try {
            result = callable.call();
        } catch(Exception e) {
            throw new RuntimeException(e);
        } finally {
            var end = System.currentTimeMillis();
            System.err.println("time: " + (end - start) + " ms");
        }
        System.out.println(result);
    }

    public static Set<Character> synchronous() throws IOException {
        var character1 = characterOfEpisode(1).stream()
                        .map(uri -> {
                            try {
                                return character(uri);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .collect(toSet());
        var character2 = characterOfEpisode(2).stream()
                .map(uri -> {
                    try {
                        return character(uri);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(toSet());
        var commonCharacters = new HashSet<>(character1);
        commonCharacters.retainAll(character2);
        return commonCharacters;
    }

    public static Set<Character> synchronous2() throws IOException {
        var characterURIs1 = characterOfEpisode(1);
        var characterURIs2 = characterOfEpisode(2);
        var commonCharacterUris = new HashSet<>(characterURIs1);
        commonCharacterUris.retainAll(characterURIs2);
        return commonCharacterUris.stream()
                .map(uri -> {
                    try {
                        return character(uri);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(toSet());
    }

    public static Set<Character> executors() throws IOException, InterruptedException, ExecutionException {
        Set<URI> characterURIs1, characterURIs2;
        try(var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future1 = executor.submit(() -> characterOfEpisode(1));
            var future2 = executor.submit(() -> characterOfEpisode(2));
            characterURIs1 = future1.get();
            characterURIs2 = future2.get();
        }
        var commonCharacterURIs = new HashSet<>(characterURIs1);
        commonCharacterURIs.retainAll(characterURIs2);
        try(var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = commonCharacterURIs.stream()
                    .<Callable<Character>>map(uri -> () -> character(uri))
                    .toList();
            var futures = executor.invokeAll(tasks);
            return futures.stream().
                    map(future -> {
                        try {
                            return future.get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(toSet());
        }
    }

    public static Set<Character> sts() throws InterruptedException {
        Set<URI> characterURIs1, characterURIs2;
        try(var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var future1 = scope.fork(() -> characterOfEpisode(1));
            var future2 = scope.fork(() -> characterOfEpisode(2));
            scope.join();
            characterURIs1 = future1.resultNow();
            characterURIs2 = future2.resultNow();
        }
        var commonCharacterURIs = new HashSet<>(characterURIs1);
        commonCharacterURIs.retainAll(characterURIs2);
        try(var scope = new StructuredTaskScope.ShutdownOnFailure()) {
              var futures = commonCharacterURIs.stream()
                      .map(characterURI -> scope.fork(() -> character(characterURI)))
                      .toList();
              scope.join();
              return futures.stream()
                      .map(Future::resultNow)
                      .collect(toSet());
        }
    }

    public static Set<Character> asyncScope() throws IOException, InterruptedException {
        Set<URI> characterURIs1, characterURIs2;
        try(var scope = new AsyncScope<Set<URI>, IOException>()) {
            var task1 = scope.fork(() -> characterOfEpisode(1));
            var task2 = scope.fork(() -> characterOfEpisode(2));
            var errorOpt = scope.join(stream -> stream.filter(AsyncScope.Result::isFailed).findFirst());
            if (errorOpt.isPresent()) {
                throw errorOpt.orElseThrow().failure();
            }
            characterURIs1 = task1.getNow();
            characterURIs2 = task2.getNow();
        }
        var commonCharacterURIs = new HashSet<>(characterURIs1);
        commonCharacterURIs.retainAll(characterURIs2);
        try(var scope = new AsyncScope<Character, IOException>()) {
            for(var characterURI: commonCharacterURIs) {
                scope.fork(() -> character(characterURI));
            }
            return scope.join(stream -> stream
                    .peek(r -> {
                        if (r.isFailed()) {
                            throw new UncheckedIOException(r.failure());
                        }
                    })
                    .map(AsyncScope.Result::result)
                    .collect(Collectors.toSet()));
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        time(() -> synchronous());
        time(() -> synchronous2());
        time(() -> executors());
        time(() -> sts());
        time(() -> asyncScope());
    }
}
