package io.github.cispal19.captcha.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** La regla de dependencias de Clean Architecture se verifica en el build. */
@AnalyzeClasses(
    packages = "io.github.cispal19.captcha",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyRulesTest {

  @ArchTest
  static final ArchRule DOMAIN_DEPENDS_ONLY_ON_JDK =
      classes()
          .that()
          .resideInAPackage("..captcha.domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "..captcha.domain..");

  @ArchTest
  static final ArchRule APPLICATION_DEPENDS_ONLY_ON_DOMAIN_AND_JDK =
      classes()
          .that()
          .resideInAPackage("..captcha.application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "..captcha.domain..", "..captcha.application..");

  @ArchTest
  static final ArchRule INNER_LAYERS_IGNORE_FRAMEWORKS =
      noClasses()
          .that()
          .resideInAnyPackage("..captcha.domain..", "..captcha.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "io.lettuce..", "org.altcha..", "org.json..");

  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("..captcha.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..captcha.infrastructure..", "..captcha.web..", "..captcha.autoconfigure..");
}
