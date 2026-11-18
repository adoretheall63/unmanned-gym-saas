package com.hiking.gymsaas.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.gymsaas.account.model.entity.AdminUser;
import com.hiking.gymsaas.booking.model.entity.BookingOrder;
import com.hiking.gymsaas.device.model.entity.AccessCredential;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

class EntitySchemaTest {
    private static final Map<String, String> MODULES = new LinkedHashMap<>();
    static {
        register("account", "merchant customer_user admin_user merchant_customer credit_event face_profile");
        register("store", "store store_slot");
        register("booking", "booking_order");
        register("card", "card_product member_card card_ledger");
        register("visit", "visit_session visit_order visit_settlement settlement_revision");
        register("payment", "payment_record");
        register("device", "device device_person access_credential credential_window device_operation device_event");
        register("operation", "business_event review_task");
        register("common.idempotency", "api_request");
    }
    private static void register(String module, String names) {
        for (String table : names.split(" ")) MODULES.put(table, module);
    }

    @Test
    void allColumnsMatchDdlTypesJsonHandlersAndReadOnlyStrategies() throws Exception {
        String sql;
        try (var in = getClass().getResourceAsStream("/schema.sql")) {
            assertNotNull(in);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var tables = Pattern.compile("CREATE TABLE `(\\w+)` \\((.*?)\\) ENGINE=", Pattern.DOTALL).matcher(sql);
        int tableCount = 0, columnCount = 0;
        while (tables.find()) {
            String table = tables.group(1);
            String className = Arrays.stream(table.split("_")).map(x -> Character.toUpperCase(x.charAt(0)) + x.substring(1)).reduce("", String::concat);
            Class<?> entity = Class.forName("com.hiking.gymsaas." + MODULES.get(table) + ".model.entity." + className);
            TableName mapping = entity.getAnnotation(TableName.class);
            assertEquals(table, mapping.value());
            assertTrue(mapping.autoResultMap());
            var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), table);
            assistant.setCurrentNamespace(entity.getName());
            var info = TableInfoHelper.initTableInfo(assistant, entity);
            var fields = new HashMap<String, Field>();
            for (Field field : entity.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                var key = field.getAnnotation(TableId.class);
                var col = field.getAnnotation(TableField.class);
                fields.put(key != null ? key.value() : col.value(), field);
            }
            var columns = Pattern.compile("^  `(\\w+)` ([^\\n]+)", Pattern.MULTILINE).matcher(tables.group(2));
            int count = 0;
            while (columns.find()) {
                String name = columns.group(1), definition = columns.group(2);
                Field field = fields.get(name);
                assertNotNull(field, table + "." + name);
                Class<?> expected = definition.startsWith("BIGINT") || definition.startsWith("INT UNSIGNED") ? Long.class
                        : definition.startsWith("INT") || definition.startsWith("SMALLINT") ? Integer.class
                        : definition.startsWith("TINYINT") ? Boolean.class
                        : definition.startsWith("DATETIME") ? LocalDateTime.class
                        : definition.startsWith("TIME") ? LocalTime.class
                        : definition.startsWith("DECIMAL") ? BigDecimal.class
                        : definition.startsWith("JSON") ? field.getType() : String.class;
                assertEquals(expected, field.getType(), table + "." + name);
                if (name.equals("id")) {
                    assertEquals(IdType.AUTO, field.getAnnotation(TableId.class).type());
                } else {
                    var col = field.getAnnotation(TableField.class);
                    if (definition.startsWith("JSON")) {
                        assertEquals(JacksonTypeHandler.class, col.typeHandler());
                        assertTrue(field.getType() == List.class || field.getType() == com.fasterxml.jackson.databind.JsonNode.class);
                    }
                    if (definition.contains("GENERATED ALWAYS") || name.equals("created_at") || name.equals("updated_at")) {
                        assertEquals(FieldStrategy.NEVER, col.insertStrategy());
                        assertEquals(FieldStrategy.NEVER, col.updateStrategy());
                        var mapped = info.getFieldList().stream().filter(f -> f.getColumn().equals(name)).findFirst().orElseThrow();
                        assertFalse(mapped.isWithInsertFill());
                    }
                }
                count++;
            }
            assertEquals(count, fields.size());
            columnCount += count;
            tableCount++;
        }
        assertEquals(26, tableCount);
        assertEquals(369, columnCount);
    }

    @Test
    void entityIdsAreStringsButMoneyRemainsNumericAndSecretsAreExcluded() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var order = new BookingOrder();
        order.setId(Long.MAX_VALUE);
        order.setBaseAmountCent(1000L);
        var json = mapper.readTree(mapper.writeValueAsString(order));
        assertEquals("9223372036854775807", json.get("id").asText());
        assertTrue(json.get("id").isTextual());
        assertTrue(json.get("baseAmountCent").isIntegralNumber());
        var admin = new AdminUser();
        admin.setPasswordHash("secret-hash");
        assertFalse(mapper.readTree(mapper.writeValueAsString(admin)).has("passwordHash"));
        assertFalse(admin.toString().contains("secret-hash"));
        var credential = new AccessCredential();
        credential.setSecretCiphertext("secret-ciphertext");
        assertFalse(mapper.readTree(mapper.writeValueAsString(credential)).has("secretCiphertext"));
    }
}
