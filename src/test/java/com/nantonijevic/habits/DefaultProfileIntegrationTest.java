package com.nantonijevic.habits;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProfileIntegrationTest {

    private static final String ACTIVE_PREFIX =
        "ACTIVE_PROFILES=";

    private static final String DEFAULT_PREFIX =
        "DEFAULT_PROFILES=";

    @Test
    void explicitProfileSuppressesLocalDefault()
        throws Exception {

        Path stdoutFile =
            Files.createTempFile("default-profile-", ".out");

        Path stderrFile =
            Files.createTempFile("default-profile-", ".err");

        try {
            Process process = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                mainResourcesFirstClasspath(),
                ProfileProbe.class.getName()
            )
                .redirectOutput(stdoutFile.toFile())
                .redirectError(stderrFile.toFile())
                .start();

            boolean finished =
                process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }

            String stdout = Files.readString(stdoutFile);
            String stderr = Files.readString(stderrFile);

            assertThat(finished)
                .as(
                    "Profile probe did not finish. stderr:%n%s",
                    stderr
                )
                .isTrue();

            assertThat(process.exitValue())
                .as("Profile probe stderr:%n%s", stderr)
                .isZero();

            assertThat(profileLines(stdout, ACTIVE_PREFIX))
                .containsExactly("ACTIVE_PROFILES=prod");

            assertThat(profileLines(stdout, DEFAULT_PREFIX))
                .containsExactly("DEFAULT_PROFILES=local");
        } finally {
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);
        }
    }

    private List<String> profileLines(
        String stdout,
        String prefix
    ) {
        return stdout.lines()
            .filter(line -> line.startsWith(prefix))
            .toList();
    }

    private String javaExecutable() {
        return Path.of(
            System.getProperty("java.home"),
            "bin",
            "java"
        ).toString();
    }

    private String mainResourcesFirstClasspath() {
        String classpath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );

        List<String> entries = new ArrayList<>(
            Arrays.asList(
                classpath.split(
                    Pattern.quote(File.pathSeparator)
                )
            )
        );

        String mainClasses = entries.stream()
            .filter(this::isMainClassesDirectory)
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException(
                    "target/classes is missing from test classpath"
                )
            );

        entries.remove(mainClasses);
        entries.addFirst(mainClasses);

        return String.join(File.pathSeparator, entries);
    }

    private boolean isMainClassesDirectory(String entry) {
        return Path.of(entry)
            .normalize()
            .endsWith(Path.of("target", "classes"));
    }

    public static final class ProfileProbe {

        private ProfileProbe() {
        }

        public static void main(String[] arguments) {
            SpringApplication application =
                new SpringApplication(
                    EmptyConfiguration.class
                );

            application.setAdditionalProfiles("prod");
            application.setWebApplicationType(
                WebApplicationType.NONE
            );
            application.setLogStartupInfo(false);
            application.setRegisterShutdownHook(false);

            try (ConfigurableApplicationContext context =
                     application.run(
                         "--spring.main.banner-mode=off"
                     )) {

                System.out.println(
                    ACTIVE_PREFIX
                        + String.join(
                        ",",
                        context.getEnvironment()
                            .getActiveProfiles()
                    )
                );

                System.out.println(
                    DEFAULT_PREFIX
                        + String.join(
                        ",",
                        context.getEnvironment()
                            .getDefaultProfiles()
                    )
                );
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class EmptyConfiguration {
    }
}
