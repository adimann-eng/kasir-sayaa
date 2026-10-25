import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {

    /**
     * Memasang tema gelap FlatLaf (tema BenkeyShop) bila library flatlaf.jar
     * ada di classpath. Jika tidak ada, aplikasi tetap berjalan dengan tema bawaan Swing.
     */
    public static void pasangTema() {
        try {
            Class.forName("com.formdev.flatlaf.FlatDarkLaf").getMethod("setup").invoke(null);
        } catch (Throwable ignored) {
            // FlatLaf tidak tersedia -> pakai tampilan standar
        }
        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 16);
    }

    public static void main(String[] args) {
        pasangTema();
        // Memanggil jendela pembuka dari file KasirFrame.java
        SwingUtilities.invokeLater(KasirFrame::new);
    }
}
