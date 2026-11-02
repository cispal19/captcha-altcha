package io.github.cispal19.captcha.application.port;

/** Seudonimiza IP y usuario antes de almacenarlos o registrarlos. */
public interface SubjectHasher {

  String hash(String value);
}
