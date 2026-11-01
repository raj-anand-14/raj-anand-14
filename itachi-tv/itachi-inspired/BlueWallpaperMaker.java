import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;

public final class BlueWallpaperMaker {
    private static final int W = 1920;
    private static final int H = 1080;

    private static final class Grade {
        final String name;
        final double red, green, blue, addBlue, dark;
        Grade(String name, double red, double green, double blue, double addBlue, double dark) {
            this.name = name; this.red = red; this.green = green; this.blue = blue;
            this.addBlue = addBlue; this.dark = dark;
        }
    }

    public static void main(String[] args) throws Exception {
        File sourceFile = new File(args[0]);
        File outputDir = new File(args[1]);
        BufferedImage source = ImageIO.read(sourceFile);
        if (source == null || source.getWidth() != 3840 || source.getHeight() != 2400) {
            throw new IllegalArgumentException("Expected the uploaded 3840x2400 wallpaper");
        }
        Grade[] grades = {
            new Grade("01-blue-rain.jpg", .78, .92, 1.18, 13, .98),
            new Grade("02-midnight-blue.jpg", .62, .75, 1.12, 10, .90),
            new Grade("03-moonlit-blue.jpg", .72, .93, 1.28, 18, 1.00)
        };
        for (Grade grade : grades) {
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, W, H, 0, 120, 3840, 2280, null);
            graphics.dispose();
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                double red = (pixel >>> 16) & 255;
                double green = (pixel >>> 8) & 255;
                double blue = pixel & 255;
                boolean redEye = red > green * 1.22 && red > blue * 1.26;
                double nr = redEye ? red * 1.02 : red * grade.red;
                double ng = redEye ? green * .76 : green * grade.green;
                double nb = redEye ? blue * .78 : blue * grade.blue + grade.addBlue;
                int r = clamp(nr * grade.dark), g = clamp(ng * grade.dark), b = clamp(nb * grade.dark);
                pixels[i] = (r << 16) | (g << 8) | b;
            }
            File output = new File(outputDir, grade.name);
            ImageIO.write(image, "jpg", output);
            System.out.println(grade.name + ": " + output.length() + " bytes");
            image.flush();
        }
        source.flush();
    }

    private static int clamp(double value) {
        return (int)Math.max(0, Math.min(255, Math.round(value)));
    }
}
