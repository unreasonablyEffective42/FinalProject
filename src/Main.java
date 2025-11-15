import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.scilab.forge.jlatexmath.TeXConstants;


class ImageDisplay extends JFrame {
    private JLabel imageLabel;
    private BufferedImage image;

    public ImageDisplay(BufferedImage image,String name, int width, int height){

        this.image = image;
        setTitle(name);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(width, height);
        setLocationRelativeTo(null);

        imageLabel = new JLabel(new ImageIcon(this.image));
        add(imageLabel);

        setVisible(true);

    }

    public void refresh() {
        imageLabel.setIcon(new ImageIcon(image));
        imageLabel.revalidate();
        imageLabel.repaint();
    }
}

public class Main{
    public static void main(String[] args){
        String src = "sqrt(3/4) + 5/4cos(x^2/3)";
        Parser parser = new Parser(src);
        Renderer renderer = new Renderer();
        TeXFormula form = renderer.toTex(parser.parse());
        TeXIcon icon = form.createTeXIcon(TeXConstants.STYLE_DISPLAY, 36f);
        BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB); 
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        icon.paintIcon(null, g2, 0, 0);
        g2.dispose();
        ImageDisplay window = new ImageDisplay(img,"LaTeX", img.getWidth(), img.getHeight());
    }
}
