package pk.wasiumart.mart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pk.wasiumart.inventory.GroceryRecord;
import pk.wasiumart.inventory.InventoryInput;
import pk.wasiumart.inventory.InventoryService;

@Service
public class MartService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final InventoryService inventory;
    public MartService(JdbcTemplate db, TransactionTemplate tx, InventoryService inventory) { this.db=db; this.tx=tx; this.inventory=inventory; }
    private String table(String type) { if (!Set.of("customers","suppliers").contains(type)) throw new IllegalArgumentException("Invalid contact type"); return "mart_"+type; }
    private String required(String x,String label,int max) { if(x==null||x.isBlank()||x.strip().length()>max) throw new IllegalArgumentException(label+" is required and must be at most "+max+" characters"); return x.strip(); }
    private String optional(String x,int max) { if(x==null)return ""; if(x.strip().length()>max)throw new IllegalArgumentException("Text is too long"); return x.strip(); }
    private BigDecimal nonnegative(BigDecimal v,String field) { if(v==null||v.signum()<0||v.precision()>18||v.scale()>4) throw new IllegalArgumentException(field+" must be non-negative with at most 4 decimal places"); return v; }
    private BigDecimal money(BigDecimal v,String field) { nonnegative(v,field); if(v.stripTrailingZeros().scale()>2)throw new IllegalArgumentException(field+" supports at most 2 decimal places"); return v.setScale(2,RoundingMode.UNNECESSARY); }
    private BigDecimal rounded(BigDecimal v) { return v.setScale(2,RoundingMode.HALF_UP); }
    private String key(String v) { return required(v,"Request key",80); }
    private long lastId() { return Objects.requireNonNull(db.queryForObject("SELECT last_insert_rowid()",Long.class)); }
    private Map<String,Object> one(String sql,Object... args) { List<Map<String,Object>> found=db.queryForList(sql,args); if(found.isEmpty())throw new NoSuchElementException("Record not found"); return found.get(0); }
    public List<Map<String,Object>> contacts(String type) { return db.queryForList("SELECT * FROM "+table(type)+" ORDER BY name COLLATE NOCASE,id"); }
    public Map<String,Object> saveContact(String type,Long id,MartController.Contact c) {
        String t=table(type),name=required(c.name(),"Name",120),phone=optional(c.phone(),40),address=optional(c.address(),300);
        synchronized(inventory) { return tx.execute(status->{ long result;
            if(id==null){db.update("INSERT INTO "+t+"(name,phone,address,created_at) VALUES (?,?,?,?)",name,phone,address,Instant.now().toString());result=lastId();}
            else {if(db.update("UPDATE "+t+" SET name=?,phone=?,address=? WHERE id=?",name,phone,address,id)==0)throw new NoSuchElementException("Contact not found");result=id;}
            return one("SELECT * FROM "+t+" WHERE id=?",result);
        }); }
    }
    public Map<String,Object> summary(){
        BigDecimal gross=BigDecimal.ZERO, todaySales=BigDecimal.ZERO, purchasesTotal=BigDecimal.ZERO;
        LocalDate today=LocalDate.now(); long todayCount=0;
        for(var row:db.queryForList("SELECT total,occurred_at FROM mart_sales")) {
            BigDecimal value=new BigDecimal((String)row.get("total")); gross=gross.add(value);
            if(Instant.parse((String)row.get("occurred_at")).atZone(java.time.ZoneId.systemDefault()).toLocalDate().equals(today)){todaySales=todaySales.add(value);todayCount++;}
        }
        for(var row:db.queryForList("SELECT total FROM mart_purchases"))purchasesTotal=purchasesTotal.add(new BigDecimal((String)row.get("total")));
        return Map.of("todaySales",todaySales,"todayCount",todayCount,"grossSales",gross,"purchasesTotal",purchasesTotal);
    }
    public List<Map<String,Object>> purchases(){return db.queryForList("SELECT * FROM mart_purchases ORDER BY id DESC LIMIT 500");}
    public List<Map<String,Object>> sales(){return db.queryForList("SELECT * FROM mart_sales ORDER BY id DESC LIMIT 500");}
    public Map<String,Object> sale(long id){Map<String,Object> result=new LinkedHashMap<>(one("SELECT * FROM mart_sales WHERE id=?",id)); result.put("items",db.queryForList("SELECT inventory_id,product_name,unit,quantity,unit_price,line_total FROM mart_sale_items WHERE sale_id=? ORDER BY id",id));return result;}
    public Map<String,Object> purchase(MartController.Purchase p) {
        String request=key(p.requestKey());
        synchronized(inventory){return tx.execute(status->{
            List<Map<String,Object>> previous=db.queryForList("SELECT * FROM mart_purchases WHERE request_key=?",request);if(!previous.isEmpty())return previous.get(0);
            if(p.supplierId()==null)throw new IllegalArgumentException("Choose a supplier");
            String supplier=(String)one("SELECT name FROM mart_suppliers WHERE id=?",p.supplierId()).get("name");
            BigDecimal quantity=nonnegative(p.quantity(),"Quantity"),cost=money(p.costPrice(),"Cost price"),selling=money(p.sellingPrice(),"Selling price");
            if(quantity.signum()<=0)throw new IllegalArgumentException("Quantity must be greater than zero");
            BigDecimal threshold=nonnegative(p.lowStockThreshold(),"Low stock threshold");
            String ref=optional(p.invoiceRef(),100);
            GroceryRecord added=inventory.create(new InventoryInput(p.name(),p.category(),quantity,p.unit(),selling,threshold,p.expiryDate()));
            db.update("INSERT INTO mart_purchases(request_key,supplier_id,supplier_name,invoice_ref,inventory_id,product_name,quantity,unit,cost_price,total,occurred_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",request,p.supplierId(),supplier,ref,added.id(),added.name(),quantity.toPlainString(),added.unit(),cost.toPlainString(),rounded(quantity.multiply(cost)).toPlainString(),Instant.now().toString());
            return one("SELECT * FROM mart_purchases WHERE id=?",lastId());
        });}
    }
    public Map<String,Object> checkout(MartController.Sale sale) {
        String request=key(sale.requestKey());
        synchronized(inventory){return tx.execute(status->{
            List<Map<String,Object>> existing=db.queryForList("SELECT id FROM mart_sales WHERE request_key=?",request);if(!existing.isEmpty())return sale(((Number)existing.get(0).get("id")).longValue());
            if(sale.items()==null||sale.items().isEmpty()||sale.items().size()>200)throw new IllegalArgumentException("Add between 1 and 200 items");
            BigDecimal discount=money(sale.discount()==null?BigDecimal.ZERO:sale.discount(),"Discount"),tax=nonnegative(sale.taxRate()==null?BigDecimal.ZERO:sale.taxRate(),"Tax rate");
            if(tax.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Tax rate cannot exceed 100%");
            String method=required(sale.paymentMethod(),"Payment method",20).toUpperCase(Locale.ROOT);if(!Set.of("CASH","CARD").contains(method))throw new IllegalArgumentException("Payment method must be cash or card");
            String customer="Walk-in customer";if(sale.customerId()!=null)customer=(String)one("SELECT name FROM mart_customers WHERE id=?",sale.customerId()).get("name");
            Set<Long> seen=new HashSet<>();List<GroceryRecord> batches=new ArrayList<>();List<BigDecimal> quantities=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO;
            for(var line:sale.items()){
                if(line.inventoryId()==null||!seen.add(line.inventoryId()))throw new IllegalArgumentException("Each batch can appear only once");
                GroceryRecord item=inventory.find(line.inventoryId()).orElseThrow(()->new NoSuchElementException("Product batch not found"));
                BigDecimal qty=nonnegative(line.quantity(),"Quantity");if(qty.signum()<=0||qty.compareTo(item.quantity())>0)throw new IllegalArgumentException("Insufficient stock for "+item.name());
                if(item.expiryDate()!=null&&item.expiryDate().isBefore(LocalDate.now()))throw new IllegalArgumentException(item.name()+" is expired and cannot be sold");
                batches.add(item);quantities.add(qty);subtotal=subtotal.add(rounded(qty.multiply(item.unitPrice())));
            }
            if(discount.compareTo(subtotal)>0)throw new IllegalArgumentException("Discount cannot exceed subtotal");
            BigDecimal taxAmount=rounded(subtotal.subtract(discount).multiply(tax).divide(new BigDecimal("100"))),total=subtotal.subtract(discount).add(taxAmount);
            BigDecimal paid=money(sale.amountPaid(),"Amount paid");if(paid.compareTo(total)<0)throw new IllegalArgumentException("Amount paid is less than total");
            if(method.equals("CARD")&&paid.compareTo(total)!=0)throw new IllegalArgumentException("Card payment must equal total");
            db.update("INSERT INTO mart_sales(request_key,customer_id,customer_name,subtotal,discount,tax_rate,tax_amount,total,payment_method,amount_paid,change_due,occurred_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",request,sale.customerId(),customer,subtotal.toPlainString(),discount.toPlainString(),tax.toPlainString(),taxAmount.toPlainString(),total.toPlainString(),method,paid.toPlainString(),paid.subtract(total).toPlainString(),Instant.now().toString());
            long id=lastId();
            for(int i=0;i<batches.size();i++) {GroceryRecord b=batches.get(i);BigDecimal q=quantities.get(i);inventory.adjustStock(b.id(),q.negate(),"Sale #"+id);db.update("INSERT INTO mart_sale_items(sale_id,inventory_id,product_name,unit,quantity,unit_price,line_total) VALUES(?,?,?,?,?,?,?)",id,b.id(),b.name(),b.unit(),q.toPlainString(),b.unitPrice().toPlainString(),rounded(q.multiply(b.unitPrice())).toPlainString());}
            return sale(id);
        });}
    }
}
