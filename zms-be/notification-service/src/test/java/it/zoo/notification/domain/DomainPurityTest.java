package it.zoo.notification.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainPurityTest {

    @Test
    void shouldNotImportAnyFrameworkInsideDomain() throws IOException {
        Path domain = Path.of("src/main/java/it/zoo/notification/domain");
        assertTrue(Files.isDirectory(domain), "domain package must exist at " + domain.toAbsolutePath());

        try (Stream<Path> files = Files.walk(domain)) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> {
                        try {
                            return Files.readAllLines(p).stream()
                                    .filter(line -> line.startsWith("import "))
                                    .filter(line -> line.contains("jakarta.")
                                            || line.contains("io.quarkus")
                                            || line.contains("org.hibernate")
                                            || line.contains("org.mapstruct"))
                                    .map(line -> p.getFileName() + ": " + line.trim());
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();

            assertTrue(offenders.isEmpty(), "Framework imports leaked into domain/: " + offenders);
        }
    }
}
