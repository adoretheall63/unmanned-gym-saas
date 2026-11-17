package com.wrgym.common.exception;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.api.R;
import com.wrgym.common.web.RequestContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 统一失败响应（docs/接口文档.md §1.4）。
 *
 * <p>HTTP 状态由 {@link ErrorCode#httpStatus()} 决定，body 固定为 R（含 requestId / serverTime / fieldErrors）。
 * <p>未预期异常只返回 INTERNAL_ERROR，不暴露堆栈、SQL、密钥或其他商家资源。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException ex) {
        ErrorCode code = ex.code();
        return ResponseEntity.status(code.httpStatus())
                .body(R.fail(code, ex.getMessage(), ex.fieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.httpStatus())
                .body(R.fail(ErrorCode.VALIDATION_ERROR, "参数校验失败", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleParamValidation(ConstraintViolationException ex) {
        List<FieldErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.httpStatus())
                .body(R.fail(ErrorCode.VALIDATION_ERROR, "参数校验失败", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.httpStatus())
                .body(R.fail(ErrorCode.VALIDATION_ERROR, "请求体无法解析或包含未定义字段", List.of()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<R<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        // If-Match 缺失按契约返回 428；其他必填 Header 缺失按 400
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            return ResponseEntity.status(ErrorCode.PRECONDITION_REQUIRED.httpStatus())
                    .body(R.fail(ErrorCode.PRECONDITION_REQUIRED));
        }
        List<FieldErrorItem> errors = List.of(new FieldErrorItem(ex.getHeaderName(), "缺少必填请求头"));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.httpStatus())
                .body(R.fail(ErrorCode.VALIDATION_ERROR, "缺少必填请求头", errors));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.httpStatus())
                .body(R.fail(ErrorCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.httpStatus())
                .body(R.fail(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<R<Void>> handleNoHandler(Exception ex) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.httpStatus())
                .body(R.fail(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error requestId={} ", RequestContext.currentRequestId(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(R.fail(ErrorCode.INTERNAL_ERROR));
    }

    private FieldErrorItem toFieldError(FieldError error) {
        String message = error.getDefaultMessage() == null ? "字段不合法" : error.getDefaultMessage();
        return new FieldErrorItem(error.getField(), message);
    }
}
