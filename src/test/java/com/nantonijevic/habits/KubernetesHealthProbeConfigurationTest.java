package com.nantonijevic.habits;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesHealthProbeConfigurationTest {

    private static final String APPLICATION_YAML =
        "src/main/resources/application.yml";

    @Test
    void mainConfigurationSupportsKubernetesHealthProbes()
        throws IOException {

        String probesEnabled = mainApplicationProperty(
            "management.endpoint.health.probes.enabled"
        );

        assertThat(probesEnabled)
            .as(
                "main application.yml must enable "
                    + "Kubernetes health probes"
            )
            .isEqualTo("true");

        String readinessMembers = mainApplicationProperty(
            "management.endpoint.health.group.readiness.include"
        );

        assertThat(readinessMembers)
            .as(
                "readiness must include application state "
                    + "and the database"
            )
            .isNotNull();

        assertThat(
            Arrays.stream(readinessMembers.split(","))
                .map(String::trim)
                .toList()
        )
            .containsExactlyInAnyOrder(
                "readinessState",
                "db"
            );
    }

    private String mainApplicationProperty(
        String propertyName
    ) throws IOException {

        return new YamlPropertySourceLoader()
            .load(
                "main-application",
                new FileSystemResource(APPLICATION_YAML)
            )
            .stream()
            .map(source ->
                source.getProperty(propertyName)
            )
            .filter(value -> value != null)
            .map(Object::toString)
            .findFirst()
            .orElse(null);
    }
}
