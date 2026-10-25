import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * BenkeyShop - Aplikasi Kasir (Java Swing, OOP, MySQL/XAMPP).
 *
 * Seluruh class pendukung (Product, CartItem, CartService, Sale, SaleLine,
 * ProductDAO, SalesDAO, ReceiptPrinter, DB) berada di file ini sebagai
 * nested class, sehingga konsep tiga file
 * (Main.java -> KasirFrame.java -> AplikasiKasir.java) tetap terjaga.
 * Antarmuka dibangun lewat kode Swing, bukan GUI Builder.
 */
public class AplikasiKasir extends JFrame {

    // =====================================================================
    //  MODEL / ENTITY
    // =====================================================================

    /** Model/entity produk dengan enkapsulasi atribut. */
    public static class Product {
        private long id;
        private String sku, name, category;
        private BigDecimal price;
        private int stock;

        public Product(long id, String sku, String name, String category, BigDecimal price, int stock) {
            this.id = id; this.sku = sku; this.name = name;
            this.category = category; this.price = price; this.stock = stock;
        }
        public long getId() { return id; }                 public void setId(long id) { this.id = id; }
        public String getSku() { return sku; }             public void setSku(String v) { sku = v; }
        public String getName() { return name; }           public void setName(String v) { name = v; }
        public String getCategory() { return category; }   public void setCategory(String v) { category = v; }
        public BigDecimal getPrice() { return price; }     public void setPrice(BigDecimal v) { price = v; }
        public int getStock() { return stock; }            public void setStock(int v) { stock = v; }
    }

    /** Objek item keranjang, dengan perhitungan subtotal per item. */
    public static class CartItem {
        private final Product product;
        private int quantity;

        public CartItem(Product product, int quantity) { this.product = product; this.quantity = quantity; }
        public Product getProduct() { return product; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) {
            if (quantity < 1) throw new IllegalArgumentException("Jumlah minimal 1");
            this.quantity = quantity;
        }
        public BigDecimal getSubtotal() { return product.getPrice().multiply(BigDecimal.valueOf(quantity)); }
    }

    /** Class layanan untuk aturan bisnis keranjang, terpisah dari tampilan. */
    public static class CartService {
        private final Map<Long, CartItem> items = new LinkedHashMap<>();

        public void add(Product product) {
            CartItem item = items.get(product.getId());
            int next = item == null ? 1 : item.getQuantity() + 1;
            if (next > product.getStock()) throw new IllegalArgumentException("Stok tidak mencukupi");
            if (item == null) items.put(product.getId(), new CartItem(product, 1));
            else item.setQuantity(next);
        }
        public void changeQuantity(long productId, int delta) {
            CartItem item = items.get(productId);
            if (item == null) return;
            int next = item.getQuantity() + delta;
            if (next <= 0) { items.remove(productId); return; }
            if (next > item.getProduct().getStock()) throw new IllegalArgumentException("Melebihi stok");
            item.setQuantity(next);
        }
        public void remove(long productId) { items.remove(productId); }
        public void clear() { items.clear(); }
        public boolean isEmpty() { return items.isEmpty(); }
        public Collection<CartItem> getItems() { return List.copyOf(items.values()); }
        public Map<Long, CartItem> snapshot() { return new LinkedHashMap<>(items); }
        public BigDecimal getSubtotal() {
            return items.values().stream().map(CartItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** Entity transaksi penjualan. */
    public static class Sale {
        private final String receiptNo, buyerName;
        private final LocalDateTime soldAt;
        private final BigDecimal subtotal, discount, total, paid, changeAmount;
        private final List<SaleLine> lines;

        public Sale(String receiptNo, String buyerName, LocalDateTime soldAt, BigDecimal subtotal,
                    BigDecimal discount, BigDecimal total, BigDecimal paid, BigDecimal changeAmount,
                    List<SaleLine> lines) {
            this.receiptNo = receiptNo; this.buyerName = buyerName; this.soldAt = soldAt;
            this.subtotal = subtotal; this.discount = discount; this.total = total;
            this.paid = paid; this.changeAmount = changeAmount; this.lines = List.copyOf(lines);
        }
        public String getReceiptNo() { return receiptNo; }
        public String getBuyerName() { return buyerName; }
        public LocalDateTime getSoldAt() { return soldAt; }
        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getDiscount() { return discount; }
        public BigDecimal getTotal() { return total; }
        public BigDecimal getPaid() { return paid; }
        public BigDecimal getChangeAmount() { return changeAmount; }
        public List<SaleLine> getLines() { return lines; }
    }

    /** Snapshot rincian barang saat transaksi, tidak bergantung pada data produk terkini. */
    public static class SaleLine {
        private final Long productId;
        private final String skuSnapshot, nameSnapshot;
        private final BigDecimal unitPrice;
        private final int quantity;

        public SaleLine(Long productId, String sku, String name, BigDecimal unitPrice, int quantity) {
            this.productId = productId; this.skuSnapshot = sku; this.nameSnapshot = name;
            this.unitPrice = unitPrice; this.quantity = quantity;
        }
        public Long getProductId() { return productId; }
        public String getSkuSnapshot() { return skuSnapshot; }
        public String getNameSnapshot() { return nameSnapshot; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public int getQuantity() { return quantity; }
        public BigDecimal getLineTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }

    // =====================================================================
    //  DATABASE & DAO
    // =====================================================================

    /**
     * Penyedia koneksi database. Membaca /db.properties bila ada di classpath;
     * jika tidak ada, memakai nilai bawaan XAMPP (root, tanpa password, port 3306).
     */
    public static final class DB {
        private static final Properties P = new Properties();
        static {
            P.setProperty("db.url", "jdbc:mysql://localhost:3306/benkeyshop?useSSL=false"
                    + "&serverTimezone=Asia/Jakarta&allowPublicKeyRetrieval=true");
            P.setProperty("db.user", "root");
            P.setProperty("db.password", "");
            try (InputStream in = DB.class.getResourceAsStream("/db.properties")) {
                if (in != null) P.load(in);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        private DB() {}

        public static Connection connect() throws SQLException {
            return DriverManager.getConnection(
                    System.getProperty("db.url", P.getProperty("db.url")),
                    System.getProperty("db.user", P.getProperty("db.user", "root")),
                    System.getProperty("db.password", P.getProperty("db.password", "")));
        }
    }

    /** Data Access Object: query produk tidak dicampur dengan komponen UI. */
    public static class ProductDAO {
        public List<Product> search(String keyword) throws SQLException {
            List<Product> result = new ArrayList<>();
            String sql = "SELECT id,sku,name,category,price,stock FROM products "
                    + "WHERE sku LIKE ? OR name LIKE ? OR category LIKE ? ORDER BY name";
            try (Connection c = DB.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
                String q = "%" + (keyword == null ? "" : keyword.trim()) + "%";
                for (int i = 1; i <= 3; i++) ps.setString(i, q);
                try (ResultSet r = ps.executeQuery()) {
                    while (r.next()) result.add(map(r));
                }
            }
            return result;
        }

        public Product findById(long id) throws SQLException {
            try (Connection c = DB.connect();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT id,sku,name,category,price,stock FROM products WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet r = ps.executeQuery()) { return r.next() ? map(r) : null; }
            }
        }

        public void save(Product p) throws SQLException {
            boolean insert = p.getId() <= 0;
            String sql = insert
                    ? "INSERT INTO products(sku,name,category,price,stock) VALUES(?,?,?,?,?)"
                    : "UPDATE products SET sku=?,name=?,category=?,price=?,stock=? WHERE id=?";
            try (Connection c = DB.connect();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, p.getSku());
                ps.setString(2, p.getName());
                ps.setString(3, p.getCategory());
                ps.setBigDecimal(4, p.getPrice());
                ps.setInt(5, p.getStock());
                if (!insert) ps.setLong(6, p.getId());
                ps.executeUpdate();
                if (insert) {
                    try (ResultSet k = ps.getGeneratedKeys()) { if (k.next()) p.setId(k.getLong(1)); }
                }
            }
        }

        public void delete(long id) throws SQLException {
            try (Connection c = DB.connect();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        }

        private Product map(ResultSet r) throws SQLException {
            return new Product(r.getLong("id"), r.getString("sku"), r.getString("name"),
                    r.getString("category"), r.getBigDecimal("price"), r.getInt("stock"));
        }
    }

    /** DAO transaksi dan laporan. */
    public static class SalesDAO {
        public void save(Sale sale, Map<Long, CartItem> cart) throws SQLException {
            try (Connection c = DB.connect()) {
                c.setAutoCommit(false);
                try {
                    // 1) Kunci baris stok dan pastikan stok cukup
                    for (CartItem i : cart.values()) {
                        try (PreparedStatement p = c.prepareStatement("SELECT stock FROM products WHERE id=? FOR UPDATE")) {
                            p.setLong(1, i.getProduct().getId());
                            try (ResultSet r = p.executeQuery()) {
                                if (!r.next() || r.getInt(1) < i.getQuantity())
                                    throw new SQLException("Stok tidak cukup: " + i.getProduct().getName());
                            }
                        }
                    }
                    // 2) Simpan header transaksi
                    long saleId;
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO sales(receipt_no,buyer_name,subtotal,discount,total,paid,change_amount) "
                                    + "VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        p.setString(1, sale.getReceiptNo());
                        p.setString(2, sale.getBuyerName());
                        p.setBigDecimal(3, sale.getSubtotal());
                        p.setBigDecimal(4, sale.getDiscount());
                        p.setBigDecimal(5, sale.getTotal());
                        p.setBigDecimal(6, sale.getPaid());
                        p.setBigDecimal(7, sale.getChangeAmount());
                        p.executeUpdate();
                        try (ResultSet k = p.getGeneratedKeys()) { k.next(); saleId = k.getLong(1); }
                    }
                    // 3) Simpan detail (snapshot) dan kurangi stok
                    for (CartItem i : cart.values()) {
                        Product p = i.getProduct();
                        try (PreparedStatement q = c.prepareStatement(
                                "INSERT INTO sale_items(sale_id,product_id,sku_snapshot,product_name_snapshot,"
                                        + "unit_price,quantity,line_total) VALUES(?,?,?,?,?,?,?)")) {
                            q.setLong(1, saleId);
                            q.setLong(2, p.getId());
                            q.setString(3, p.getSku());
                            q.setString(4, p.getName());
                            q.setBigDecimal(5, p.getPrice());
                            q.setInt(6, i.getQuantity());
                            q.setBigDecimal(7, i.getSubtotal());
                            q.executeUpdate();
                        }
                        try (PreparedStatement q = c.prepareStatement(
                                "UPDATE products SET stock=stock-? WHERE id=? AND stock>=?")) {
                            q.setInt(1, i.getQuantity());
                            q.setLong(2, p.getId());
                            q.setInt(3, i.getQuantity());
                            if (q.executeUpdate() != 1) throw new SQLException("Stok berubah.");
                        }
                    }
                    c.commit();
                } catch (Exception e) {
                    c.rollback();
                    if (e instanceof SQLException se) throw se;
                    throw new SQLException(e);
                } finally {
                    c.setAutoCommit(true);
                }
            }
        }

        public List<Object[]> report(LocalDate a, LocalDate b) throws SQLException {
            List<Object[]> out = new ArrayList<>();
            try (Connection c = DB.connect();
                 PreparedStatement p = c.prepareStatement(
                         "SELECT receipt_no,sold_at,buyer_name,subtotal,discount,total FROM sales "
                                 + "WHERE sold_at>=? AND sold_at<? ORDER BY sold_at DESC")) {
                p.setString(1, a + " 00:00:00");
                p.setString(2, b.plusDays(1) + " 00:00:00");
                try (ResultSet r = p.executeQuery()) {
                    while (r.next())
                        out.add(new Object[]{r.getString(1), r.getTimestamp(2), r.getString(3),
                                r.getBigDecimal(4), r.getBigDecimal(5), r.getBigDecimal(6)});
                }
            }
            return out;
        }

        /** @return {jumlah transaksi, omzet, rata-rata transaksi} */
        public BigDecimal[] summary(LocalDate a, LocalDate b) throws SQLException {
            try (Connection c = DB.connect();
                 PreparedStatement p = c.prepareStatement(
                         "SELECT COUNT(*),COALESCE(SUM(total),0),COALESCE(AVG(total),0) FROM sales "
                                 + "WHERE sold_at>=? AND sold_at<?")) {
                p.setString(1, a + " 00:00:00");
                p.setString(2, b.plusDays(1) + " 00:00:00");
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    return new BigDecimal[]{BigDecimal.valueOf(r.getLong(1)), r.getBigDecimal(2), r.getBigDecimal(3)};
                }
            }
        }

        /** @return {jumlah transaksi hari ini, omzet hari ini} */
        public BigDecimal[] today() throws SQLException {
            try (Connection c = DB.connect(); Statement s = c.createStatement();
                 ResultSet r = s.executeQuery(
                         "SELECT COUNT(*),COALESCE(SUM(total),0) FROM sales WHERE DATE(sold_at)=CURDATE()")) {
                r.next();
                return new BigDecimal[]{BigDecimal.valueOf(r.getLong(1)), r.getBigDecimal(2)};
            }
        }
    }

    /** Tanggung jawab format dan pencetakan struk dipisahkan dari form kasir. */
    public static class ReceiptPrinter {
        private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));

        public String format(Sale sale) {
            StringBuilder b = new StringBuilder(
                    "             BENKEYSHOP\n          STRUK PEMBELIAN\n------------------------------------------\n");
            b.append("No      : ").append(sale.getReceiptNo()).append('\n')
             .append("Pembeli : ").append(sale.getBuyerName()).append('\n');
            b.append("Tanggal : ").append(sale.getSoldAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))).append('\n');
            for (SaleLine l : sale.getLines())
                b.append(l.getNameSnapshot()).append('\n')
                 .append("  ").append(l.getQuantity()).append(" x ").append(fmt(l.getUnitPrice()))
                 .append(" = ").append(fmt(l.getLineTotal())).append('\n');
            b.append("------------------------------------------\nSubtotal : ").append(fmt(sale.getSubtotal())).append('\n')
             .append("Diskon   : ").append(fmt(sale.getDiscount())).append('\n');
            b.append("TOTAL    : ").append(fmt(sale.getTotal())).append('\n')
             .append("Bayar    : ").append(fmt(sale.getPaid())).append('\n')
             .append("Kembali  : ").append(fmt(sale.getChangeAmount())).append('\n');
            b.append("------------------------------------------\n   Terima kasih telah berbelanja!\n");
            return b.toString();
        }

        public void print(String text) throws PrinterException {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("Struk BenkeyShop");
            job.setPrintable((graphics, page, pageIndex) -> {
                if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
                Graphics2D g = (Graphics2D) graphics;
                g.translate(page.getImageableX(), page.getImageableY());
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
                int y = 14;
                for (String line : text.split("\n")) { g.drawString(line, 4, y); y += 12; }
                return Printable.PAGE_EXISTS;
            });
            if (job.printDialog()) job.print();
        }

        private String fmt(BigDecimal n) { return money.format(n).replace(",00", ""); }
    }

    // =====================================================================
    //  ANTARMUKA KASIR
    // =====================================================================

    private final Color bg = new Color(17, 24, 27), panel = new Color(27, 37, 39),
            green = new Color(153, 220, 92), muted = new Color(166, 181, 177);

    private final ProductDAO products = new ProductDAO();
    private final SalesDAO sales = new SalesDAO();
    private final CartService cart = new CartService();
    private final ReceiptPrinter printer = new ReceiptPrinter();
    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));

    private final DefaultTableModel
            productModel = model("ID", "SKU", "Produk", "Kategori", "Harga", "Stok"),
            cartModel = model("SKU", "Produk", "Harga", "Qty", "Subtotal"),
            salesModel = model("No. Struk", "Tanggal", "Pembeli", "Subtotal", "Diskon", "Total");

    private JTable productTable, cartTable, salesTable;
    private JTextField cashierSearch, productSearch, buyer, paid, sku, name, category, price, stock, from, to;
    private JLabel status;
    private final Map<String, JLabel> metrics = new HashMap<>();
    private JLabel cartSub, cartTotal;
    private long selectedId = -1;

    public AplikasiKasir() {
        super("BenkeyShop • OOP POS");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(1050, 680));
        setLocationRelativeTo(null);
        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 16);
        setContentPane(ui());
        loadProducts("");
        refreshCart();
        refreshReport();
        dashboard();
    }

    private JPanel ui() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(22, 24, 18, 24));

        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("BenkeyShop");
        title.setForeground(green);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        JLabel sub = new JLabel("SMART RETAIL / POINT OF SALE");
        sub.setForeground(muted);
        brand.add(title);
        brand.add(sub);
        h.add(brand, BorderLayout.WEST);
        JLabel date = new JLabel(LocalDate.now().toString());
        date.setForeground(muted);
        h.add(date, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ringkasan", dashboardPanel());
        tabs.addTab("Kasir", cashierPanel());
        tabs.addTab("Produk", productPanel());
        tabs.addTab("Laporan & Analisis", reportPanel());

        root.add(h, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        status = new JLabel("Siap");
        status.setForeground(muted);
        root.add(status, BorderLayout.SOUTH);
        return root;
    }

    private JPanel dashboardPanel() {
        JPanel p = panel(new BorderLayout(14, 14));
        p.setBorder(new EmptyBorder(18, 2, 2, 2));
        JPanel cards = new JPanel(new GridLayout(1, 3, 12, 0));
        cards.setOpaque(false);
        cards.add(metric("PENJUALAN HARI INI", "Rp 0"));
        cards.add(metric("TRANSAKSI HARI INI", "0"));
        cards.add(metric("STOK MENIPIS", "0"));
        JPanel info = panel(new BorderLayout());
        info.setBorder(new EmptyBorder(22, 22, 22, 22));
        JLabel t = heading("Selamat datang di BenkeyShop");
        t.setForeground(green);
        info.add(t, BorderLayout.NORTH);
        info.add(new JLabel("Kasir desktop berbasis OOP • Keranjang • Struk • Laporan dan analisis"), BorderLayout.CENTER);
        p.add(cards, BorderLayout.NORTH);
        p.add(info, BorderLayout.CENTER);
        return p;
    }

    private JPanel metric(String text, String value) {
        JPanel p = panel(new BorderLayout(6, 6));
        p.setBorder(new EmptyBorder(18, 18, 18, 18));
        JLabel a = new JLabel(text);
        a.setForeground(muted);
        JLabel b = new JLabel(value);
        b.setForeground(green);
        b.setFont(new Font("SansSerif", Font.BOLD, 24));
        metrics.put(text, b);
        p.add(a, BorderLayout.NORTH);
        p.add(b, BorderLayout.CENTER);
        return p;
    }

    private JPanel cashierPanel() {
        JPanel p = panel(new BorderLayout(12, 12));
        p.setBorder(new EmptyBorder(12, 2, 2, 2));

        // --- Kiri: katalog / pencarian produk ---
        JPanel left = panel(new BorderLayout(8, 8));
        left.setBorder(new EmptyBorder(14, 14, 14, 14));
        cashierSearch = new JTextField();
        cashierSearch.putClientProperty("JTextField.placeholderText", "Cari produk / SKU");
        cashierSearch.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { loadProducts(cashierSearch.getText()); }
        });
        productTable = new JTable(productModel);
        style(productTable);
        JButton add = accent("Tambah ke Keranjang");
        add.addActionListener(e -> addCart());
        productTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) addCart(); }
        });
        left.add(cashierSearch, BorderLayout.NORTH);
        left.add(new JScrollPane(productTable), BorderLayout.CENTER);
        left.add(add, BorderLayout.SOUTH);

        // --- Kanan: keranjang & pembayaran ---
        JPanel right = panel(new BorderLayout(8, 8));
        right.setBorder(new EmptyBorder(14, 14, 14, 14));
        buyer = new JTextField();
        buyer.putClientProperty("JTextField.placeholderText", "Nama pembeli (wajib)");
        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.setOpaque(false);
        top.add(heading("Keranjang"), BorderLayout.NORTH);
        top.add(buyer, BorderLayout.CENTER);

        cartTable = new JTable(cartModel);
        style(cartTable);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 6, 0));
        buttons.setOpaque(false);
        JButton plus = soft("+ Qty"), minus = soft("- Qty"), del = soft("Hapus");
        plus.addActionListener(e -> qty(1));
        minus.addActionListener(e -> qty(-1));
        del.addActionListener(e -> removeCart());
        buttons.add(plus);
        buttons.add(minus);
        buttons.add(del);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        cartSub = new JLabel("Subtotal: Rp 0");
        cartSub.setForeground(muted);
        cartTotal = new JLabel("TOTAL Rp 0");
        cartTotal.setForeground(green);
        cartTotal.setFont(new Font("SansSerif", Font.BOLD, 21));
        paid = new JTextField();
        paid.putClientProperty("JTextField.placeholderText", "Uang dibayar, mis. 50000");
        JButton pay = accent("Bayar & Cetak Struk");
        pay.addActionListener(e -> checkout());
        JButton clear = soft("Kosongkan Keranjang");
        clear.addActionListener(e -> { cart.clear(); refreshCart(); });
        bottom.add(cartSub);
        bottom.add(cartTotal);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(paid);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(pay);
        bottom.add(clear);

        JPanel south = new JPanel(new BorderLayout(6, 6));
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.CENTER);
        right.add(top, BorderLayout.NORTH);
        right.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        right.add(south, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(.5);
        split.setBorder(null);
        p.add(split);
        return p;
    }

    private JPanel productPanel() {
        JPanel p = panel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(12, 2, 2, 2));
        JPanel form = panel(new GridLayout(1, 5, 7, 7));
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        sku = new JTextField();
        name = new JTextField();
        category = new JTextField();
        price = new JTextField();
        stock = new JTextField();
        form.add(field("SKU", sku));
        form.add(field("Nama", name));
        form.add(field("Kategori", category));
        form.add(field("Harga", price));
        form.add(field("Stok", stock));

        JButton save = accent("Simpan / Perbarui"), fresh = soft("Form Baru"), del = soft("Hapus Produk");
        save.addActionListener(e -> saveProduct());
        fresh.addActionListener(e -> clearForm());
        del.addActionListener(e -> deleteProduct());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        actions.add(save);
        actions.add(fresh);
        actions.add(del);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(form);
        north.add(actions, BorderLayout.SOUTH);

        JPanel table = panel(new BorderLayout(6, 6));
        productSearch = new JTextField();
        productSearch.putClientProperty("JTextField.placeholderText", "Cari SKU, nama, kategori");
        productSearch.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { loadProducts(productSearch.getText()); }
        });
        table.add(productSearch, BorderLayout.NORTH);
        JTable t = new JTable(productModel);
        style(t);
        t.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) fillForm(t); });
        table.add(new JScrollPane(t));

        p.add(north, BorderLayout.NORTH);
        p.add(table);
        return p;
    }

    private JPanel reportPanel() {
        JPanel p = panel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(12, 2, 2, 2));
        JPanel f = panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        from = new JTextField(LocalDate.now().withDayOfMonth(1).toString(), 10);
        to = new JTextField(LocalDate.now().toString(), 10);
        JButton run = accent("Tampilkan Laporan"), csv = soft("Ekspor CSV");
        run.addActionListener(e -> refreshReport());
        csv.addActionListener(e -> exportCsv());
        f.add(new JLabel("Dari YYYY-MM-DD"));
        f.add(from);
        f.add(new JLabel("Sampai"));
        f.add(to);
        f.add(run);
        f.add(csv);
        salesTable = new JTable(salesModel);
        style(salesTable);
        p.add(f, BorderLayout.NORTH);
        p.add(new JScrollPane(salesTable));
        return p;
    }

    // ---------------------------------------------------------------- helper UI

    private JPanel panel(LayoutManager l) { JPanel p = new JPanel(l); p.setBackground(panel); return p; }

    private JLabel heading(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("SansSerif", Font.BOLD, 17));
        return l;
    }

    private JButton accent(String s) {
        JButton b = new JButton(s);
        b.setBackground(green);
        b.setForeground(new Color(20, 29, 24));
        return b;
    }

    private JButton soft(String s) {
        JButton b = new JButton(s);
        b.setBackground(new Color(43, 58, 57));
        b.setForeground(Color.WHITE);
        return b;
    }

    private JPanel field(String s, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(3, 3));
        p.setOpaque(false);
        JLabel l = new JLabel(s);
        l.setForeground(muted);
        p.add(l, BorderLayout.NORTH);
        p.add(c);
        return p;
    }

    private void style(JTable t) {
        t.setRowHeight(29);
        t.setShowGrid(false);
        t.setSelectionBackground(new Color(57, 78, 59));
        t.getTableHeader().setBackground(new Color(37, 50, 49));
        t.getTableHeader().setForeground(green);
    }

    private static DefaultTableModel model(String... s) {
        return new DefaultTableModel(s, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private String fmt(BigDecimal n) { return money.format(n).replace(",00", ""); }

    private void msg(String s) {
        JOptionPane.showMessageDialog(this, s, "BenkeyShop", JOptionPane.INFORMATION_MESSAGE);
    }

    private void err(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Kesalahan", JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------- produk & keranjang

    private void loadProducts(String q) {
        try {
            productModel.setRowCount(0);
            for (Product p : products.search(q))
                productModel.addRow(new Object[]{p.getId(), p.getSku(), p.getName(), p.getCategory(),
                        fmt(p.getPrice()), p.getStock()});
        } catch (Exception e) {
            if (status != null) status.setText("Database belum terhubung: periksa XAMPP/db.properties");
        }
    }

    private Product selected(JTable t) throws Exception {
        int r = t.getSelectedRow();
        if (r < 0) return null;
        return products.findById(Long.parseLong(productModel.getValueAt(t.convertRowIndexToModel(r), 0).toString()));
    }

    private void addCart() {
        try {
            Product p = selected(productTable);
            if (p == null) { msg("Pilih produk dahulu."); return; }
            cart.add(p);
            refreshCart();
        } catch (Exception e) { err(e); }
    }

    private void refreshCart() {
        cartModel.setRowCount(0);
        for (CartItem i : cart.getItems())
            cartModel.addRow(new Object[]{i.getProduct().getSku(), i.getProduct().getName(),
                    fmt(i.getProduct().getPrice()), i.getQuantity(), fmt(i.getSubtotal())});
        cartSub.setText("Subtotal: " + fmt(cart.getSubtotal()));
        cartTotal.setText("TOTAL " + fmt(cart.getSubtotal()));
    }

    private long cartSelectedId() {
        int r = cartTable.getSelectedRow();
        if (r < 0) return -1;
        return new ArrayList<>(cart.snapshot().keySet()).get(r);
    }

    private void qty(int d) {
        long id = cartSelectedId();
        if (id < 0) return;
        try { cart.changeQuantity(id, d); refreshCart(); } catch (Exception e) { err(e); }
    }

    private void removeCart() {
        long id = cartSelectedId();
        if (id > 0) { cart.remove(id); refreshCart(); }
    }

    // ---------------------------------------------------------------- checkout & struk

    private void checkout() {
        if (cart.isEmpty()) { msg("Keranjang kosong."); return; }
        if (buyer.getText().trim().isEmpty()) { msg("Nama pembeli wajib diisi."); return; }
        BigDecimal sub = cart.getSubtotal(), paidAmount;
        try {
            paidAmount = new BigDecimal(paid.getText().trim().replace(".", "").replace(",", ""));
            if (paidAmount.compareTo(sub) < 0) { msg("Pembayaran kurang."); return; }
        } catch (Exception e) {
            msg("Masukkan pembayaran sebagai angka, mis. 50000.");
            return;
        }
        String no = "BKS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmssSSS"));
        List<SaleLine> lines = new ArrayList<>();
        for (CartItem i : cart.getItems())
            lines.add(new SaleLine(i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                    i.getProduct().getPrice(), i.getQuantity()));
        Sale sale = new Sale(no, buyer.getText().trim(), LocalDateTime.now(), sub, BigDecimal.ZERO, sub,
                paidAmount, paidAmount.subtract(sub), lines);
        try {
            sales.save(sale, cart.snapshot());
            String receipt = printer.format(sale);
            JTextArea area = new JTextArea(receipt, 23, 40);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setEditable(false);
            if (JOptionPane.showConfirmDialog(this, new JScrollPane(area), "Transaksi berhasil",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                printer.print(receipt);
            cart.clear();
            buyer.setText("");
            paid.setText("");
            refreshCart();
            loadProducts("");
            refreshReport();
            dashboard();
            status.setText("Transaksi tersimpan: " + no);
        } catch (Exception e) { err(e); }
    }

    // ---------------------------------------------------------------- CRUD produk

    private void saveProduct() {
        try {
            String a = sku.getText().trim(), b = name.getText().trim(), c = category.getText().trim();
            BigDecimal d = new BigDecimal(price.getText().trim());
            int st = Integer.parseInt(stock.getText().trim());
            if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.signum() < 0 || st < 0)
                throw new IllegalArgumentException("Lengkapi data produk dengan benar.");
            products.save(new Product(selectedId, a, b, c, d, st));
            clearForm();
            loadProducts("");
            dashboard();
        } catch (Exception e) { err(e); }
    }

    private void fillForm(JTable t) {
        try {
            Product p = selected(t);
            if (p == null) return;
            selectedId = p.getId();
            sku.setText(p.getSku());
            name.setText(p.getName());
            category.setText(p.getCategory());
            price.setText(p.getPrice().toPlainString());
            stock.setText(String.valueOf(p.getStock()));
        } catch (Exception e) { err(e); }
    }

    private void clearForm() {
        selectedId = -1;
        if (sku != null) {
            sku.setText("");
            name.setText("");
            category.setText("");
            price.setText("");
            stock.setText("");
        }
    }

    private void deleteProduct() {
        if (selectedId <= 0) { msg("Pilih produk dari tabel produk terlebih dahulu."); return; }
        if (JOptionPane.showConfirmDialog(this, "Hapus produk? Riwayat lama tetap utuh.", "Konfirmasi",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            products.delete(selectedId);
            clearForm();
            loadProducts("");
            dashboard();
        } catch (Exception e) { err(e); }
    }

    // ---------------------------------------------------------------- laporan & dashboard

    private void refreshReport() {
        if (salesTable == null) return;
        try {
            LocalDate a = LocalDate.parse(from.getText().trim()), b = LocalDate.parse(to.getText().trim());
            if (b.isBefore(a)) throw new IllegalArgumentException();
            salesModel.setRowCount(0);
            for (Object[] r : sales.report(a, b))
                salesModel.addRow(new Object[]{r[0], r[1], r[2], fmt((BigDecimal) r[3]),
                        fmt((BigDecimal) r[4]), fmt((BigDecimal) r[5])});
            BigDecimal[] s = sales.summary(a, b);
            status.setText("Analisis " + a + " s.d. " + b + " • " + s[0].toBigInteger()
                    + " transaksi • omzet " + fmt(s[1]) + " • rata-rata " + fmt(s[2]));
        } catch (Exception e) {
            if (status != null) status.setText("Tanggal harus YYYY-MM-DD dan koneksi database aktif.");
        }
    }

    private void dashboard() {
        try {
            BigDecimal[] s = sales.today();
            setMetric("PENJUALAN HARI INI", fmt(s[1]));
            setMetric("TRANSAKSI HARI INI", s[0].toBigInteger().toString());
            int low = 0;
            for (Product p : products.search("")) if (p.getStock() <= 5) low++;
            setMetric("STOK MENIPIS", String.valueOf(low));
        } catch (Exception e) {
            if (status != null) status.setText("Database belum terhubung; jalankan MySQL di XAMPP.");
        }
    }

    private void setMetric(String k, String v) {
        JLabel l = metrics.get(k);
        if (l != null) l.setText(v);
    }

    private void exportCsv() {
        JFileChooser ch = new JFileChooser();
        ch.setSelectedFile(new java.io.File("laporan-benkeyshop.csv"));
        if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (java.io.PrintWriter out = new java.io.PrintWriter(ch.getSelectedFile(), "UTF-8")) {
            out.println("No Struk,Tanggal,Pembeli,Subtotal,Diskon,Total");
            for (int i = 0; i < salesModel.getRowCount(); i++) {
                for (int j = 0; j < salesModel.getColumnCount(); j++) {
                    if (j > 0) out.print(',');
                    out.print("\"" + String.valueOf(salesModel.getValueAt(i, j)).replace("\"", "\"\"") + "\"");
                }
                out.println();
            }
            msg("CSV diekspor.");
        } catch (Exception e) { err(e); }
    }

    /** Menjalankan aplikasi kasir langsung (tanpa form pembuka). */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main.pasangTema();
            new AplikasiKasir().setVisible(true);
        });
    }
}
