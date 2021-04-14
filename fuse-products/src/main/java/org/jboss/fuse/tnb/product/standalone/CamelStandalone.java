package org.jboss.fuse.tnb.product.standalone;

import org.jboss.fuse.tnb.common.config.TestConfiguration;
import org.jboss.fuse.tnb.common.utils.MapUtils;
import org.jboss.fuse.tnb.common.utils.WaitUtils;
import org.jboss.fuse.tnb.product.LocalProduct;
import org.jboss.fuse.tnb.product.Product;
import org.jboss.fuse.tnb.product.integration.IntegrationBuilder;
import org.jboss.fuse.tnb.product.integration.IntegrationGenerator;
import org.jboss.fuse.tnb.product.util.Maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

@AutoService(Product.class)
public class CamelStandalone extends LocalProduct {
    private static final Logger LOG = LoggerFactory.getLogger(CamelStandalone.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private Path logFile;

    @Override
    public void createIntegration(String name, IntegrationBuilder integrationBuilder, String... camelComponents) {
        LOG.info("Creating Camel Standalone application project");
        Maven.createFromArchetype(
            "org.apache.camel.archetypes",
            "camel-archetype-main",
            TestConfiguration.camelVersion(),
            TestConfiguration.appGroupId(),
            name,
            TestConfiguration.appLocation().toFile()
        );

        IntegrationGenerator.toFile(integrationBuilder, TestConfiguration.appLocation().resolve(name));

        Maven.addCamelComponentDependencies(TestConfiguration.appLocation().resolve(name), camelComponents);

        logFile = TestConfiguration.appLocation().resolve(name + ".log");

        LOG.info("Starting integration {}", name);
        executorService.submit(() -> Maven.invoke(
            TestConfiguration.appLocation().resolve(name),
            Arrays.asList("compile", "exec:java"),
            null,
            MapUtils.toProperties(Map.of("exec.mainClass", "com.test.MyApplication")),
            logFile.toAbsolutePath().toString()
            )
        );

        waitForIntegration(name);
    }

    @Override
    public void waitForIntegration(String name) {
        BooleanSupplier success = () -> {
            try {
                return Files.readString(logFile).contains("started and consuming");
            } catch (IOException e) {
                // Ignored as the file may not exist yet
            }
            return false;
        };
        BooleanSupplier fail = () -> {
            try {
                String content = Files.readString(logFile);
                return content.toLowerCase().contains("build failure") || content.contains("triggering shutdown of the JVM");
            } catch (IOException e) {
                // Ignored as the file may not exist yet
            }
            return false;
        };

        WaitUtils.waitFor(success, fail, 1000L, String.format("Waiting until the integration %s is running", name));
    }

    @Override
    public void removeIntegration() {
        LOG.debug("Shutting down the executor service");
        executorService.shutdown();
    }
}