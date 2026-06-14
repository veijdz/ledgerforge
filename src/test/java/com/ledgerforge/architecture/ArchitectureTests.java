package com.ledgerforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Architecture fitness functions for the hexagonal/Modulith boundaries (see 02-architecture.md).
 *
 * <p>The package structure is still empty, so these rules pass vacuously today. They are wired from
 * day one so they bite the moment domain and adapter code lands.
 */
@AnalyzeClasses(packages = ArchitectureTests.BASE_PACKAGE)
class ArchitectureTests {

  static final String BASE_PACKAGE = "com.ledgerforge";

  /** Domain stays free of the framework: no Spring, jOOQ, Jakarta Persistence, or web concerns. */
  @ArchTest
  static final ArchRule domainHasNoFrameworkDependencies =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "org.jooq..",
              "jakarta.persistence..",
              "..adapter.in.web..",
              "..web..")
          // No domain classes exist yet; the rule is wired now and bites once they land.
          .allowEmptyShould(true);

  /** Domain is independent of adapters: adapters depend on the domain, never the reverse. */
  @ArchTest
  static final ArchRule domainDoesNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..")
          .allowEmptyShould(true);

  /** No package cycles anywhere under the base package. */
  @ArchTest
  static final ArchRule noPackageCycles =
      SlicesRuleDefinition.slices().matching("com.ledgerforge.(*)..").should().beFreeOfCycles();

  /** Controllers expose DTOs, never domain entities (..model.. holds the aggregates/VOs). */
  @ArchTest
  static final ArchRule controllersDoNotReturnDomainEntities =
      noClasses()
          .that()
          .resideInAnyPackage("..adapter.in.web..", "..web..")
          .and()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.model..")
          .allowEmptyShould(true);
}
