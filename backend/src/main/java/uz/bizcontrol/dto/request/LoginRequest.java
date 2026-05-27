package uz.bizcontrol.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank private String login;  // email or phone
    @NotBlank private String password;
}
