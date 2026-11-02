package io.github.cispal19.captcha.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import org.altcha.altcha.v2.Altcha;

/**
 * Herramienta (no es una prueba) que regenera {@code fixtures/altcha/challenge.json}. Se ejecuta a
 * mano y después {@code node frontend/scripts/solve-fixture.mjs} genera {@code payload.b64}. Ambos
 * archivos se versionan.
 *
 * <pre>mvn -f backend/pom.xml -pl captcha-spring-boot-starter test-compile exec:java ...</pre>
 */
public final class AltchaFixtureGenerator {

  /** Clave marcada test-only (allowlist de Gitleaks); no es un secreto. */
  public static final String TEST_ONLY_KEY = "test-only-altcha-fixture-key-000000000000";

  private static final long TEN_YEARS_SECONDS = 10L * 365 * 24 * 3600;

  private AltchaFixtureGenerator() {}

  public static void main(String[] args) throws Exception {
    Altcha.Challenge challenge =
        Altcha.createChallenge(
            new Altcha.CreateChallengeOptions()
                .algorithm("PBKDF2/SHA-256")
                .cost(5000)
                .hmacSignatureSecret(TEST_ONLY_KEY)
                .expiresInSeconds(TEN_YEARS_SECONDS));
    Path target = fixturesDir().resolve("challenge.json");
    Files.writeString(target, challenge.toJson() + "\n");
    System.out.println("Escrito " + target);
  }

  /** Busca {@code fixtures/altcha} subiendo desde el directorio de trabajo. */
  public static Path fixturesDir() {
    for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
      Path candidate = p.resolve("fixtures/altcha");
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("No se encontró fixtures/altcha");
  }
}
