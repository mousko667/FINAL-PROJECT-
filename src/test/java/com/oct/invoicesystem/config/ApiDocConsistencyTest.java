package com.oct.invoicesystem.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT-041 / AUDIT-017 — {@code docs/API.md} must stay in sync with the code.
 *
 * <p>The former API.md described 86 of 219 endpoints and listed 19 phantom paths (returning 404),
 * and — most dangerously — granted {@code ROLE_ADMIN} on 15 financial surfaces the code deliberately
 * closes. The document was regenerated from the source annotations (decision <b>D6</b>). This test is
 * the guardrail that stops it drifting again: every path documented in API.md must correspond to a
 * real {@code @*Mapping} in a controller. Without it, API.md re-derives within months (the exact
 * failure mode of the original finding).</p>
 *
 * <p>The check is intentionally one-directional (documented ⊆ real): a documented path that does not
 * exist is a lie to any integrator and fails the test. It parses the controllers by regex — the same
 * technique used to regenerate the file — rather than booting Spring, so it stays a fast unit test.</p>
 */
class ApiDocConsistencyTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path API_MD = PROJECT_ROOT.resolve("docs/API.md");
    private static final Path CONTROLLERS_DIR =
            PROJECT_ROOT.resolve("src/main/java/com/oct/invoicesystem/domain");

    private static final Pattern MAPPING =
            Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping\\s*(\\(([^)]*)\\))?");
    private static final Pattern REQUEST_MAPPING =
            Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]*)\"");
    // A back-ticked API path in a markdown table cell, e.g. `/api/v1/invoices/{id}`
    private static final Pattern DOC_PATH = Pattern.compile("`(/api/v1[^`]*)`");

    @Test
    void everyDocumentedPathExistsInTheCode() throws IOException {
        Set<String> realPaths = collectRealPaths();
        Set<String> documentedPaths = collectDocumentedPaths();

        // Sanity: the extractor must actually find endpoints, otherwise the assertion is vacuous.
        assertThat(realPaths)
                .as("controller scan must find the real endpoints")
                .hasSizeGreaterThan(150);
        assertThat(documentedPaths)
                .as("API.md must document paths")
                .hasSizeGreaterThan(150);

        Set<String> phantom = new TreeSet<>(documentedPaths);
        phantom.removeAll(realPaths);

        assertThat(phantom)
                .as("API.md documents paths that no longer exist in any controller (phantoms). "
                        + "Fix the controller or regenerate API.md from the code.")
                .isEmpty();
    }

    /** Parses every controller and returns the set of normalized real paths (path only, verb-agnostic). */
    private Set<String> collectRealPaths() throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(CONTROLLERS_DIR)) {
            List<Path> controllers = files
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .collect(Collectors.toList());
            for (Path file : controllers) {
                String src = Files.readString(file, StandardCharsets.UTF_8);
                String base = basePath(src);
                Matcher m = MAPPING.matcher(src);
                while (m.find()) {
                    String args = m.group(3);
                    String sub = firstPathLiteral(args);
                    paths.add(join(base, sub));
                }
            }
        }
        return paths;
    }

    private Set<String> collectDocumentedPaths() throws IOException {
        assertThat(Files.exists(API_MD)).as("docs/API.md must exist").isTrue();
        String md = Files.readString(API_MD, StandardCharsets.UTF_8);
        Set<String> docs = new LinkedHashSet<>();
        Matcher m = DOC_PATH.matcher(md);
        while (m.find()) {
            String p = m.group(1).trim();
            // Ignore the `/api/v1` base-URL mention and query-string examples.
            if (p.equals("/api/v1") || p.contains("?")) {
                continue;
            }
            docs.add(normalizePlaceholders(p));
        }
        return docs;
    }

    private String basePath(String src) {
        Matcher m = REQUEST_MAPPING.matcher(src);
        if (m.find()) {
            String lit = firstPathLiteral(m.group(1));
            return lit == null ? "" : lit;
        }
        return "";
    }

    private String firstPathLiteral(String annotationArgs) {
        if (annotationArgs == null || annotationArgs.isBlank()) {
            return null;
        }
        Matcher valueMatcher =
                Pattern.compile("(?:value|path)\\s*=\\s*\"([^\"]*)\"").matcher(annotationArgs);
        if (valueMatcher.find()) {
            return valueMatcher.group(1);
        }
        Matcher lit = LITERAL.matcher(annotationArgs);
        return lit.find() ? lit.group(1) : null;
    }

    private String join(String base, String sub) {
        String b = base == null ? "" : base;
        String s = sub == null ? "" : sub;
        if (!b.isEmpty() && !b.startsWith("/")) {
            b = "/" + b;
        }
        if (!s.isEmpty() && !s.startsWith("/")) {
            s = "/" + s;
        }
        String full = s.isEmpty() ? b : b + s;
        full = full.replaceAll("/+", "/");
        if (full.length() > 1 && full.endsWith("/")) {
            full = full.substring(0, full.length() - 1);
        }
        return normalizePlaceholders(full.isEmpty() ? "/" : full);
    }

    /** Collapses every {@code {xxx}} path variable to {@code {}} so {@code {id}} == {@code {invoiceId}}. */
    private String normalizePlaceholders(String path) {
        return path.replaceAll("\\{[^}]*}", "{}");
    }
}
