package com.instantpay.it;

import com.instantpay.adapter.in.web.dto.SendPaymentRequest;
import com.instantpay.domain.model.PaymentStatus;
import com.instantpay.domain.port.in.SendPaymentUseCase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@Import(PaymentFlowIntegrationTest.KafkaTopicConfig.class)
class PaymentFlowIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    // ---- Constants ----
    private static final String TOPIC = "payments.events";
    private static final String DEBTOR_IBAN   = "CH9300762011623852957";   // valid
    private static final String CREDITOR_IBAN = "CH5604835012345678009";   // valid

    @TestConfiguration
    static class KafkaTopicConfig {
        @Bean
        org.apache.kafka.clients.admin.NewTopic paymentsTopic() {
            return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired SendPaymentUseCase useCase;

    private KafkaMessageListenerContainer<String, String> listener;
    private BlockingQueue<String> messages;

    @BeforeEach
    void setUp() {
        // Seed accounts
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS accounts(
                id UUID PRIMARY KEY,
                iban VARCHAR(34) UNIQUE NOT NULL,
                balance NUMERIC(19,2) NOT NULL,
                version BIGINT NOT NULL
            );
        """);
        jdbc.update("INSERT INTO accounts(id, iban, balance, version) VALUES (?,?,?,?) ON CONFLICT (iban) DO NOTHING",
                UUID.randomUUID(), DEBTOR_IBAN, new BigDecimal("100.00"), 0L);
        jdbc.update("INSERT INTO accounts(id, iban, balance, version) VALUES (?,?,?,?) ON CONFLICT (iban) DO NOTHING",
                UUID.randomUUID(), CREDITOR_IBAN, new BigDecimal("0.00"), 0L);

        // Start a simple listener container and wait for assignment
        Map<String, Object> props = KafkaTestUtils.consumerProps(  kafka.getBootstrapServers(), "it-consumer","true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        var cf = new DefaultKafkaConsumerFactory<String, String>(props);
        var containerProps = new ContainerProperties(TOPIC);

        messages = new ArrayBlockingQueue<>(10);
        listener = new KafkaMessageListenerContainer<>(cf, containerProps);
        listener.setupMessageListener((MessageListener<String, String>) rec -> messages.add(rec.value()));
        listener.start();

        // Ensure partitions assigned before producing (prevents “forever waiting”)
        ContainerTestUtils.waitForAssignment(listener, 1);
    }

    @AfterEach
    void tearDown() {
        if (listener != null) listener.stop();
    }

    @Test
    void endToEnd_success() throws Exception {
        var req = new SendPaymentRequest(
                DEBTOR_IBAN, CREDITOR_IBAN, "CHF", new BigDecimal("25.00"), "IT-flow"
        );

        var cmd = new SendPaymentUseCase.SendPaymentCommand(
                "idem-IT-1",
                req.debtorIban(),
                req.creditorIban(),
                req.currency(),
                req.amount(),
                req.remittanceInfo(),
                "req-123"
        );

        var result = useCase.send(cmd);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);

        var debtorBal  = jdbc.queryForObject("SELECT balance FROM accounts WHERE iban = ?", BigDecimal.class, DEBTOR_IBAN);
        var creditorBal= jdbc.queryForObject("SELECT balance FROM accounts WHERE iban = ?", BigDecimal.class, CREDITOR_IBAN);
        assertThat(debtorBal).isEqualByComparingTo("75.00");
        assertThat(creditorBal).isEqualByComparingTo("25.00");

        // Bound the wait; fail fast if no event arrives
        var msg = messages.poll(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(msg).as("Kafka payment event").isNotNull();
        assertThat(msg).contains("IT-flow");
    }
}
