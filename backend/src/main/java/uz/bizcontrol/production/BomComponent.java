package uz.bizcontrol.production;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** One raw-material line within a {@link BomTemplate}. */
@Entity
@Table(name = "bom_components")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BomComponent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bom_template_id", nullable = false)
    private Long bomTemplateId;

    @Column(name = "component_product_id", nullable = false)
    private Long componentProductId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Builder.Default
    private String unit = "piece";

    @Builder.Default
    @Column(name = "waste_percent", precision = 6, scale = 2)
    private BigDecimal wastePercent = BigDecimal.ZERO;

    @Column(name = "alternative_component_id")
    private Long alternativeComponentId;

    @Builder.Default
    @Column(name = "is_optional")
    private boolean optional = false;

    @Column(columnDefinition = "TEXT")
    private String note;
}
