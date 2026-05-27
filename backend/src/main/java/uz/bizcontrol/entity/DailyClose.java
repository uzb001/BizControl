package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_closes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "close_date"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyClose {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "close_date", nullable = false)
    private LocalDate closeDate;

    @Column(name = "expected_cash")
    private BigDecimal expectedCash = BigDecimal.ZERO;

    @Column(name = "actual_cash")
    private BigDecimal actualCash = BigDecimal.ZERO;

    @Column(name = "cash_difference")
    private BigDecimal cashDifference = BigDecimal.ZERO;  // actualCash - expectedCash

    @Column(name = "total_sales")
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_expenses")
    private BigDecimal totalExpenses = BigDecimal.ZERO;

    @Column(name = "total_profit")
    private BigDecimal totalProfit = BigDecimal.ZERO;

    @Column(name = "responsible_user_id")
    private Long responsibleUserId;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private String status = "open";   // open, closed

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
