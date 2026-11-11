package pk.wasiumart.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiTest {
    private static final Path DB = tempDb();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
    }
    private static Path tempDb() {
        try { Path dir = Files.createTempDirectory("wasiumart-test-"); dir.toFile().deleteOnExit(); Path db=dir.resolve("inventory.db"); db.toFile().deleteOnExit(); return db; }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @BeforeEach void clear() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_activity");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_sale_item");
        jdbc.update("DELETE FROM mart_sale_items");
        jdbc.update("DELETE FROM mart_sales");
        jdbc.update("DELETE FROM mart_purchases");
        jdbc.update("DELETE FROM mart_customers");
        jdbc.update("DELETE FROM mart_suppliers");
        jdbc.update("DELETE FROM inventory_activity");
        jdbc.update("DELETE FROM inventory_records");
    }
    private String input(String name, String quantity) {
        return "{\"name\":\"" + name + "\",\"category\":\"Food\",\"quantity\":" + quantity + ",\"unit\":\"kg\",\"unitPrice\":12.50,\"lowStockThreshold\":3,\"expiryDate\":null}";
    }
    private long create(String name, String quantity) throws Exception {
        String body = mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(input(name, quantity)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
    @Test void crudQueueAndActivityPreserveFifo() throws Exception {
        long a=create("Rice", "2"), b=create("Milk", "0"), c=create("Tea", "10");
        mvc.perform(put("/api/inventory/"+b).contentType(MediaType.APPLICATION_JSON).content(input("Fresh Milk", "4"))).andExpect(status().isOk());
        mvc.perform(get("/api/queue")).andExpect(status().isOk()).andExpect(jsonPath("$.front.id").value(a)).andExpect(jsonPath("$.rear.id").value(c)).andExpect(jsonPath("$.records[1].name").value("Fresh Milk"));
        mvc.perform(delete("/api/inventory/"+b)).andExpect(status().isOk());
        mvc.perform(post("/api/queue/dequeue")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(a));
        mvc.perform(get("/api/queue")).andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.front.id").value(c));
        mvc.perform(get("/api/activity")).andExpect(jsonPath("$[0].action").value("DEQUEUE")).andExpect(jsonPath("$[1].action").value("DELETE"));
        mvc.perform(get("/api/inventory/"+b)).andExpect(status().isNotFound());
    }
    @Test void databaseReconstructsQueueAndDashboardUsesExactMoney() throws Exception {
        long a=create("Rice", "2"), b=create("Milk", "0");
        var restarted = new InventoryService(jdbc, transactions);
        assertEquals(a, restarted.queue().front().id());
        assertEquals(b, restarted.queue().rear().id());
        assertEquals(0, restarted.dashboard().inventoryValue().compareTo(new java.math.BigDecimal("25.00")));
        assertEquals(1, restarted.dashboard().lowStock());
        assertEquals(1, restarted.dashboard().outOfStock());
    }
    @Test void invalidAndEmptyRequestsHaveClearStatuses() throws Exception {
        mvc.perform(post("/api/queue/dequeue")).andExpect(status().isConflict());
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(input("", "1"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(input("Rice", "-1"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content("{bad" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/inventory/99999").contentType(MediaType.APPLICATION_JSON).content(input("Rice", "1"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/inventory/99999")).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_records", Integer.class));
    }
    @Test void stockAdjustmentsAreAtomicAndPreserveQueueOrder() throws Exception {
        long first=create("Rice", "2"), second=create("Milk", "4");
        mvc.perform(post("/api/inventory/"+first+"/adjust").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":\"2.5\",\"reason\":\"Restock\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(4.5));
        mvc.perform(post("/api/inventory/"+first+"/adjust").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-5}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory/"+first+"/adjust").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory/"+first+"/adjust").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(3.5));
        mvc.perform(get("/api/queue")).andExpect(jsonPath("$.front.id").value(first)).andExpect(jsonPath("$.rear.id").value(second));
        mvc.perform(get("/api/activity")).andExpect(jsonPath("$[0].action").value("STOCK_OUT")).andExpect(jsonPath("$[1].action").value("STOCK_IN")).andExpect(jsonPath("$[1].reason").value("Restock")).andExpect(jsonPath("$[1].quantityDelta").value("2.5")).andExpect(jsonPath("$[1].quantityUnit").value("kg"));
    }
    @Test void staleFrontConfirmationCannotRemoveAnotherRecord() throws Exception {
        long a=create("Rice", "2"), b=create("Milk", "1");
        mvc.perform(post("/api/queue/dequeue").contentType(MediaType.APPLICATION_JSON).content("{\"expectedId\":"+a+"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/queue/dequeue").contentType(MediaType.APPLICATION_JSON).content("{\"expectedId\":"+a+"}"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/queue")).andExpect(jsonPath("$.front.id").value(b));
    }
    @Test void purchaseAndBillingAreAtomicAndIdempotent() throws Exception {
        var supplierResponse=mvc.perform(post("/api/mart/suppliers").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test Supplier\",\"phone\":\"123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long supplier=json.readTree(supplierResponse).get("id").asLong();
        String purchase="{\"requestKey\":\"purchase-1\",\"supplierId\":"+supplier+",\"name\":\"Tea\",\"category\":\"Grocery\",\"quantity\":10,\"unit\":\"packs\",\"costPrice\":60,\"sellingPrice\":100,\"lowStockThreshold\":2}";
        String created=mvc.perform(post("/api/mart/purchases").contentType(MediaType.APPLICATION_JSON).content(purchase)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value("600.00")).andReturn().getResponse().getContentAsString();
        long batch=json.readTree(created).get("inventory_id").asLong();
        mvc.perform(post("/api/mart/purchases").contentType(MediaType.APPLICATION_JSON).content(purchase)).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mart_purchases",Integer.class));
        var customerResponse=mvc.perform(post("/api/mart/customers").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test Customer\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long customer=json.readTree(customerResponse).get("id").asLong();
        String sale="{\"requestKey\":\"sale-1\",\"customerId\":"+customer+",\"items\":[{\"inventoryId\":"+batch+",\"quantity\":2}],\"discount\":10,\"taxRate\":10,\"paymentMethod\":\"CASH\",\"amountPaid\":250}";
        mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(sale)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value("209.00")).andExpect(jsonPath("$.change_due").value("41.00")).andExpect(jsonPath("$.items[0].product_name").value("Tea"));
        mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(sale)).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mart_sales",Integer.class));
        assertEquals("8",jdbc.queryForObject("SELECT quantity FROM inventory_records WHERE id=?",String.class,batch));
        String insufficient=sale.replace("sale-1","sale-2").replace("\"quantity\":2","\"quantity\":50");
        mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(insufficient)).andExpect(status().isBadRequest());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mart_sales",Integer.class));
        assertEquals("8",jdbc.queryForObject("SELECT quantity FROM inventory_records WHERE id=?",String.class,batch));
    }
    @Test void failedSaleRollsBackStockAndReceipt() throws Exception {
        long batch=create("Rice", "5");
        jdbc.execute("CREATE TRIGGER fail_sale_item BEFORE INSERT ON mart_sale_items BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        String sale="{\"requestKey\":\"rollback-1\",\"items\":[{\"inventoryId\":"+batch+",\"quantity\":2}],\"discount\":0,\"taxRate\":0,\"paymentMethod\":\"CASH\",\"amountPaid\":100}";
        assertThrows(Exception.class,()->mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(sale)).andReturn());
        assertEquals("5",jdbc.queryForObject("SELECT quantity FROM inventory_records WHERE id=?",String.class,batch));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mart_sales",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM inventory_activity WHERE action='STOCK_OUT'",Integer.class));
    }
    @Test void billingRejectsInvalidPaymentsAndExpiredStockWithoutChangingInventory() throws Exception {
        long batch=create("Rice", "5");
        String base="{\"requestKey\":\"invalid-payment\",\"items\":[{\"inventoryId\":"+batch+",\"quantity\":2}],\"discount\":0,\"taxRate\":0,\"paymentMethod\":\"CASH\",\"amountPaid\":1}";
        mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(base)).andExpect(status().isBadRequest());
        String card=base.replace("invalid-payment","bad-card").replace("\"CASH\"","\"CARD\"").replace("\"amountPaid\":1","\"amountPaid\":30");
        mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(card)).andExpect(status().isBadRequest());
        String tax=base.replace("invalid-payment","bad-tax").replace("\"taxRate\":0","\"taxRate\":101");
        mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(tax)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE inventory_records SET expiry_date='2000-01-01' WHERE id=?",batch);
        mvc.perform(post("/api/mart/sales").contentType(MediaType.APPLICATION_JSON).content(base.replace("invalid-payment","expired-item").replace("\"amountPaid\":1","\"amountPaid\":100"))).andExpect(status().isBadRequest());
        assertEquals("5",jdbc.queryForObject("SELECT quantity FROM inventory_records WHERE id=?",String.class,batch));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mart_sales",Integer.class));
    }
    @Test void activityFailureRollsBackDequeue() throws Exception {
        long a=create("Rice", "2"); create("Milk", "1");
        jdbc.execute("CREATE TRIGGER fail_activity BEFORE INSERT ON inventory_activity BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThrows(Exception.class, () -> mvc.perform(post("/api/queue/dequeue")).andReturn());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_records", Integer.class));
        assertEquals(a, new InventoryService(jdbc, transactions).queue().front().id());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_activity", Integer.class));
    }
}
