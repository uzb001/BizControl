package uz.bizcontrol.dto.response;

import lombok.Builder;
import lombok.Data;
import uz.bizcontrol.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseDetailResponse {

    private Long id;
    private String purchaseNumber;
    private LocalDateTime purchaseDate;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private String currency;
    private String note;

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal additionalCost;
    private BigDecimal paidAmount;
    private BigDecimal unpaidAmount;

    private SupplierInfo supplier;
    private List<ItemInfo> items;
    private List<PaymentInfo> payments;

    @Data
    @Builder
    public static class SupplierInfo {
        private Long id;
        private String name;
        private String phone;
        private String contactPerson;
        private BigDecimal currentDebt;
    }

    @Data
    @Builder
    public static class ItemInfo {
        private Long id;
        private Long productId;
        private String productName;
        private String productSku;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal purchasePrice;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    public static class PaymentInfo {
        private Long id;
        private String type;
        private BigDecimal amount;
        private String paymentSource;
        private String currency;
        private String note;
        private String status;
        private LocalDateTime createdAt;
    }

    public static PurchaseDetailResponse from(Purchase purchase, List<CashTransaction> payments) {
        List<ItemInfo> itemInfos = purchase.getItems() == null ? List.of() :
                purchase.getItems().stream().map(i -> ItemInfo.builder()
                        .id(i.getId())
                        .productId(i.getProduct() != null ? i.getProduct().getId() : null)
                        .productName(i.getProduct() != null ? i.getProduct().getName() : null)
                        .productSku(i.getProduct() != null ? i.getProduct().getSku() : null)
                        .unit(i.getProduct() != null ? i.getProduct().getUnit() : null)
                        .quantity(i.getQuantity())
                        .purchasePrice(i.getPurchasePrice())
                        .discountAmount(i.getDiscountAmount())
                        .totalAmount(i.getTotalAmount())
                        .build()).toList();

        List<PaymentInfo> paymentInfos = payments == null ? List.of() :
                payments.stream().map(p -> PaymentInfo.builder()
                        .id(p.getId())
                        .type(p.getTransactionType())
                        .amount(p.getAmount())
                        .paymentSource(p.getPaymentSource())
                        .currency(p.getCurrency())
                        .note(p.getNote())
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .build()).toList();

        SupplierInfo supplierInfo = null;
        if (purchase.getSupplier() != null) {
            Supplier s = purchase.getSupplier();
            supplierInfo = SupplierInfo.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .phone(s.getPhone())
                    .contactPerson(s.getContactPerson())
                    .currentDebt(s.getCurrentDebt())
                    .build();
        }

        return PurchaseDetailResponse.builder()
                .id(purchase.getId())
                .purchaseNumber(purchase.getPurchaseNumber())
                .purchaseDate(purchase.getPurchaseDate())
                .status(purchase.getStatus())
                .paymentStatus(purchase.getPaymentStatus())
                .paymentMethod(purchase.getPaymentMethod())
                .currency(purchase.getCurrency())
                .note(purchase.getNote())
                .totalAmount(purchase.getTotalAmount())
                .discountAmount(purchase.getDiscountAmount())
                .additionalCost(purchase.getAdditionalCost())
                .paidAmount(purchase.getPaidAmount())
                .unpaidAmount(purchase.getUnpaidAmount())
                .supplier(supplierInfo)
                .items(itemInfos)
                .payments(paymentInfos)
                .build();
    }
}
