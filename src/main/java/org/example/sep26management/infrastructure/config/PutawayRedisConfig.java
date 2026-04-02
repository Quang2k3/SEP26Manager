package org.example.sep26management.infrastructure.config;

import org.example.sep26management.application.event.PutawayEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Đăng ký Redis Pub/Sub listener cho putaway task events.
 *
 * Tách khỏi RedisConfig để dễ maintain và feature-flag.
 * Channel "putaway-task-events" khớp với PutawayEventPublisher.CHANNEL.
 */
@Configuration
public class PutawayRedisConfig {

    @Bean
    public MessageListenerAdapter putawayEventListenerAdapter(PutawayEventSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public ChannelTopic putawayTaskTopic() {
        return new ChannelTopic("putaway-task-events");
    }

    @Bean
    public RedisMessageListenerContainer putawayRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter putawayEventListenerAdapter,
            ChannelTopic putawayTaskTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(putawayEventListenerAdapter, putawayTaskTopic);
        return container;
    }
}