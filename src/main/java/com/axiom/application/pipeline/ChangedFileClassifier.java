package com.axiom.application.pipeline;

import com.axiom.domain.pipeline.FileCategory;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ChangedFileClassifier {
    public FileCategory classify(String path) {
        String normalized = normalize(path);
        String name = fileName(normalized);

        if (normalized.startsWith("build/generated/")
                || normalized.startsWith("generated/")
                || normalized.startsWith("dist/")
                || normalized.startsWith("coverage/")) {
            return FileCategory.GENERATED;
        }
        if (normalized.startsWith("src/test/")
                || normalized.startsWith("tests/")
                || normalized.startsWith("test/")
                || normalized.contains("/__tests__/")
                || name.matches(".*(test|spec)\\.(java|kt|js|jsx|ts|tsx)$")
                || name.matches(".*test\\.java$")
                || name.matches("(test_.*|.*_test)\\.py$")) {
            return FileCategory.TEST_SOURCE;
        }
        if (name.equals("pom.xml")
                || name.equals("package.json")
                || name.equals("pyproject.toml")
                || name.equals("requirements.txt")) {
            return FileCategory.DEPENDENCY_MANIFEST;
        }
        if (name.equals("package-lock.json")
                || name.equals("pnpm-lock.yaml")
                || name.equals("yarn.lock")
                || name.equals("poetry.lock")
                || name.equals("uv.lock")
                || name.equals("gradle.lockfile")) {
            return FileCategory.DEPENDENCY_LOCKFILE;
        }
        if (name.equals("build.gradle")
                || name.equals("build.gradle.kts")
                || name.equals("settings.gradle")
                || name.equals("settings.gradle.kts")
                || name.equals("gradle.properties")
                || name.equals("makefile")) {
            return FileCategory.BUILD_CONFIGURATION;
        }
        if (normalized.startsWith(".github/workflows/")
                || name.equals(".gitlab-ci.yml")
                || name.equals("jenkinsfile")) {
            return FileCategory.CI_CONFIGURATION;
        }
        if (name.equals("dockerfile")
                || name.equals("docker-compose.yml")
                || name.equals("docker-compose.yaml")
                || normalized.startsWith("k8s/")
                || normalized.startsWith("kubernetes/")
                || normalized.startsWith("helm/")
                || normalized.startsWith("terraform/")
                || normalized.endsWith(".tf")) {
            return FileCategory.INFRASTRUCTURE_CONFIGURATION;
        }
        if (normalized.contains("db/migration/") || normalized.startsWith("migrations/")) {
            return FileCategory.DATABASE_MIGRATION;
        }
        if (name.equals("application.yml")
                || name.equals("application.yaml")
                || name.equals("application.properties")
                || name.equals(".env.example")
                || normalized.startsWith("config/")) {
            return FileCategory.APPLICATION_CONFIGURATION;
        }
        if (normalized.startsWith("docs/")
                || name.startsWith("readme")
                || normalized.endsWith(".md")) {
            return FileCategory.DOCUMENTATION;
        }
        if (normalized.startsWith("src/")
                || normalized.startsWith("app/")
                || normalized.startsWith("lib/")
                || normalized.matches(".*\\.(java|kt|kts|js|jsx|ts|tsx|py|go|rs|cs)$")) {
            return FileCategory.PRODUCTION_SOURCE;
        }
        return FileCategory.UNKNOWN;
    }

    public String moduleHint(String path) {
        String normalized = normalize(path);
        int separator = normalized.lastIndexOf('/');
        if (separator <= 0) return null;
        String parent = normalized.substring(0, separator);
        return parent.length() <= 255 ? parent : parent.substring(0, 255);
    }

    private String normalize(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        return normalized;
    }

    private String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
