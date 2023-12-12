package io.github.mattisonchao.pulsar.tls.ec;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.Cleanup;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ECCertificateTest {

  private final PulsarBrokerContainer broker =
      new PulsarBrokerContainer(
          DockerImageName.parse(
              "docker.cloudsmith.io/streamnative/cloud-pulsar/pulsar-cloud:2.10.5.9-99cb86"));

  private static final String CA_PATH =
      ECCertificateTest.class.getResource("/ec/ca.cert.pem").getPath();
  private static final String CLIENT_CERT_PATH =
      ECCertificateTest.class.getResource("/ec/client.cert.pem").getPath();
  private static final String CLIENT_KEY_PATH =
      ECCertificateTest.class.getResource("/ec/client.key-pk8.pem").getPath();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeClass
  public void init() throws JsonProcessingException {
    final Map<String, String> brokerClientAuthParams = new HashMap<>();
    brokerClientAuthParams.put("tlsCertFile", "/certs/broker_client.cert.pem");
    brokerClientAuthParams.put("tlsKeyFile", "/certs/broker_client.key-pk8.pem");
    final String brokerClientAuthParamStr = objectMapper.writeValueAsString(brokerClientAuthParams);
    broker
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/ec/ca.cert.pem"), "/certs/ca.cert.pem")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/ec/server.cert.pem"), "/certs/server.cert.pem")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/ec/server.key-pk8.pem"),
            "/certs/server.key-pk8.pem")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/ec/broker_client.cert.pem"),
            "/certs/broker_client.cert.pem")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/ec/broker_client.key-pk8.pem"),
            "/certs/broker_client.key-pk8.pem")
        .withEnv("PULSAR_PREFIX_tlsEnabled", "true")
        .withEnv("PULSAR_PREFIX_brokerServicePort", "6650")
        .withEnv("PULSAR_PREFIX_brokerServicePortTls", "6651")
        .withEnv("PULSAR_PREFIX_webServicePort", "8080")
        .withEnv("PULSAR_PREFIX_webServicePortTls", "8081")
        .withEnv("PULSAR_PREFIX_tlsTrustCertsFilePath", "/certs/ca.cert.pem")
        .withEnv("PULSAR_PREFIX_tlsCertificateFilePath", "/certs/server.cert.pem")
        .withEnv("PULSAR_PREFIX_tlsKeyFilePath", "/certs/server.key-pk8.pem")
        .withEnv("PULSAR_PREFIX_authenticationEnabled", "true")
        .withEnv(
            "PULSAR_PREFIX_authenticationProviders",
            "org.apache.pulsar.broker.authentication.AuthenticationProviderTls")
        //  -------- For standalone, because standalone will use internal client to init namespace
        .withEnv("PULSAR_PREFIX_brokerClientTlsEnabled", "true")
        .withEnv("PULSAR_PREFIX_brokerClientTrustCertsFilePath", "/certs/ca.cert.pem")
        .withEnv(
            "PULSAR_PREFIX_brokerClientAuthenticationPlugin",
            "org.apache.pulsar.client.impl.auth.AuthenticationTls")
        .withEnv("PULSAR_PREFIX_brokerClientAuthenticationParameters", brokerClientAuthParamStr);
    broker.start();
  }

  @Test
  public void testProduceAndConsume()
      throws PulsarClientException, JsonProcessingException {
    final Map<String, String> adminAuthParams = new HashMap<>();
    adminAuthParams.put("tlsCertFile", CLIENT_CERT_PATH);
    adminAuthParams.put("tlsKeyFile", CLIENT_KEY_PATH);
    final String adminAuthParamsStr = objectMapper.writeValueAsString(adminAuthParams);
    @Cleanup
    final PulsarClient client =
        PulsarClient.builder()
            .serviceUrl(broker.getBrokerSSLUrl())
            .tlsTrustCertsFilePath(CA_PATH)
            .authentication(
                "org.apache.pulsar.client.impl.auth.AuthenticationTls", adminAuthParamsStr)
            .build();

    @Cleanup
    final Consumer<byte[]> consumer =
        client
            .newConsumer()
            .topic("persistent://public/default/test-1")
            .subscriptionName("sub-1")
            .subscribe();
    @Cleanup
    final Producer<byte[]> producer =
        client.newProducer().topic("persistent://public/default/test-1").create();

    producer.send("test-message".getBytes(StandardCharsets.UTF_8));
    final Message<byte[]> message = consumer.receive();
    Assert.assertEquals(new String(message.getData(), StandardCharsets.UTF_8), "test-message");
  }
}
