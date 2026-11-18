package com.hiking.gymsaas.operation.model.dto;

import com.hiking.gymsaas.common.page.PageQuery;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class AuditQuery extends PageQuery {
    @Pattern(regexp = "[A-Z_]{1,32}") private String targetType;
    @Pattern(regexp = "[1-9][0-9]{0,18}") private String targetId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime to;
}
