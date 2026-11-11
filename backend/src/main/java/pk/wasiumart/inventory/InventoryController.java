package pk.wasiumart.inventory;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }

    @GetMapping("/inventory") public List<GroceryRecord> list() { return service.list(); }
    @GetMapping("/inventory/{id}") public GroceryRecord get(@PathVariable long id) {
        return service.find(id).orElseThrow(() -> new NoSuchElementException("Record not found"));
    }
    @PostMapping("/inventory") public ResponseEntity<GroceryRecord> create(@RequestBody InventoryInput input) {
        GroceryRecord created = service.create(input);
        return ResponseEntity.created(URI.create("/api/inventory/" + created.id())).body(created);
    }
    @PutMapping("/inventory/{id}") public GroceryRecord update(@PathVariable long id, @RequestBody InventoryInput input) { return service.update(id, input); }
    public record StockAdjustment(java.math.BigDecimal delta, String reason) {}
    @PostMapping("/inventory/{id}/adjust") public GroceryRecord adjust(@PathVariable long id, @RequestBody StockAdjustment input) { return service.adjustStock(id, input.delta(), input.reason()); }
    @DeleteMapping("/inventory/{id}") public GroceryRecord delete(@PathVariable long id) { return service.delete(id); }
    @GetMapping("/queue") public InventoryService.QueueView queue() { return service.queue(); }
    public record DequeueRequest(Long expectedId) {}
    @PostMapping("/queue/dequeue") public GroceryRecord dequeue(@RequestBody(required=false) DequeueRequest input) { return service.dequeue(input == null ? null : input.expectedId()); }
    @GetMapping("/dashboard") public InventoryService.Dashboard dashboard() { return service.dashboard(); }
    @GetMapping("/activity") public List<InventoryService.Activity> activity() { return service.activity(); }
}
