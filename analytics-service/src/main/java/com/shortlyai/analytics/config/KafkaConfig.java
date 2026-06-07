package com.shortlyai.analytics.config;

import com.shortlyai.analytics.events.UrlClickedEvent;
import com.shortlyai.analytics.events.UrlCreatedEvent;
import com.shortlyai.analytics.events.UrlDeletedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Shared base props — both factories use these
    private Map<String, Object> baseProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "analytics-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }

    // url.clicks factory
    @Bean
    public ConsumerFactory<String, UrlClickedEvent> clickedConsumerFactory() {
        JacksonJsonDeserializer<UrlClickedEvent> deser =
                new JacksonJsonDeserializer<>(UrlClickedEvent.class);
        deser.addTrustedPackages("com.shortlyai.*");
        deser.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(baseProps(), new StringDeserializer(), deser);
    }

    // Default factory name — picked up automatically by ClickConsumer
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlClickedEvent>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UrlClickedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(clickedConsumerFactory());
        return factory;
    }

    // url.deleted factory

    @Bean
    public ConsumerFactory<String, UrlDeletedEvent> deletedConsumerFactory() {
        JacksonJsonDeserializer<UrlDeletedEvent> deser =
                new JacksonJsonDeserializer<>(UrlDeletedEvent.class);
        deser.addTrustedPackages("com.shortlyai.*");
        deser.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(baseProps(), new StringDeserializer(), deser);
    }

    // Named — referenced explicitly in DeleteConsumer's containerFactory
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlDeletedEvent>
    deletedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UrlDeletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(deletedConsumerFactory());
        return factory;
    }

    // url.created factory

    @Bean
    public ConsumerFactory<String, UrlCreatedEvent> createdConsumerFactory() {
        JacksonJsonDeserializer<UrlCreatedEvent> deser =
                new JacksonJsonDeserializer<>(UrlCreatedEvent.class);
        deser.addTrustedPackages("com.shortlyai.*");
        deser.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(baseProps(), new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlCreatedEvent>
    createdKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UrlCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(createdConsumerFactory());
        return factory;
    }
}