/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.configuration;

import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration placeholder.
 * The actual version rewrite is handled by a response filter
 * (see org.dspace.app.rest.filter.OpenApiVersionRewriteFilter).
 */
@Configuration
public class OpenApiConfiguration {
    // Intentionally empty: avoids compile-time dependency on springdoc-core.
}
