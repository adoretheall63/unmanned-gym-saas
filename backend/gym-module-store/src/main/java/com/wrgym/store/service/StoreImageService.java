package com.wrgym.store.service;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.IdempotencyService;
import com.wrgym.common.web.Ids;
import com.wrgym.security.auth.SecurityUtils;
import com.wrgym.store.config.StorageProperties;
import com.wrgym.store.dto.StoreRuleDtos;
import com.wrgym.store.entity.StoreEntity;
import com.wrgym.store.mapper.StoreMapper;
import com.wrgym.store.storage.LocalObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 4.8 门店展示图片上传（M5 开发实现）。
 *
 * <p>校验：仅 JPEG/PNG/WebP（魔数）+ 可解码 + ≤5MiB；只接受公开展示图，不接受人脸；
 * objectKey 属于本商家（stores/{merchantId}/...），上传后用 4.7 引用。
 * <p>幂等：Idempotency-Key 的请求摘要包含文件字节摘要，重放不会重复写文件。
 */
@Service
public class StoreImageService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StoreMapper storeMapper;
    private final LocalObjectStorage objectStorage;
    private final StorageProperties storageProperties;
    private final IdempotencyService idempotencyService;

    public StoreImageService(StoreMapper storeMapper, LocalObjectStorage objectStorage,
                             StorageProperties storageProperties, IdempotencyService idempotencyService) {
        this.storeMapper = storeMapper;
        this.objectStorage = objectStorage;
        this.storageProperties = storageProperties;
        this.idempotencyService = idempotencyService;
    }

    public StoreRuleDtos.UploadResult upload(String storeIdRaw, String idempotencyKey, MultipartFile file) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        StoreEntity store = storeMapper.selectTenant(storeId, merchantId);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "file 必填");
        }
        if (file.getSize() > storageProperties.getMaxImageBytes()) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "读取上传文件失败");
        }
        String contentType = detectContentType(bytes);
        validateDecodable(bytes);
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        String objectKey = "stores/" + merchantId + "/" + LocalDate.now().format(DAY) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String fileDigest = sha256(bytes);
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = storeId + "\n" + bytes.length + "\n" + fileDigest;
        return idempotencyService.execute("admin.store.image", key, requestBody,
                StoreRuleDtos.UploadResult.class, () -> {
                    objectStorage.save(objectKey, bytes);
                    return new StoreRuleDtos.UploadResult(objectKey, objectStorage.publicUrl(objectKey),
                            contentType, bytes.length);
                });
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    private void validateDecodable(byte[] bytes) {
        try {
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
