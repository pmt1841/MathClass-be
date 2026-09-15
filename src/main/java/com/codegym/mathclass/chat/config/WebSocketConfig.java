package com.codegym.mathclass.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSecurityInterceptor webSocketSecurityInterceptor;
    private final WebSocketHandshakeInterceptor webSocketHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        taskScheduler.initialize();

        // Topic broadcast message từ server -> client kèm heartbeat ping-pong 10s
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(taskScheduler);
        // Prefix cho các tin nhắn client gửi lên server qua @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(webSocketHandshakeInterceptor)
                .withSockJS();

        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(webSocketHandshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketSecurityInterceptor);
    }
}
