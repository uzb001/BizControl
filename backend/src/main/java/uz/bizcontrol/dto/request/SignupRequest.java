package uz.bizcontrol.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {
    @NotBlank private String fullName;
    private String email;
    private String phone;
    @NotBlank @Size(min = 6) private String password;
    @NotBlank private String companyName;
    private String businessType;
    private String mainCurrency = "UZS";
}
