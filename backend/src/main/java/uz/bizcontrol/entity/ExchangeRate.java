package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Manual per-company exchange rate from {@code currency} to the company base currency. */
@Entity
@Table(name = "exchange_rates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "currency"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExchangeRate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 10)
    private String currency;

    @Builder.Default
    @Column(name = "rate_to_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal rateToBase = BigDecimal.ONE;

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @PreUpdate @PrePersist
    public void touch() { this.updatedAt = LocalDateTime.now(); }
}
