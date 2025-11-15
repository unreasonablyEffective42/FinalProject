import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class Cas extends JFrame {
    private final JTextField inputField;
    private final JLabel renderLabel;
    private final JLabel resultPlaceholder;
    private final Renderer renderer;
    private final Evaluator evaluator;

    public Cas() {
        super("CAS Renderer");
        renderer = new Renderer();
        evaluator = new Evaluator();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setPreferredSize(new Dimension(600, 400));

        JPanel displayPanel = new JPanel();
        displayPanel.setLayout(new BoxLayout(displayPanel, BoxLayout.Y_AXIS));
        displayPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        renderLabel = new JLabel("Enter an expression and press Generate", JLabel.RIGHT);
        renderLabel.setOpaque(true);
        renderLabel.setBackground(Color.WHITE);
        renderLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        renderLabel.setHorizontalAlignment(JLabel.RIGHT);
        renderLabel.setIconTextGap(12);
        renderLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        resultPlaceholder = new JLabel("Result will appear here", JLabel.RIGHT);
        resultPlaceholder.setOpaque(true);
        resultPlaceholder.setBackground(new Color(245, 245, 245));
        resultPlaceholder.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resultPlaceholder.setHorizontalAlignment(JLabel.RIGHT);
        resultPlaceholder.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topBox = new JPanel(new BorderLayout());
        topBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        topBox.add(renderLabel, BorderLayout.EAST);
        topBox.setOpaque(false);

        JPanel bottomBox = new JPanel(new BorderLayout());
        bottomBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        bottomBox.add(resultPlaceholder, BorderLayout.EAST);
        bottomBox.setOpaque(false);

        displayPanel.add(topBox);
        displayPanel.add(bottomBox);
        add(displayPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(8, 8));
        inputField = new JTextField("sqrt(3/4) + 5/4*cos(x)");
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
            renderLabel.setText("Enter an expression first.");
            renderLabel.setIcon(null);
            return;
        }
        try {
            Parser parser = new Parser(source);
            Expression parsed = parser.parse();
            Expression simplified = evaluator.simplify(parsed);

            BufferedImage originalImage = renderToImage(renderer.toTex(parsed), renderLabel);
            BufferedImage simplifiedImage = renderToImage(renderer.toTex(simplified), resultPlaceholder);

            renderLabel.setIcon(new ImageIcon(originalImage));
            renderLabel.setText(null);

            resultPlaceholder.setIcon(new ImageIcon(simplifiedImage));
            resultPlaceholder.setText(null);

            Dimension simplifiedSize = new Dimension(simplifiedImage.getWidth(), simplifiedImage.getHeight());
            resultPlaceholder.setPreferredSize(simplifiedSize);
            resultPlaceholder.setMinimumSize(simplifiedSize);
            resultPlaceholder.setMaximumSize(new Dimension(Integer.MAX_VALUE, simplifiedImage.getHeight()));
        } catch (Exception ex) {
            renderLabel.setIcon(null);
            renderLabel.setText("Error: " + ex.getMessage());
            resultPlaceholder.setIcon(null);
            resultPlaceholder.setText("");
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
