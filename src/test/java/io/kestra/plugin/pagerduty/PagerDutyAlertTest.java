package io.kestra.plugin.pagerduty;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.google.common.io.Files;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@KestraTest
public class PagerDutyAlertTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ModelValidator modelValidator;

    @Test
    void run() throws Exception {
        var runContext = runContextFactory.of(
            Map.of(
                "payloadSummary", "PagerDuty test alert"
            )
        );

        var task = PagerDutyAlert.builder()
            .url(startServer().getURI() + "/webhook-unit-test")
            .payload(
                Property.ofValue(
                    Files.asCharSource(
                        new File(
                            Objects.requireNonNull(
                                PagerDutyAlertTest.class.getClassLoader()
                                    .getResource("pagerduty.peb")
                            )
                                .toURI()
                        ),
                        StandardCharsets.UTF_8
                    ).read()
                )
            )
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void flowSettingDeprecatedOptionsStillValidatesAndRuns() throws Exception {
        // Flows written before the deprecation still set these options: they must keep parsing, validating and running.
        var summary = "deprecated-options-" + IdUtils.create();
        var flow = YamlParser.parse(
            """
                id: pagerduty_deprecated_options
                namespace: io.kestra.tests
                tasks:
                  - id: alert
                    type: io.kestra.plugin.pagerduty.PagerDutyAlert
                    url: "%s/webhook-unit-test"
                    payload: '{"routing_key": "SOME_INTEGRATION_KEY", "event_action": "trigger", "payload": {"summary": "%s", "source": "kestra", "severity": "error"}}'
                    options:
                      readTimeout: PT30S
                      connectionPoolIdleTimeout: PT10S
                      maxContentLength: 1024
                """.formatted(startServer().getURI(), summary),
            Flow.class
        );

        assertThat(modelValidator.isValid(flow).isEmpty(), is(true));

        var runContext = runContextFactory.of(Map.of());
        var task = (PagerDutyAlert) flow.getTasks().getFirst();
        var options = task.getOptions();
        assertThat(runContext.render(options.getReadTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(30)));
        assertThat(runContext.render(options.getConnectionPoolIdleTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(10)));
        assertThat(runContext.render(options.getMaxContentLength()).as(Integer.class).orElseThrow(), is(1024));

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString(summary));
    }

    private EmbeddedServer startServer() {
        var embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();
        return embeddedServer;
    }
}
