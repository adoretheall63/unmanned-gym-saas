package com.wrgym.common.idempotency;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wrgym.common.entity.BaseEntity;

/**
 * 持久化写请求幂等记录（docs/数据库设计.md §1.25 api_request）。
 *
 * <p>唯一键 {@code (principal_type, principal_id, operation, idempotency_key)}；
 * {@code request_hash} 为规范化方法/路径/请求体的 SHA-256；{@code result_snapshot} 只存脱敏结果，
 * 不含 JWT、口令、人脸原图、临时密码与播放凭据。
 */
@TableName("api_request")
public class ApiRequestEntity extends BaseEntity {

    private String principalType;
    private Long principalId;
    private String operation;
    private String idempotencyKey;
    private String requestHash;
    private String resourceType;
    private Long resourceId;
    private Integer httpStatus;
    private String resultSnapshot;

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public Long getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(Long principalId) {
        this.principalId = principalId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getResultSnapshot() {
        return resultSnapshot;
    }

    public void setResultSnapshot(String resultSnapshot) {
        this.resultSnapshot = resultSnapshot;
    }
}
