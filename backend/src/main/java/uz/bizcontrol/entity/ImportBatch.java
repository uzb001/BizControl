package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportBatch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String module; // products, customers, suppliers

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "success_rows")
    private int successRows;

    @Column(name = "failed_rows")
    private int failedRows;

    @Column(name = "duplicate_rows")
    private int duplicateRows;

    private String status = "pending"; // pending, preview, confirmed, rolled_back, failed

    @Column(name = "rollback_data", columnDefinition = "TEXT")
    private String rollbackData; // JSON array of created IDs

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary; // JSON array of {row, error}

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;
}
