package com.ledgerforge;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the Spring Modulith structure: module boundaries are respected and there are no module
 * cycles. With empty modules and no cross-module dependencies this passes; it bites once modules
 * reference each other outside published named interfaces.
 */
class ModularityTests {

  @Test
  void verifiesModularStructure() {
    ApplicationModules.of(LedgerForgeApplication.class).verify();
  }
}
