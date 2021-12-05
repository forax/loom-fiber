package fr.umlv.loom.actor.http;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import fr.umlv.loom.actor.Actor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

/**
 * An HTTP server that uses actors to handle HTTP request / response seen as JSON objects/arrays.
 *
 *
 */
public class HttpServer {
  /**
   * HTTP request method.
   *
   * see https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods
   */
  public enum RequestMethod {
    GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH
  }

  /**
   * HTTP response status.
   *
   * see https://developer.mozilla.org/en-US/docs/Web/HTTP/Status
   */
  public enum HttpStatus {
    CONTINUE(100),
    SWITCHING_PROTOCOLS(101),
    EARLY_HINTS(103),

    OK(200),
    CREATED(201),
    ACCEPTED(202),
    NON_AUTTHORITATIVE_INFORMATION(203),
    NO_CONTENT(204),
    RESET_CONTENT(205),
    PARTIAL_CONTENT(206),

    MULTIPLE_CHOICES(300),
    MOVED_PERMANENTLY(301),
    FOUND(302),
    SEE_OTHER(303),
    NOT_MODIFIER(304),
    TEMPORARY_REDIRECT(307),
    PERMANENT_REDIRECT(308),

    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    PAYMENT_REQUIRED(402),
    FORBIDDEN(403),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    NOT_ACCEPTABLE(406),
    PROXY_AUTHENTICATION_REQUIRED(407),
    REQUEST_TIMEOUT(408),
    CONFLICT5(409),
    GONE(410),
    LENGTH_REQUIRED(411),
    PRECONDITION_FAILED(412),
    PAYLOAD_TOO_LARGE(413),
    URI_TOO_LONG(414),
    UNSUPPORTED_MEDIA_TYPE(415),
    RANGE_NOT_SATISFIABLE(416),
    EXPECTATION_FAILED(417),
    I_M_A_TEAPOT(418),
    UNPROCESSABLE_ENTITY(422),
    TOO_EARLY(425),
    UPGRADE_REQUIRED(426),
    PRECONDITION_REQUIRED(428),
    TOO_MANY_REQUESTS(429),
    REQUEST_HEADER_FIELDS_TOO_LARGE(431),
    UNAVAILABLE_FOR_LEGAL_REASONS(451),

    INTERNAL_SERVER_ERROR(500),
    NOT_IMPLEMENTED(501),
    BAD_GATEWAY(502),
    SERVICE_UNAVAILABLE(503),
    GATEWAY_TIMEOUT(504),
    HTTP_VERSION_NOT_SUPPORTED(505),
    VARIANT_ALSO_NEGOTIATES(506),
    INSUFFICIENT_STORAGE(507),
    LOOP_DETECTED(508),
    NOT_EXTENDED(510),
    NETWORK_AUTHENTICATION_REQUIRED(511)
    ;

    private final int code;

    HttpStatus(int code) {
      this.code = code;
    }
  }

  /**
   * The method annotated by this annotation defines a route.
   *
   * The semantics is similar to the Spring Web Annotation with the same name.
   * see https://www.baeldung.com/spring-mvc-annotations
   *
   * @see #routes(Actor)
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface RequestMapping {
    String path();
    RequestMethod method() default RequestMethod.GET;
  }

  /**
   * The parameter annotated by this annotation will be injected with
   * the request body as a JSON object/array.
   *
   * The semantics is similar to the Spring Web Annotation with the same name.
   * see https://www.baeldung.com/spring-mvc-annotations
   *
   * @see #routes(Actor)
   */
  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface RequestBody { }

  /**
   * The parameter annotated by this annotation will be injected with
   * the value captured from the request URI.
   *
   * The semantics is similar to the Spring Web Annotation with the same name.
   * see https://www.baeldung.com/spring-mvc-annotations
   *
   * @see #routes(Actor)
   */
  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface PathVariable {
    String value();
  }

  /**
   * The parameter annotated by this annotation will be injected with
   * the value of the request header or the default value if
   * the header does not exist.
   *
   * The semantics is similar to the Spring Web Annotation with the same name.
   * see https://www.baeldung.com/spring-mvc-annotations
   *
   * @see #routes(Actor)
   */
  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface RequestParam {
    String value();
    String defaultValue() default "";
  }

  /**
   * API of the actor able to send a HTTP response.
   *
   * @param <T> type of the result, can be java.lang.Void if the value is null.
   */
  public interface Response<T> {
    /**
     * Send a response with a HTTP status and a result as a JSON object.
     * @param status the response status
     * @param result an object that will be converted to JSON or null
     */
    void response(HttpStatus status, T result);

    /**
     * Send a response with a HTTP status, some header key/value pairs and a result as a JSON object.
     * @param status the response status
     * @param headerMap the response headers
     * @param result an object that will be converted to JSON or null
     */
    void response(HttpStatus status, Map<String, String> headerMap, T result);
  }

  private record PathInfo(Map<String, Integer> pathVariableMap, Pattern regex) {}

  private record InjectorInfo(ArgumentInjector.RequestBodyInjector requestBodyInjector, ArgumentInjector.ResponseActorInjector responseActorInjector, ArgumentInjector[] injectors) {}

  private record Route(String requestMethod, PathInfo pathInfo, InjectorInfo injectorInfo, Method method, Actor<?> actor) {}

  private record RouteResult(Route route, Matcher pathMatcher) {}

  private sealed interface ArgumentInjector {
    record PathVariableInjector(int groupIndex) implements ArgumentInjector {}
    record RequestParamInjector(String key, String defaultValue) implements ArgumentInjector {}
    record RequestBodyInjector(JavaType javaType) implements ArgumentInjector {}
    record ResponseActorInjector(JavaType javaType) implements ArgumentInjector {}

    private static String firstValue(Headers requestHeaders, String key, String defaultValue) {
      var values = requestHeaders.get(key);
      return values == null? defaultValue: values.get(0);
    }

    static Object[] inject(ArgumentInjector[] argumentInjectors, Matcher pathMatcher, Headers requestHeaders, Object requestBody, Actor<Response<?>> responseActor) {
      var arguments = new Object[argumentInjectors.length];
      for(var i = 0; i < arguments.length; i++) {
        var argumentInjector = argumentInjectors[i];
        /*arguments[i] = switch (argumentInjectors[i]) {
          case PathVariableInjector pathVariable -> pathMatcher.group(pathVariable.groupIndex);
          case RequestParamInjector requestParam -> firstValue(requestHeaders, requestParam.key, requestParam.defaultValue);
          case RequestBodyInjector __ -> requestBody;
          case ResponseActorInjector __ -> responseActor;
        };*/
        Object argument;
        if (argumentInjector instanceof PathVariableInjector pathVariable) {
          argument = pathMatcher.group(pathVariable.groupIndex);
        } else if (argumentInjector instanceof RequestParamInjector requestParam) {
          argument = firstValue(requestHeaders, requestParam.key, requestParam.defaultValue);
        } else if (argumentInjector instanceof RequestBodyInjector) {
          argument = requestBody;
        } else if (argumentInjector instanceof ResponseActorInjector) {
          argument = responseActor;
        } else {
          throw new AssertionError("invalid injector " + argumentInjector);
        }
        arguments[i] = argument;
      }
      return arguments;
    }

    private static PathVariableInjector createPathVariableInjector(Type parameterType, PathVariable pathVariable, PathInfo pathInfo) {
      if (parameterType != String.class) {
        throw new IllegalStateException("a parameter annotated by @PathVariable should be of type String (" + parameterType + ")");
      }
      var pathVariableName = pathVariable.value();
      var groupIndex = pathInfo.pathVariableMap.get(pathVariableName);
      if (groupIndex == null) {
        throw new IllegalStateException("annotation @PathVariable unknown name " + pathVariableName);
      }
      return new PathVariableInjector(groupIndex);
    }

    private static RequestParamInjector createRequestParamInjector(Type parameterType, RequestParam requestParam) {
      if (parameterType != String.class) {
        throw new IllegalStateException("a parameter annotated by @RequestParam should be of type String");
      }
      return new RequestParamInjector(requestParam.value(), requestParam.defaultValue());
    }

    private static RequestBodyInjector createRequestBodyInjector(Type parameterType) {
      return new RequestBodyInjector(OBJECT_MAPPER.constructType(parameterType));
    }

    private static ResponseActorInjector createResponseActorInjector(Type parameterType) {
      if (!(parameterType instanceof ParameterizedType parameterizedType)
          || parameterizedType.getRawType() != Actor.class
          || !(parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType responseParameterizedType)
          || responseParameterizedType.getRawType() != Response.class) {
        throw new IllegalStateException("the response actor should be typed Actor<Response<type of the result>> (" + parameterType + ")");
      }
      var responseArgumentType = responseParameterizedType.getActualTypeArguments()[0];
      return new ResponseActorInjector(OBJECT_MAPPER.constructType(responseArgumentType));
    }

    private static ArgumentInjector findInjector(Type parameterType, Annotation[] annotations, PathInfo pathInfo) {
      ArgumentInjector foundInjector = null;
      Annotation foundAnnotation = null;
      for (Annotation annotation : annotations) {
        /*var injector = switch (annotation) {
          case PathVariable pathVariable -> createPathVariableInjector(parameterType, pathVariable, pathInfo);
          case RequestParam requestParam -> createRequestParamInjector(parameterType, requestParam);
          case RequestBody __ -> createRequestBodyInjector(parameterType);
          default -> null;
        };*/
        ArgumentInjector injector;
        if (annotation instanceof PathVariable pathVariable) {
          injector = createPathVariableInjector(parameterType, pathVariable, pathInfo);
        } else  if (annotation instanceof RequestParam requestParam) {
          injector = createRequestParamInjector(parameterType, requestParam);
        } else if (annotation instanceof RequestBody) {
          injector = createRequestBodyInjector(parameterType);
        } else {
          injector = null;
        }

        if (injector == null) {
          continue;
        }
        if (foundInjector != null) {
          throw new IllegalStateException("several incompatible annotations " + annotation + " " + foundAnnotation);
        }
        foundInjector = injector;
        foundAnnotation = annotation;
      }
      return foundInjector != null? foundInjector: createResponseActorInjector(parameterType);
    }

    private static InjectorInfo validateInjectors(ArgumentInjector[] injectors) {
      var group = Arrays.stream(injectors)
          .collect(groupingBy(Object::getClass));
      var requestBodyInjectors = group.getOrDefault(RequestBodyInjector.class, List.of());
      if (requestBodyInjectors.size() > 1) {
        throw new IllegalStateException("multiple @RequestBody annotation found");
      }
      var responseActorInjectors = group.getOrDefault(ResponseActorInjector.class, List.of());
      if (responseActorInjectors.isEmpty()) {
        throw new IllegalStateException("no response actor declared");
      }
      return new InjectorInfo(
          (RequestBodyInjector) requestBodyInjectors.stream().findFirst().orElse(null),
          (ResponseActorInjector) responseActorInjectors.get(0),
          injectors);
    }

    static InjectorInfo findInjectorInfo(Type[] parameterTypes, Annotation[][] parameterAnnotations, PathInfo pathInfo) {
      var injectors = new ArgumentInjector[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        var parameterType = parameterTypes[i];
        var annotations = parameterAnnotations[i];
        injectors[i] = findInjector(parameterType, annotations, pathInfo);
      }
      return validateInjectors(injectors);
    }
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  //@RequestMapping(method = GET|POST, path=/path/to/somewhere/{id})
  //void method(@PathVariable("id") path_variable, @RequestParam("param") request_parameter, @RequestBody request_body_record, reply_actor)

  private static final Pattern PATH_PATTERN = Pattern.compile("\\{(\\w+)\\}");

  private final ArrayList<Route> routes = new ArrayList<>();
  private final ArrayList<Actor<?>> actors = new ArrayList<>();

  private static PathInfo createPathInfo(String path) {
    if (path.endsWith("/")) {
      throw new IllegalStateException("invalid path " + path + " ends with '/'");
    }
     var matcher = PATH_PATTERN.matcher(path);
     var box = new Object() { int counter = 1; };
     var pathVariableMap = matcher.results()
         .map(result -> result.group(1))
         .collect(toMap(k -> k, __ -> box.counter++));
     if (pathVariableMap.isEmpty()) {
       return new PathInfo(Map.of(), Pattern.compile(path));
     }
     var regex = matcher.replaceAll(matchResult -> "(\\\\w+)");
     return new PathInfo(pathVariableMap, Pattern.compile(regex));
  }

  /**
   * Add the routes defined by the actor behavior to the server.
   * When a HTTP request calls the server, it redirects the call to the actors dependening on the routes
   * they declared.
   *
   * A method of the actor behavior annotated with {@link RequestMapping} declares a new route.
   * The annotation {@link RequestMapping} defines
   * <ul>
   *   <li>
   *     {@link RequestMapping#method()} the HTTP request method of the route
   *   </li>
   *   <li>
   *     {@link RequestMapping#path()} define a regular expression to match the HTTP request URI.
   *     A path should never ends with a slash '/' and can include request parameter name "{foo}" in between
   *     curly braces.
   *    <p>
   *     By example: <tt>/users</tt> match the URI "/users" while <tt>/users/{id}</tt> match any URIs that starts
   *     with "/users". The value matched by <tt>{if}</tt> is available using the annotation {@link RequestParam}.
   *   </li>
   * </ul>
   *
   * The parameters of a method defining a route can be annotated by
   * <ul>
   *   <li>{@link PathVariable)} to match an argument is extracted from the URI, the type of the parameter
   *       is String</li>
   *   <li>{@link RequestBody}, the argument is a JSON encoded record containing the body of the request,
   *       the type of the argument is a record or a list/map of records</li>
   *   <li>{@link RequestParam}, the argument is the value of an header of the request, the type of the parameter
   *       is a String</li>
   * </ul>
   *
   * Moreover, the method should define the actor that will receive the response.
   * The type of the actor is <tt>Actor<Response<X></tt> with <tt>X</tt> being a record, a list of record
   * or java.lang.Void if the result is null.
   *
   * @param actor the actor defining the routes
   * @return the current server so calls can be chained
   */
  public HttpServer routes(Actor<?> actor) {
    Objects.requireNonNull(actor);
    for(var method: actor.behaviorType().getMethods()) {
      var requestMapping = method.getAnnotation(RequestMapping.class);
      if (requestMapping == null) {
        continue;
      }
      var pathInfo = createPathInfo(requestMapping.path());
      var injectorInfo = ArgumentInjector.findInjectorInfo(method.getGenericParameterTypes(), method.getParameterAnnotations(), pathInfo);
      var requestMethod = requestMapping.method().name();
      var route = new Route(requestMethod, pathInfo, injectorInfo, method, actor);
      routes.add(route);
    }
    actors.add(actor);
    return this;
  }

  private RouteResult findRoute(HttpExchange exchange) {
    var uri = exchange.getRequestURI().toString();
    var requestMethod = exchange.getRequestMethod();
    for(var route: routes) {
      if (!requestMethod.equals(route.requestMethod)) {
        continue;
      }
      var pathMatcher = route.pathInfo.regex.matcher(uri);
      if (pathMatcher.matches()) {
        return new RouteResult(route, pathMatcher);
      }
    }
    return null;
  }

  private void serveStaticContent(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestMethod().equals("GET")) {
      exchange.sendResponseHeaders(500, 0);
      exchange.close();
      return;
    }

    var path = Path.of(".", exchange.getRequestURI().toString());
    System.out.println("  try to serve static content from " + path);

    long fileSize;
    try {
      fileSize = Files.size(path);
    } catch (IOException e) {
      exchange.sendResponseHeaders(404, 0);
      exchange.close();
      System.out.println("  nof found " + path);
      return;
    }
    exchange.sendResponseHeaders(200, fileSize);
    try (var outputStream = exchange.getResponseBody();
         var inputStream = Files.newInputStream(path)) {
      inputStream.transferTo(outputStream);
    }
    exchange.close();
    System.out.println("  served " + fileSize + " bytes from " + path);
  }

  /**
   * Bind the server to a specific name and local port and start the server.
   * The server spawns all actors,. Then for each request, it spawns a new actor to handle the response
   * and then it redirects the request to an actor using the routes.
   * The spawned actor is shutdown when the response is fully sent or if an error occurs.
   *
   * @param address the name and local port
   * @throws IOException if the server can not be bound to the address
   * @throws InterruptedException if the current thread is interrupted
   */
  public void bind(InetSocketAddress address) throws IOException, InterruptedException {
    Objects.requireNonNull(address);
    if (routes.isEmpty()) {
      throw new IllegalStateException("no route registered");
    }
    Actor.run(actors, startupContext -> {
      var server = com.sun.net.httpserver.HttpServer.create(address, 0);
      server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      server.createContext("/", exchange -> {
        System.out.println("request " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

        var routeResult = findRoute(exchange);
        if (routeResult == null) {
          serveStaticContent(exchange);
          return;
        }

        var pathMatcher = routeResult.pathMatcher;
        var route = routeResult.route;
        var injectorInfo = route.injectorInfo;

        Object requestBody = null;
        var requestBodyInjector = injectorInfo.requestBodyInjector;
        if (requestBodyInjector != null) {
          requestBody = OBJECT_MAPPER.readValue(exchange.getRequestBody(), requestBodyInjector.javaType);
        }

        @SuppressWarnings("unchecked")
        var responseActor = (Actor<Response<?>>) (Actor<?>) Actor.of(Response.class)
            .behavior(context -> new Response<>() {
              @Override
              public void response(HttpStatus status, Object result) {
                 response(status, Map.of(), result);
              }

              @Override
              public void response(HttpStatus status, Map<String, String> headerMap, Object result) {
                try {
                  exchange.sendResponseHeaders(status.code, 0);
                  var headers = exchange.getResponseHeaders();
                  headerMap.forEach(headers::add);
                  headers.add("Content-Type", "application/json");
                  try(var outputStream = exchange.getResponseBody()) {
                    OBJECT_MAPPER.writeValue(outputStream, result);
                  }
                } catch(IOException e) {
                  throw new UncheckedIOException(e);
                }
                context.shutdown();
              }
            });
        startupContext.spawn(responseActor);

        var method = route.method;
        var actor = route.actor;
        var args = ArgumentInjector.inject(injectorInfo.injectors, pathMatcher, exchange.getRequestHeaders(), requestBody, responseActor);
        startupContext.postTo(actor, behavior -> method.invoke(behavior, args));
      });
      server.start();
      System.out.println("server started at " + server.getAddress());
    });
  }
}
