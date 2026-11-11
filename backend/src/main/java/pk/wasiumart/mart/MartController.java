package pk.wasiumart.mart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mart")
public class MartController {
    private final MartService service;
    public MartController(MartService service) { this.service=service; }
    public record Contact(String name, String phone, String address) {}
    public record Purchase(String requestKey, Long supplierId, String invoiceRef, String name, String category,
        BigDecimal quantity, String unit, BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal lowStockThreshold, LocalDate expiryDate) {}
    public record SaleLine(Long inventoryId, BigDecimal quantity) {}
    public record Sale(String requestKey, Long customerId, List<SaleLine> items, BigDecimal discount,
        BigDecimal taxRate, String paymentMethod, BigDecimal amountPaid) {}
    @GetMapping("/summary") public Map<String,Object> summary() { return service.summary(); }
    @GetMapping("/customers") public List<Map<String,Object>> customers() { return service.contacts("customers"); }
    @PostMapping("/customers") public Map<String,Object> addCustomer(@RequestBody Contact c) { return service.saveContact("customers",null,c); }
    @PutMapping("/customers/{id}") public Map<String,Object> editCustomer(@PathVariable long id,@RequestBody Contact c) { return service.saveContact("customers",id,c); }
    @GetMapping("/suppliers") public List<Map<String,Object>> suppliers() { return service.contacts("suppliers"); }
    @PostMapping("/suppliers") public Map<String,Object> addSupplier(@RequestBody Contact c) { return service.saveContact("suppliers",null,c); }
    @PutMapping("/suppliers/{id}") public Map<String,Object> editSupplier(@PathVariable long id,@RequestBody Contact c) { return service.saveContact("suppliers",id,c); }
    @GetMapping("/purchases") public List<Map<String,Object>> purchases() { return service.purchases(); }
    @PostMapping("/purchases") public Map<String,Object> purchase(@RequestBody Purchase p) { return service.purchase(p); }
    @GetMapping("/sales") public List<Map<String,Object>> sales() { return service.sales(); }
    @GetMapping("/sales/{id}") public Map<String,Object> sale(@PathVariable long id) { return service.sale(id); }
    @PostMapping("/sales") public Map<String,Object> checkout(@RequestBody Sale s) { return service.checkout(s); }
}
