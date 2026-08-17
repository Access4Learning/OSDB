package org.sifunity.broker.extensions.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.extensions.config.SequencingAutoConfiguration;
import org.sifunity.broker.extensions.sequencing.MessageSequenceFilter;
import org.sifunity.broker.extensions.sequencing.SequenceService;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SequencingAutoConfiguration Integration Tests")
class SequencingAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SequencingAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    @DisplayName("When enabled=true, all beans are registered")
    void whenEnabled_allBeansRegistered() {
        contextRunner
                .withPropertyValues("sif.extensions.sequencing.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SequenceService.class);
                    assertThat(context).hasSingleBean(ToggleService.class);
                    assertThat(context).hasBean("messageSequenceFilter");
                    assertThat(context).getBean("messageSequenceFilter")
                            .isInstanceOf(FilterRegistrationBean.class);
                });
    }

    @Test
    @DisplayName("When property absent (matchIfMissing=true), all beans are registered")
    void whenPropertyAbsent_allBeansRegistered() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(SequenceService.class);
                    assertThat(context).hasSingleBean(ToggleService.class);
                    assertThat(context).hasBean("messageSequenceFilter");
                    assertThat(context).getBean("messageSequenceFilter")
                            .isInstanceOf(FilterRegistrationBean.class);
                });
    }

    @Test
    @DisplayName("When disabled=false, no extension beans are registered")
    void whenDisabled_noBeansRegistered() {
        contextRunner
                .withPropertyValues("sif.extensions.sequencing.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SequenceService.class);
                    assertThat(context).doesNotHaveBean(ToggleService.class);
                    assertThat(context).doesNotHaveBean("messageSequenceFilter");
                    assertThat(context).doesNotHaveBean("extensionsRestTemplate");
                });
    }

    @Test
    @DisplayName("When no RestTemplate exists, auto-configuration creates one")
    void whenNoRestTemplate_createsOne() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    assertThat(context).hasBean("extensionsRestTemplate");
                });
    }

    @Test
    @DisplayName("When RestTemplate already exists, does not override it")
    void whenRestTemplateExists_doesNotOverride() {
        RestTemplate existingRestTemplate = new RestTemplate();

        contextRunner
                .withBean("myRestTemplate", RestTemplate.class, () -> existingRestTemplate)
                .run(context -> {
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    assertThat(context).doesNotHaveBean("extensionsRestTemplate");
                    assertThat(context).getBean(RestTemplate.class).isSameAs(existingRestTemplate);
                });
    }
}
