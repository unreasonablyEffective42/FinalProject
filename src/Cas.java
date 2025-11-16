import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class Cas extends JFrame {
    private final JTextField inputField;
    private final Renderer renderer;
    private final Evaluator evaluator;
    private final JPanel historyPanel;
    private final JScrollPane scrollPane;

    public Cas() {
        super("CAS Renderer");
        renderer = new Renderer();
        evaluator = new Evaluator();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setPreferredSize(new Dimension(600, 400));

        historyPanel = new JPanel();
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(Color.WHITE);
        historyPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        scrollPane = new JScrollPane(historyPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(8, 8));
        inputField = new JTextField();
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                renderExpression();
            }
        });
        JButton generateButton = new JButton("Generate");
        generateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                renderExpression();
            }
        });

        controlPanel.add(inputField, BorderLayout.CENTER);
        controlPanel.add(generateButton, BorderLayout.EAST);
        controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        add(controlPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void renderExpression() {
        String source = inputField.getText().trim();
        if (source.isEmpty()) {
            return;
        }
        try {
            Parser originalParser = new Parser(source, false, false);
            Expression original = originalParser.parse();

            Parser evaluatedParser = new Parser(source, true, true);
            Expression parsed = evaluatedParser.parse();
            Expression simplified = evaluator.simplify(parsed);

            BufferedImage originalImage = renderToImage(renderer.toTex(original), this);
            BufferedImage simplifiedImage = renderToImage(renderer.toTex(simplified), this);
            addHistoryEntry(originalImage, simplifiedImage);
            inputField.setText("");
        } catch (Exception ex) {
            addErrorEntry(ex.getMessage());
        }
    }

    private BufferedImage renderToImage(TeXFormula formula, Component owner) {
        TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 36f);
        BufferedImage image = new BufferedImage(
                icon.getIconWidth(),
                icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        icon.paintIcon(owner, g2, 0, 0);
        g2.dispose();
        return image;
    }

    private void addHistoryEntry(BufferedImage originalImage, BufferedImage simplifiedImage) {
        JPanel entry = new JPanel();
        entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
        entry.setBackground(Color.WHITE);
        entry.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        entry.add(createImageLabel(originalImage, "Original expression"));
        entry.add(createDivider());
        entry.add(createImageLabel(simplifiedImage, "Result"));

        historyPanel.add(entry);
        historyPanel.add(Box.createVerticalStrut(8));
        historyPanel.add(createDivider());
        historyPanel.add(Box.createVerticalStrut(8));
        historyPanel.revalidate();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                verticalBar.setValue(verticalBar.getMaximum());
            }
        });
    }

    private void addErrorEntry(String message) {
        JPanel entry = new JPanel();
        entry.setLayout(new BorderLayout());
        entry.setBackground(Color.WHITE);
        entry.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel label = new JLabel("Error: " + message, JLabel.LEFT);
        label.setForeground(Color.RED);
        entry.add(label, BorderLayout.CENTER);
        historyPanel.add(entry);
        historyPanel.add(Box.createVerticalStrut(8));
        historyPanel.add(createDivider());
        historyPanel.add(Box.createVerticalStrut(8));
        historyPanel.revalidate();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                verticalBar.setValue(verticalBar.getMaximum());
            }
        });
    }

    private JLabel createImageLabel(BufferedImage image, String fallback) {
        JLabel label = new JLabel(fallback, JLabel.LEFT);
        label.setOpaque(true);
        label.setBackground(Color.WHITE);
        label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (image != null) {
            label.setIcon(new ImageIcon(image));
            label.setText(null);
        }
        return label;
    }

    private JComponent createDivider() {
        JComponent divider = new JPanel();
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        divider.setPreferredSize(new Dimension(Integer.MAX_VALUE, 2));
        divider.setMinimumSize(new Dimension(0, 2));
        divider.setBackground(Color.BLACK);
        return divider;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Cas cas = new Cas();
                cas.setVisible(true);
            }
        });
    }
}
