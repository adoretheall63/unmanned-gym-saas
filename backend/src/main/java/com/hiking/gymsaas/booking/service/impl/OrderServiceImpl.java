package com.hiking.gymsaas.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.gymsaas.booking.mapper.AdminOrderMapper;
import com.hiking.gymsaas.booking.mapper.BookingMapper;
import com.hiking.gymsaas.booking.mapper.BookingOrderMapper;
import com.hiking.gymsaas.booking.model.dto.OrderRequests;
import com.hiking.gymsaas.booking.model.entity.BookingOrder;
import com.hiking.gymsaas.booking.model.vo.OrderViews;
import com.hiking.gymsaas.booking.service.OrderRules;
import com.hiking.gymsaas.booking.service.OrderService;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.idempotency.IdempotencyService;
import com.hiking.gymsaas.common.page.PageQuery;
import com.hiking.gymsaas.common.page.PageResult;
import com.hiking.gymsaas.common.security.CurrentIdentity;
import com.hiking.gymsaas.common.util.Ids;
import com.hiking.gymsaas.payment.model.entity.PaymentRecord;
import com.hiking.gymsaas.payment.service.PaymentService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * §6 首闭环实现（Q030–Q039、Q085）。
 *
 * <p>下单：锁顾客 → 锁时段 → 黑名单/信用限制/欠费/跨店重叠校验 → 原子占位（reserved_count + 1，
 * 条件 reserved_count &lt; capacity 防超卖）→ 写订单与规则快照；支付截止 = min(下单+15 分钟, 时段开始)。
 * <p>模拟支付：锁订单 → 截止前才可确认；首次写 MOCK 流水并把订单置 RESERVED；
 * 已支付再确认（含换幂等键）返回原流水，business_key 唯一键防重复收费；零元不建流水。
 */
@Service
@Profile("!bootstrap")
public class OrderServiceImpl implements OrderService {
    private final BookingMapper reader;
    private final BookingOrderMapper orders;
    private final AdminOrderMapper adminOrders;
    private final PaymentService payments;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public OrderServiceImpl(BookingMapper reader, BookingOrderMapper orders, AdminOrderMapper adminOrders,
                            PaymentService payments, IdempotencyService idempotency, ObjectMapper json) {
        this.reader = reader;
        this.orders = orders;
        this.adminOrders = adminOrders;
        this.payments = payments;
        this.idempotency = idempotency;
        this.json = json;
    }

    @Override
    public IdempotencyService.Outcome<OrderViews.Order> create(OrderRequests.Create input, String key) {
        long userId = CurrentIdentity.customer().id();
        long slotId = Ids.parse(input.slotId());
        return idempotency.execute("createOrder", "POST", "/api/v1/app/orders", key, input,
                OrderViews.Order.class, () -> {
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    reader.lockCustomerUser(userId);
                    var slot = reader.lockSlot(slotId);
                    if (slot == null || !"OPEN".equals(slot.getStatus()) || slot.getStartAt() == null
                            || !slot.getStartAt().isAfter(now)) {
                        throw new BusinessException(ErrorCode.SLOT_UNAVAILABLE);
                    }
                    var store = reader.store(slot.getStoreId());
                    if (store == null) throw new BusinessException(ErrorCode.NOT_FOUND);
                    var merchant = reader.merchant(store.getMerchantId());
                    if (merchant == null || !"ACTIVE".equals(merchant.status())
                            || !"OPEN".equals(store.getStatus())
                            || Boolean.TRUE.equals(store.getAdmissionPaused())) {
                        throw new BusinessException(ErrorCode.SLOT_UNAVAILABLE);
                    }
                    // 首次预约建立商家顾客关系（booking_order 外键依赖该关系，Q017 黑名单按商家生效）
                    reader.ensureMerchantCustomer(store.getMerchantId(), userId);
                    var customer = reader.merchantCustomer(store.getMerchantId(), userId);
                    if (customer != null) {
                        if (Boolean.TRUE.equals(customer.blocked())) {
                            throw new BusinessException(ErrorCode.MERCHANT_BLOCKED);
                        }
                        if (customer.bookingRestrictedUntil() != null
                                && customer.bookingRestrictedUntil().isAfter(now)) {
                            throw new BusinessException(ErrorCode.BOOKING_RESTRICTED);
                        }
                    }
                    if (reader.outstandingPayments(store.getMerchantId(), userId) > 0) {
                        throw new BusinessException(ErrorCode.OUTSTANDING_PAYMENT);
                    }
                    if (reader.overlappingOrders(userId, slot.getStartAt(), slot.getEndAt()) > 0) {
                        throw new BusinessException(ErrorCode.BOOKING_OVERLAP);
                    }
                    if (reader.holdCapacity(slotId) != 1) {
                        throw new BusinessException(ErrorCode.SLOT_UNAVAILABLE);
                    }
                    var order = new BookingOrder();
                    order.setMerchantId(store.getMerchantId());
                    order.setStoreId(store.getId());
                    order.setUserId(userId);
                    order.setSlotId(slotId);
                    order.setOrderNo(OrderRules.orderNo(now));
                    order.setBenefitType("TIME_FEE");
                    order.setStartAt(slot.getStartAt());
                    order.setEndAt(slot.getEndAt());
                    order.setPayDeadline(OrderRules.payDeadline(now, slot.getStartAt()));
                    // 金额取该时段价格（4.15 可按时段调价），回退门店默认价；客户端永远不能传入金额
                    order.setBaseAmountCent(slot.getPriceCent() != null ? slot.getPriceCent()
                            : OrderRules.baseAmount(store));
                    order.setOvertimePriceCent(store.getOvertimePriceCent());
                    order.setOvertimeCapCent(store.getOvertimeCapCent());
                    order.setRuleSnapshot(json.valueToTree(OrderRules.ruleSnapshot(store, merchant.name())));
                    order.setStatus("PENDING_PAYMENT");
                    order.setCapacityHeld(true);
                    order.setCancelStage("NONE");
                    order.setReviewRequired(false);
                    orders.insert(order);
                    return new IdempotencyService.Outcome<>(201, view(order, store.getName()));
                });
    }

    @Override
    public PageResult<OrderViews.Order> listMine(PageQuery query, String status) {
        long userId = CurrentIdentity.customer().id();
        var filter = new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getUserId, userId)
                .eq(status != null && !status.isBlank(), BookingOrder::getStatus, status)
                .orderByDesc(BookingOrder::getCreatedAt, BookingOrder::getId);
        var page = orders.selectPage(query.toPage(), filter);
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(),
                withStoreNames(page.getRecords()));
    }

    @Override
    public OrderViews.Order getMine(String orderId) {
        long userId = CurrentIdentity.customer().id();
        var order = orders.selectById(Ids.parse(orderId));
        if (order == null || !order.getUserId().equals(userId)) throw new BusinessException(ErrorCode.NOT_FOUND);
        return withStoreNames(List.of(order)).get(0);
    }

    @Override
    public IdempotencyService.Outcome<OrderViews.OrderConfirmation> mockPayment(String orderId, String key) {
        long id = Ids.parse(orderId);
        long userId = CurrentIdentity.customer().id();
        return idempotency.execute("confirmMockPayment", "POST",
                "/api/v1/app/orders/" + id + "/mock-payment", key, Map.of(), OrderViews.OrderConfirmation.class,
                () -> {
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    var order = orders.lockOrder(id);
                    if (order == null || !order.getUserId().equals(userId)) {
                        throw new BusinessException(ErrorCode.NOT_FOUND);
                    }
                    boolean zero = order.getBaseAmountCent() == null || order.getBaseAmountCent() == 0;
                    if ("PENDING_PAYMENT".equals(order.getStatus())) {
                        // 只接受截止之前完成的支付；截止 ≤ 时段开始，串行判断后至多一条转换成功
                        if (!now.isBefore(order.getPayDeadline())) {
                            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
                        }
                        PaymentRecord payment = zero ? null
                                : payments.recordOrderFee(order.getMerchantId(), userId, id, order.getBaseAmountCent());
                        orders.update(null, new LambdaUpdateWrapper<BookingOrder>().eq(BookingOrder::getId, id)
                                .set(BookingOrder::getStatus, "RESERVED")
                                .set(BookingOrder::getConfirmedAt, now));
                        var reloaded = orders.selectById(id);
                        return new IdempotencyService.Outcome<>(200, new OrderViews.OrderConfirmation(
                                withStoreNames(List.of(reloaded)).get(0),
                                payment == null ? null : payment(payment), zero));
                    }
                    if (List.of("RESERVED", "IN_USE", "FINISHED").contains(order.getStatus())) {
                        // 换键重复确认：返回原结果，不重复收费
                        PaymentRecord existing = payments.orderReceipt(id);
                        return new IdempotencyService.Outcome<>(200, new OrderViews.OrderConfirmation(
                                withStoreNames(List.of(order)).get(0),
                                existing == null ? null : payment(existing), zero));
                    }
                    throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
                });
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ORDER_READ')")
    public PageResult<OrderViews.Order> adminList(PageQuery query, String storeId, String status) {
        var filter = new LambdaQueryWrapper<BookingOrder>()
                .eq(storeId != null && !storeId.isBlank(), BookingOrder::getStoreId,
                        storeId == null || storeId.isBlank() ? null : Ids.parse(storeId))
                .eq(status != null && !status.isBlank(), BookingOrder::getStatus, status)
                .orderByDesc(BookingOrder::getCreatedAt, BookingOrder::getId);
        var page = adminOrders.selectPage(query.toPage(), filter);
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(),
                withStoreNames(page.getRecords()));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ORDER_READ')")
    public OrderViews.Order adminGet(String orderId) {
        var order = adminOrders.selectById(Ids.parse(orderId));
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return withStoreNames(List.of(order)).get(0);
    }

    // ---------- 视图 ----------

    private List<OrderViews.Order> withStoreNames(List<BookingOrder> orders) {
        if (orders.isEmpty()) return List.of();
        Map<Long, String> names = reader.storeNames(
                        orders.stream().map(BookingOrder::getStoreId).distinct().toList()).stream()
                .collect(Collectors.toMap(BookingMapper.StoreNameRow::id, BookingMapper.StoreNameRow::name));
        return orders.stream().map(order -> view(order, names.get(order.getStoreId()))).toList();
    }

    private OrderViews.Order view(BookingOrder order, String storeName) {
        return new OrderViews.Order(order.getId().toString(), order.getOrderNo(),
                order.getMerchantId().toString(), order.getStoreId().toString(), storeName,
                order.getSlotId().toString(), at(order.getStartAt()), at(order.getEndAt()),
                order.getBenefitType(), order.getBaseAmountCent() == null ? 0 : order.getBaseAmountCent(),
                at(order.getPayDeadline()), order.getStatus(), Boolean.TRUE.equals(order.getCapacityHeld()),
                at(order.getConfirmedAt()), order.getCancelStage(), at(order.getCancelRequestedAt()),
                at(order.getCancelledAt()), order.getCancelReason(), Boolean.TRUE.equals(order.getReviewRequired()),
                order.getRuleSnapshot(), "MOCK");
    }

    private OrderViews.Payment payment(PaymentRecord record) {
        return new OrderViews.Payment(record.getId().toString(), record.getPaymentNo(), record.getKind(),
                record.getChannel(), record.getAmountCent(), record.getStatus(), at(record.getCompletedAt()));
    }

    private OffsetDateTime at(LocalDateTime utc) { return utc == null ? null : utc.atOffset(ZoneOffset.UTC); }
}
