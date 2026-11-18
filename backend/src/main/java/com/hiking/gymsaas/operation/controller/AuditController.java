package com.hiking.gymsaas.operation.controller;

import com.hiking.gymsaas.common.page.PageResult;
import com.hiking.gymsaas.common.util.Ids;
import com.hiking.gymsaas.common.web.ApiResponse;
import com.hiking.gymsaas.operation.model.dto.AuditQuery;
import com.hiking.gymsaas.operation.model.vo.AuditView;
import com.hiking.gymsaas.operation.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/admin/audit-events")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AUDIT_READ')")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }
    @GetMapping
    public ApiResponse<PageResult<AuditView>> list(@Valid @ModelAttribute AuditQuery query) { return ApiResponse.ok(audit.list(query)); }
    @GetMapping("/{auditId}")
    public ApiResponse<AuditView> detail(@PathVariable String auditId) { return ApiResponse.ok(audit.detail(Ids.parse(auditId))); }
}
