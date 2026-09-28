/**
 * Shared kernel: error handling, web infrastructure, money, pagination.
 * Open module — every other module may depend on any of its types.
 * Must never depend on a business module.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.commercex.common;

import org.springframework.modulith.ApplicationModule;
