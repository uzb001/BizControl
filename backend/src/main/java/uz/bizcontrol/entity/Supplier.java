package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "suppliers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;
    private String phone;
    private String email;
    @Column(name = "tax_id")
    private String taxId;
    @Column(name = "bank_account")
    private String bankAccount;
    private String country;
    /** Optional FK to the structured {@link Country} entity (V15+). Coexists with legacy free-text country. */
    @Column(name = "country_id")
    private Long countryId;
    private String city;
    private String address;
    private String currency = "UZS";

    @Column(name = "current_debt")
    private BigDecimal currentDebt = BigDecimal.ZERO;

    private String status = "active";
    private String notes;

    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_by") private Long updatedBy;

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
