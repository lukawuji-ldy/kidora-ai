package com.wuji.kidora.ai.agent.model;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.http.HttpClient.Version;
import java.time.Duration;

/**
 * LLM HTTP 客户端工厂。
 *
 * @author liudy
 */
public final class LlmHttpClients {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private LlmHttpClients() {
    }

    public static RestClient.Builder restClientBuilder(Duration timeout) {
        Duration readTimeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(60) : timeout;
        java.net.http.HttpClient jdk = java.net.http.HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    public static WebClient.Builder webClientBuilder(Duration timeout) {
        Duration readTimeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(60) : timeout;
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(CONNECT_TIMEOUT.toMillis()))
                .responseTimeout(readTimeout);
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    public static RetryTemplate noInnerRetry() {
        return RetryTemplate.builder().maxAttempts(1).build();
    }
}
