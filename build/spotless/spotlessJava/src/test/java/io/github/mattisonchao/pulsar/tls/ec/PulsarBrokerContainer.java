package io.github.mattisonchao.pulsar.tls.ec;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

public class PulsarBrokerContainer extends GenericContainer<PulsarBrokerContainer> {
  public static final int BROKER_PORT = 6650;
  public static final int BROKER_SSL_PORT = 6651;
  public static final int BROKER_HTTP_PORT = 8080;
  public static final int BROKER_HTTPS_PORT = 8081;

  private final WaitAllStrategy waitAllStrategy = new WaitAllStrategy();

  public String getBrokerUrl() {
    return String.format("pulsar://%s:%s", getHost(), getMappedPort(BROKER_PORT));
  }

  public String getBrokerSSLUrl() {
    return String.format("pulsar+ssl://%s:%s", getHost(), getMappedPort(BROKER_SSL_PORT));
  }

  public String getHttpServiceUrl() {
    return String.format("http://%s:%s", getHost(), getMappedPort(BROKER_HTTP_PORT));
  }

  public String getHttpsServiceUrl() {
    return String.format("https://%s:%s", getHost(), getMappedPort(BROKER_HTTPS_PORT));
  }

  public PulsarBrokerContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    withExposedPorts(BROKER_PORT, BROKER_SSL_PORT, BROKER_HTTP_PORT, BROKER_HTTPS_PORT);
    setWaitStrategy(waitAllStrategy);
  }

  @Override
  protected void configure() {
    super.configure();
    setupCommandAndEnv();
  }

  @Override
  public String getContainerName() {
    return "broker";
  }

  protected void setupCommandAndEnv() {
    String standaloneBaseCommand =
        "/pulsar/bin/apply-config-from-env.py /pulsar/conf/standalone.conf "
            + "&& bin/pulsar standalone";

    boolean functionsWorkerEnabled = false;
    if (!functionsWorkerEnabled) {
      standaloneBaseCommand += " --no-functions-worker -nss";
    }

    withCommand("/bin/bash", "-c", standaloneBaseCommand);

    waitAllStrategy
        .withStrategy(Wait.forLogMessage(".*messaging service is ready.*", 1))
        .withStartupTimeout(Duration.ofMinutes(10));
  }
}
