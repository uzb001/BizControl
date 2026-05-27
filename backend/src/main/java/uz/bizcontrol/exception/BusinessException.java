package uz.bizcontrol.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException notFound(String entity) {
        return new BusinessException(entity + " not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException forbidden() {
        return new BusinessException("Access denied", HttpStatus.FORBIDDEN);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN);
    }
}
