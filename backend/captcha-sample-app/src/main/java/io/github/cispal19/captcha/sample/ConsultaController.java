package io.github.cispal19.captcha.sample;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Formulario público protegido en modo invisible (demostración del modo por formulario). */
@RestController
public class ConsultaController {

  @PostMapping("/api/public/consulta")
  public Map<String, String> consulta() {
    return Map.of("status", "ok", "message", "Consulta registrada");
  }
}
