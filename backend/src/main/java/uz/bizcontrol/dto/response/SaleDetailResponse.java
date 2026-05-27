package uz.bizcontrol.dto.response;

import lombok.Builder;
import lombok.Data;
import uz.bizcontrol.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SaleDetailResponse {

    private Long id;
    private String saleNumber;
    private LocalDateTime saleDate;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private String currency;
    private String note;

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;
    private BigDecimal unpaidAmount;
    private BigDecimal totalProfit;

    private CustomerInfo customer;
    private List<ItemInfo> items;
    private List<PaymentInfo> payments;

    @Data
    @Builder
    public static class CustomerInfo {
        private Long id;
        private String name;
        private String phone;
        private String customerType;
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
        private BigDecimal sellingPrice;
        private BigDecimal purchaseCost;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
        private BigDecimal profitAmount;
    }

    @Data
    @Builder
    public static class PaymentInfo {
        private Long id;
        private String type;       // "income" or "reversal"
        private BigDecimal amount;
        private String paymentSource;
        private String currency;
        private String note;
        private String status;
        private LocalDateTime createdAt;
    }

    public static SaleDetailResponse from(Sale sale, List<CashTransaction> payments) {
        List<ItemInfo> itemInfos = sale.getItems() == null ? List.of() :
                sale.getItems().stream().map(i -> ItemInfo.builder()
                        .id(i.getId())
                        .productId(i.getProduct() != null ? i.getProduct().getId() : null)
                        .productName(i.getProduct() != null ? i.getProduct().getName() : null)
                        .productSku(i.getProduct() != null ? i.getProduct().getSku() : null)
                        .unit(i.getProduct() != null ? i.getProduct().getUnit() : null)
                        .quantity(i.getQuantity())
                        .sellingPrice(i.getSellingPrice())
                        .purchaseCost(i.getPurchaseCost())
                        .discountAmount(i.getDiscountAmount())
                        .totalAmount(i.getTotalAmount())
                        .profitAmount(i.getProfitAmount())
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

        CustomerInfo customerInfo = null;
        if (sale.getCustomer() != null) {
            Customer c = sale.getCustomer();
            customerInfo = CustomerInfo.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .phone(c.getPhone())
                    .customerType(c.getCustomerType())
                    .currentDebt(c.getCurrentDebt())
                    .build();
        }

        return SaleDetailResponse.builder()
                .id(sale.getId())
                .saleNumber(sale.getSaleNumber())
                .saleDate(sale.getSaleDate())
                .status(sale.getStatus())
                .paymentStatus(sale.getPaymentStatus())
                .paymentMethod(sale.getPaymentMethod())
                .currency(sale.getCurrency())
                .note(sale.getNote())
                .totalAmount(sale.getTotalAmount())
                .discountAmount(sale.getDiscountAmount())
                .paidAmount(sale.getPaidAmount())
                .unpaidAmount(sale.getUnpaidAmount())
                .totalProfit(sale.getTotalProfit())
                .customer(customerInfo)
                .items(itemInfos)
                .payments(paymentInfos)
                .build();
    }
}
