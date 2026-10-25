import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.BorderLayout;

/**
 * Form pembuka BenkeyShop.
 * Dibangun lewat kode Swing (bukan GUI Builder) dan membuka AplikasiKasir.
 */
public class KasirFrame extends JFrame {
    private JPanel mainPanel;
    private JLabel brandLabel, subtitleLabel, descriptionLabel, footerLabel;
    private JButton openButton, exitButton;

    public KasirFrame() {
        // Mengatur Judul dan Ukuran Jendela
        setTitle("BenkeyShop | Smart Retail");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(700, 460));
        setPreferredSize(new Dimension(820, 520));

        initKomponen();
        pack();
        setLocationRelativeTo(null); // Agar posisinya di tengah layar
        getContentPane().setBackground(new Color(17, 24, 27));

        // Menampilkan Jendela ke Layar
        setVisible(true);
    }

    private void initKomponen() {
        mainPanel = new JPanel();
        brandLabel = new JLabel();
        subtitleLabel = new JLabel();
        descriptionLabel = new JLabel();
        openButton = new JButton();
        exitButton = new JButton();
        footerLabel = new JLabel();

        mainPanel.setBackground(new Color(17, 24, 27));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(35, 45, 25, 45));

        brandLabel.setFont(new Font("SansSerif", Font.BOLD, 42));
        brandLabel.setForeground(new Color(153, 220, 92));
        brandLabel.setText("BenkeyShop");

        subtitleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        subtitleLabel.setForeground(new Color(166, 181, 177));
        subtitleLabel.setText("SMART RETAIL  /  POINT OF SALE");

        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        descriptionLabel.setForeground(new Color(230, 238, 234));
        descriptionLabel.setText("Kelola produk, transaksi kasir, struk, dan laporan penjualan dalam satu aplikasi.");

        // Tombol Buka Aplikasi Kasir
        openButton.setBackground(new Color(153, 220, 92));
        openButton.setFont(new Font("SansSerif", Font.BOLD, 15));
        openButton.setForeground(new Color(20, 29, 24));
        openButton.setText("Buka Aplikasi Kasir  →");
        openButton.setFocusPainted(false);
        openButton.addActionListener(evt -> bukaAplikasiKasir());

        // Tombol Keluar
        exitButton.setBackground(new Color(43, 58, 57));
        exitButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        exitButton.setForeground(Color.WHITE);
        exitButton.setText("Keluar");
        exitButton.setFocusPainted(false);
        exitButton.addActionListener(evt -> System.exit(0));

        footerLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footerLabel.setForeground(new Color(130, 150, 143));
        footerLabel.setText("JAVA 21  •  MAVEN  •  MYSQL / XAMPP  •  OOP");

        // Susun komponen dengan GroupLayout (ditulis manual lewat kode)
        GroupLayout layout = new GroupLayout(mainPanel);
        mainPanel.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(brandLabel)
                .addComponent(subtitleLabel)
                .addComponent(descriptionLabel)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(openButton, GroupLayout.PREFERRED_SIZE, 230, GroupLayout.PREFERRED_SIZE)
                    .addGap(12, 12, 12)
                    .addComponent(exitButton, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE))
                .addComponent(footerLabel)
        );
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGap(65, 65, 65)
                .addComponent(brandLabel)
                .addGap(4, 4, 4)
                .addComponent(subtitleLabel)
                .addGap(26, 26, 26)
                .addComponent(descriptionLabel)
                .addGap(32, 32, 32)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(openButton, GroupLayout.PREFERRED_SIZE, 48, GroupLayout.PREFERRED_SIZE)
                    .addComponent(exitButton, GroupLayout.PREFERRED_SIZE, 48, GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 60, Short.MAX_VALUE)
                .addComponent(footerLabel)
        );

        getContentPane().add(mainPanel, BorderLayout.CENTER);
    }

    private void bukaAplikasiKasir() {
        dispose();
        SwingUtilities.invokeLater(() -> new AplikasiKasir().setVisible(true));
    }
}
