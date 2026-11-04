package com.porquinho.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 5.1: the upload limits live in the main application.yml, which the test classpath shadows with its own
 * application.yml. This reads the main file directly so removing or shrinking the limits (Spring's default is 1MB)
 * fails the build. The nginx limit (client_max_body_size 11m) stays a manual deploy check.
 */
class MultipartLimitsConfigTest {

    @Test
    void mainConfigAllows10MbStatementsKeptInMemory() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("10MB");
        assertThat(properties.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("11MB");
        // Above the request size: the statement never goes to a temporary file on disk
        assertThat(properties.getProperty("spring.servlet.multipart.file-size-threshold")).isEqualTo("11MB");
    }
}
