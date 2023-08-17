package com.morencorps.pocket;

import io.rsocket.ipc.RequestHandlingRSocket;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.rsocket.context.RSocketServerBootstrap;
import org.springframework.boot.rsocket.server.RSocketServerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@TestConfiguration
public class RsocketMockConfig {

    @Value(value = "${spring.rsocket.server.port}")
    private Integer port;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private BeanFactory beanFactory;
    @Autowired
    private DiscoveryClient discoveryClient;

    @Bean
    @Primary
    public RSocketServerBootstrap rSocketServerBootstrap(RSocketServerFactory factory) {
        TestRsocketServer server = new TestRsocketServer();
        return new RSocketServerBootstrap(factory, (setup, sendingSocket) -> Mono.just(
                new RequestHandlingRSocket()
                        .withEndpoint(server)
        ));
    }
}