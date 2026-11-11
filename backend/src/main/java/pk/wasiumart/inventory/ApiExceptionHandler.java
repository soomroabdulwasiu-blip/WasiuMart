package pk.wasiumart.inventory;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> invalid(RuntimeException ex) { return error(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String,String>> malformed(Exception ex) { return error(HttpStatus.BAD_REQUEST, "Invalid request format"); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String,String>> notFound(Exception ex) { return error(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler(InventoryService.QueueEmptyException.class)
    ResponseEntity<Map<String,String>> empty(Exception ex) { return error(HttpStatus.CONFLICT, ex.getMessage()); }
    private ResponseEntity<Map<String,String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? "Invalid request" : message));
    }
}
