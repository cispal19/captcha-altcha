package io.github.cispal19.captcha.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Interpreta {@code protected-paths} ("MÉTODO ruta") y decide si una petición se protege. */
public final class ProtectedRequestMatcher {

  private static final Set<String> METHODS =
      Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private record Entry(String method, PathPattern pattern) {}

  private final List<Entry> entries;

  private ProtectedRequestMatcher(List<Entry> entries) {
    this.entries = entries;
  }

  /**
   * @throws IllegalArgumentException si alguna entrada no tiene el formato "MÉTODO /ruta"
   */
  public static ProtectedRequestMatcher parse(List<String> definitions) {
    if (definitions == null) {
      throw new IllegalArgumentException("la lista no puede ser nula");
    }
    PathPatternParser parser = PathPatternParser.defaultInstance;
    List<Entry> parsed =
        definitions.stream()
            .map(String::trim)
            .filter(d -> !d.isEmpty())
            .map(
                definition -> {
                  String[] parts = definition.split("\\s+");
                  if (parts.length != 2
                      || !METHODS.contains(parts[0].toUpperCase(Locale.ROOT))
                      || !parts[1].startsWith("/")) {
                    throw new IllegalArgumentException(
                        "'" + definition + "' no tiene el formato \"MÉTODO /ruta\"");
                  }
                  return new Entry(parts[0].toUpperCase(Locale.ROOT), parser.parse(parts[1]));
                })
            .toList();
    return new ProtectedRequestMatcher(parsed);
  }

  public boolean matches(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String path =
        contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
            ? uri.substring(contextPath.length())
            : uri;
    PathContainer container = PathContainer.parsePath(path.isEmpty() ? "/" : path);
    return entries.stream()
        .anyMatch(e -> e.method().equals(request.getMethod()) && e.pattern().matches(container));
  }
}
