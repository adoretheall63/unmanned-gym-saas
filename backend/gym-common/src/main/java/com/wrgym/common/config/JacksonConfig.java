package com.wrgym.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * JSON 序列化约定（docs/接口文档.md §1.1）：
 * <ul>
 *   <li>金额与 ID：JSON 中 Long 一律输出为字符串，避免 JS 精度丢失；</li>
 *   <li>时间：数据库为 UTC，输出带时区 ISO 8601（如 {@code 2026-11-16T14:00:00Z}）；</li>
 *   <li>时间戳格式默认关闭。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer gymJacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("gym-json");
            // Long / long -> 字符串
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            // 时间
            module.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
            module.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
            module.addSerializer(OffsetDateTime.class, new UtcOffsetDateTimeSerializer());
            builder.modulesToInstall(module);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    /** LocalDateTime（UTC 存储）序列化为带 Z 的 ISO 8601。 */
    static final class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    /** 接受带时区的 ISO 8601（换算到 UTC）或不带时区的本地时间（按 UTC 处理）。 */
    static final class UtcLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            if (text.endsWith("Z") || text.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return OffsetDateTime.parse(text)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            }
            return LocalDateTime.parse(text);
        }
    }

    static final class UtcOffsetDateTimeSerializer extends OffsetDateTimeSerializer {
        UtcOffsetDateTimeSerializer() {
            super();
        }

        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.withOffsetSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
