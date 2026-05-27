package uz.bizcontrol.production;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** A stage in a production order's workflow. */
@Entity
@Table(name = "production_steps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionStep {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    /** pending | in_progress | completed | skipped */
    @Builder.Default
    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "responsible_user_id")
    private Long responsibleUserId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Builder.Default
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String note;
}
