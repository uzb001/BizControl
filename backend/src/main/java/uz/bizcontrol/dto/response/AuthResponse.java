package uz.bizcontrol.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Set;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String token;
    /**
     * Short-lived selection token returned when user belongs to multiple companies.
     * Must be sent back in the {@code POST /auth/select-company} request body.
     */
    private String selectionToken;
    private Long userId;
    private String fullName;
    private String email;
    private String phone;

    // Selected company context
    private Long companyId;
    private String companyName;
    private String role;          // role code
    private Long roleId;
    private Set<String> permissions;

    /**
     * When a user belongs to more than one company, token will be null
     * and this list will be populated so the frontend can show a company-picker.
     */
    private List<CompanyChoice> companies;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CompanyChoice {
        private Long   companyId;
        private String companyName;
        private String role;
        private Long   roleId;
    }
}
