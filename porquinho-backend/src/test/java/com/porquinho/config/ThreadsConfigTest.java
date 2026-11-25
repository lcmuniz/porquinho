package com.porquinho.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Virtual threads make HikariCP spin and saturate the CPU (brettwooldridge/HikariCP#2329). Reads the main
 * application.yml directly, since the test classpath shadows it (see {@link MultipartLimitsConfigTest}).
 */
class ThreadsConfigTest {

    @Test
    void mainConfigKeepsVirtualThreadsOffAndSeveralSchedulerThreads() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.get("spring.threads.virtual.enabled")).hasToString("false");
        // The default is a single thread: one long job would hold all the others
        assertThat(Integer.parseInt(properties.get("spring.task.scheduling.pool.size").toString())).isGreaterThan(1);
    }
}
