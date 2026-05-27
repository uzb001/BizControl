package uz.bizcontrol.accounting;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable double-entry journal entry. Never edited or deleted once posted —
 * corrections are made by posting a reversing entry.
 */
@Entity
@Table(name = "journal_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JournalEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    @Column(length = 500)
    private String memo;

    /** SALE | PURCHASE | CASH | PAYMENT | MANUAL | REVERSAL */
    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    /** posted | reversed */
    @Column(nullable = false, length = 20)
    private String status = "posted";

    @Column(name = "reverses_entry_id")
    private Long reversesEntryId;

    @Column(name = "reversed_by_entry_id")
    private Long reversedByEntryId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();
}
