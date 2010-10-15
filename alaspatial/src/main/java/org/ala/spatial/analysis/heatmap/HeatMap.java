package org.ala.spatial.analysis.heatmap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ByteLookupTable;
import java.awt.image.ColorModel;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.IndexColorModel;
import java.awt.image.LookupOp;
import java.awt.image.LookupTable;
import java.awt.image.RGBImageFilter;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * HeatMap generator for species. 
 * 
 * Code based on http://www.itstud.chalmers.se/~liesen/heatmap/
 *
 * @author ajay
 */
public class HeatMap {

    private static String DOT_PATH = "/Users/ajay/projects/ala/code/alageospatialportal/alaspatial/target/ala-spatial-1.0-SNAPSHOT/images/bullet_blue.png";
    private static BufferedImage backgroundImage;
    private int radius = 8;
    private int numColours = 10;
    private BufferedImage dotImage; /// createFadedCircleImage(radius);
    private BufferedImage monochromeImage;
    private BufferedImage heatmapImage;
    private BufferedImage colorImage;
    private LookupTable colorTable;
    private LookupOp colorOp;
    private double minX = 0;
    private double minY = 0;
    private double maxX = 0;
    private double maxY = 0;
    private double bMinX = 112.911;
    private double bMinY = -54.778;
    private double bMaxX = 159.113;
    private double bMaxY = -9.221;
    private File baseDir;
    private String baseFile;

    public HeatMap() {
        initImages();
    }

    public HeatMap(File baseDir, String baseFile) {
        this.baseDir = baseDir;
        this.baseFile = baseFile;

        initImages();
    }

    private void initImages() {
        System.out.println("Got base dir in initimages: " + baseDir.getAbsolutePath());
        System.err.println("Got base dir in initimages: " + baseDir.getAbsolutePath()); 
        try {
            backgroundImage = ImageIO.read(new File(baseDir.getAbsolutePath() + "/base/mapaus1_white.png"));
            dotImage = getDotImageFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int width = backgroundImage.getWidth();
        int height = backgroundImage.getHeight();

        //colorImage = createEvenlyDistributedGradientImage(new Dimension(
        //        84, 20), Color.WHITE, Color.RED, Color.YELLOW, Color.GREEN.darker(), Color.CYAN, Color.BLUE, new Color(0, 0, 0x33));

//        colorImage = createEvenlyDistributedGradientImage(new Dimension(
//                512, 20), Color.RED.darker(), Color.RED, Color.YELLOW.darker(),
//                Color.YELLOW, Color.GREEN.darker(), Color.GREEN, Color.CYAN,
//                Color.CYAN.darker(), Color.BLUE.darker(), Color.BLUE,
//                Color.WHITE);

//        colorImage = createEvenlyDistributedGradientImage(new Dimension(
//                512, 20), new Color(255, 63, 63), new Color(255, 79, 79),
//                new Color(255, 95, 95), new Color(255, 111, 111),
//                new Color(255, 127, 127), new Color(255, 143, 143),
//                new Color(255, 159, 159), new Color(255, 175, 175),
//                new Color(255, 191, 191), new Color(255, 207, 207),
//                Color.WHITE);
        
        colorImage = createEvenlyDistributedGradientImage(new Dimension(
                512, 20), new Color(255, 0, 0), new Color(255, 30, 0),
                new Color(255, 60, 0), new Color(255, 90, 0),
                new Color(255, 120, 0), new Color(255, 150, 0),
                new Color(255, 180, 0), new Color(255, 210, 0),
                new Color(255, 230, 0), new Color(255, 255, 0),
                Color.WHITE);
        
//        colorImage = createEvenlyDistributedGradientImage(new Dimension(
//                512, 20), Color.RED, Color.YELLOW, Color.GREEN.darker(),
//                Color.CYAN, Color.BLUE,
//                Color.WHITE);

        colorTable = createColorLookupTable(colorImage, .5f);
        colorOp = new LookupOp(colorTable, null);

        monochromeImage = createCompatibleTranslucentImage(
                width, height);

        System.out.println("mi.type: " + monochromeImage.getType());

        Graphics g = monochromeImage.getGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        /*
        try {
            File ciOut = new File(baseDir.getAbsolutePath() + "/colorImage.png");
            ImageIO.write(colorImage, "png", ciOut);

            File miOut = new File(baseDir.getAbsolutePath() + "/monochromeImage.png");
            ImageIO.write(monochromeImage, "png", miOut);

            File bgOut = new File(baseDir.getAbsolutePath() + "/backgroundImage.png");
            ImageIO.write(backgroundImage, "png", bgOut);
        } catch (Exception e) {
            System.out.println("Unable to write monochromeImage:");
            e.printStackTrace(System.out);
        }
         * 
         */

    }

    /**
     * Creates the color lookup table from an image
     *
     * @param im
     * @return
     */
    public static LookupTable createColorLookupTable(BufferedImage im,
            float alpha) {
        int tableSize = 256;
        Raster imageRaster = im.getData();
        double sampleStep = 1D * im.getWidth() / tableSize; // Sample pixels
        // evenly
        byte[][] colorTable = new byte[4][tableSize];
        int[] pixel = new int[1]; // Sample pixel
        Color c;

        for (int i = 0; i < tableSize; ++i) {
            imageRaster.getDataElements((int) (i * sampleStep), 0, pixel);

            c = new Color(pixel[0]);

            colorTable[0][i] = (byte) c.getRed();
            colorTable[1][i] = (byte) c.getGreen();
            colorTable[2][i] = (byte) c.getBlue();
            colorTable[3][i] = (byte) (alpha * 0xff);
        }

        LookupTable lookupTable = new ByteLookupTable(0, colorTable);

        return lookupTable;
    }

    public static BufferedImage createEvenlyDistributedGradientImage(
            Dimension size, Color... colors) {
        BufferedImage im = createCompatibleTranslucentImage(
                size.width, size.height);
        Graphics2D g = im.createGraphics();

        float[] fractions = new float[colors.length];
        float step = 1f / colors.length;

        for (int i = 0; i < colors.length; ++i) {
            fractions[i] = i * step;
        }

        LinearGradientPaint gradient = new LinearGradientPaint(
                0, 0, size.width, 1, fractions, colors,
                MultipleGradientPaint.CycleMethod.REPEAT);

        g.setPaint(gradient);
        g.fillRect(0, 0, size.width, size.height);

        g.dispose();

        return im;
    }

    public static BufferedImage createCompatibleTranslucentImage(int width,
            int height) {
        /*
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();

        return gc.createCompatibleImage(
        width, height, Transparency.TRANSLUCENT);
         *
         */

        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        int[] image_bytes;

        image_bytes = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
                null, 0, image.getWidth());

        /* try transparency as missing value */
        for (int i = 0; i < image_bytes.length; i++) {
            image_bytes[i] = 0x00000000;
        }

        /* write bytes to image */
        image.setRGB(0, 0, image.getWidth(), image.getHeight(),
                image_bytes, 0, image.getWidth());

        return image;

    }

    public BufferedImage doColorize() {

        System.out.println("doColorize()");

        int[] image_bytes, image_bytes2;

        image_bytes = colorImage.getRGB(0, 0, colorImage.getWidth(), colorImage.getHeight(),
                null, 0, colorImage.getWidth());
        image_bytes2 = monochromeImage.getRGB(0, 0, monochromeImage.getWidth(), monochromeImage.getHeight(),
                null, 0, monochromeImage.getWidth());

        //System.out.println("imagebytes.1: " + image_bytes.length);
        //System.out.println("imagebytes.2: " + image_bytes2.length);

        for (int i = 0; i < image_bytes2.length; i++) {
            //System.out.print(image_bytes2[i] + " --> ");
            int pos = image_bytes2[i] & 0x000000ff;
            //System.out.print("at " + pos * 2 + " ==> ");
            image_bytes2[i] = image_bytes[pos * 2] & 0x99ffffff;
            //System.out.println(image_bytes2[i]);
        }

        /* write bytes to image */
        BufferedImage biColorized = new BufferedImage(monochromeImage.getWidth(), monochromeImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        biColorized.setRGB(0, 0, biColorized.getWidth(), biColorized.getHeight(),
                image_bytes2, 0, biColorized.getWidth());

        return biColorized;
    }

    public static Image makeColorTransparent(BufferedImage im, final Color color) {
        ImageFilter filter = new RGBImageFilter() {

            // the color we are looking for... Alpha bits are set to opaque
            public int markerRGB = color.getRGB() | 0xFF000000;

            public final int filterRGB(int x, int y, int rgb) {
                if ((rgb | 0xFF000000) == markerRGB) {
                    // Mark the alpha bits as zero - transparent
                    return 0x00FFFFFF & rgb;
                    //return (rgb) | (rgb << 8) | (rgb << 16) | 0xff000000;
                } else {
                    // nothing to do
                    return rgb;
                }
            }
        };

        ImageProducer ip = new FilteredImageSource(im.getSource(), filter);
        return Toolkit.getDefaultToolkit().createImage(ip);
    }

    public BufferedImage colorize(LookupOp colorOp) {
        return colorOp.filter(monochromeImage, null);
    }

    public BufferedImage colorize(LookupTable colorTable) {
        return colorize(new LookupOp(colorTable, null));
    }

    public static BufferedImage createFadedCircleImage(int size) {
        BufferedImage im = createCompatibleTranslucentImage(size, size);
        float radius = size / 2f;

        RadialGradientPaint gradient = new RadialGradientPaint(
                radius, radius, radius, new float[]{0f, 1f}, new Color[]{
                    Color.BLACK, new Color(0xffffffff, true)});

        Graphics2D g = (Graphics2D) im.getGraphics();

        g.setPaint(gradient);
        g.fillRect(0, 0, size, size);

        g.dispose();

        return im;
    }

    private void addDotImage(Point p, float alpha) {
        int circleRadius = dotImage.getWidth() / 2;

        //System.out.println("adding point at: " + p.x + ", " + p.y);

        Graphics2D g = (Graphics2D) monochromeImage.getGraphics();

        g.setComposite(BlendComposite.Multiply.derive(alpha));
        g.drawImage(dotImage, null, p.x - circleRadius, p.y - circleRadius);
    }

    private void addDotImage(Point p) {
        //int circleRadius = dotImage.getWidth() / 2;
        Graphics2D g = (Graphics2D) monochromeImage.getGraphics();
        float radius = 10f;
        Shape circle = new Ellipse2D.Float(p.x - radius, p.y - radius, radius, radius);
        //g.drawImage(dotImage, null, p.x - circleRadius, p.y - circleRadius);
        g.draw(circle);
        g.setPaint(Color.red);
        g.fill(circle);
    }

    private BufferedImage getDotImageFile() {
        try {
            if (baseDir != null) {
                System.out.println("Reading dot image: " + baseDir.getAbsolutePath() + "/base/bullet_blue.png");
                return ImageIO.read(new File(baseDir.getAbsolutePath() + "/base/bullet_blue.png"));
            }
        } catch (Exception ex) {
            System.out.println("Unable to load Dot Image File: " + baseDir.getAbsolutePath() + "/base/bullet_blue.png");
        }

        // this should return a faded circle image instead
        System.out.println("No image, so returning a generated one instead");
        return createFadedCircleImage(radius);
    }

    private void setMinMax(Vector v) {
        try {

            minX = bMinX;
            minY = bMinY;
            maxX = bMaxX;
            maxY = bMaxY;

            /*
            System.out.println("Setting the base min-max");
            String idxpts[] = ((String) v.get(0)).split(",");
            double ix = Double.parseDouble(idxpts[0]);
            double iy = Double.parseDouble(idxpts[1]);
            minX = ix;
            minY = iy;
            maxX = ix;
            maxY = iy;


            for (int i = 1; i < v.size(); i++) {
            //for (int j = 0; j < points[i].length; j++) {}
            //if (points[i][0] < minX) minX = points[i][0];
            //else if (points[i][0] < minX) minX = points[i][0];
            String strpts[] = ((String) v.get(i)).split(",");
            double cx = Double.parseDouble(strpts[0]);
            double cy = Double.parseDouble(strpts[1]);

            System.out.println("Have: " + (String) v.get(i));
            System.out.println("checking minx: " + (cx < minX));
            if (cx < minX) {
            minX = cx;
            }
            System.out.println("checking miny: " + (cy < minY));
            if (cy < minY) {
            minY = cy;
            }
            System.out.println("checking maxX: " + (cx > maxX));
            if (cx > maxX) {
            maxX = cx;
            }
            System.out.println("checking maxY: " + (cy > maxY));
            if (cy > maxY) {
            maxY = cy;
            }
            }
             * 
             */
        } catch (Exception e) {
            System.out.println("error generating min-max");
            e.printStackTrace(System.out);
        }
    }

    private Point translate(double x, double y) {
        try {
            //System.out.println("translating: " + x + ", " + y);
            // normalize points into range (0 - 1)...
            x = (x - minX) / (maxX - minX);
            y = (y - minY) / (maxY - minY);

            //System.out.println("normalised: " + x + ", " + y);
            // ...and the map into our image size...
            x = (x * backgroundImage.getWidth());
            y = ((1 - y) * backgroundImage.getHeight());

            //System.out.println("pixeled: " + x + ", " + y);
            return new Point(new Double(x).intValue(), new Double(y).intValue());
        } catch (Exception e) {
            System.out.println("Exception with translating:");
            e.printStackTrace(System.out);
        }
        return null;
    }

    private void generateLogScaleCircle(int dPoints[][]) {
        try {

            int maxValue = 0;
            int width = monochromeImage.getWidth();
            int height = monochromeImage.getHeight();

            for (int mi = 0; mi < width; mi++) {
                for (int mj = 0; mj < height; mj++) {
                    if (maxValue < dPoints[mi][mj]) {
                        maxValue = dPoints[mi][mj];
                    }
                }
            }

            System.out.println("maxW: " + maxValue);

            // we are doing "1" here to make sure nothing is 0
            int roundFactor = 1;

            for (int mi = 0; mi < width; mi++) {
                for (int mj = 0; mj < height; mj++) {
                    int rgba = (int) (255 - Math.log(dPoints[mi][mj]) * 255 / Math.log((double) maxValue));
                    if (rgba < 255 && rgba > 255 - (255 / numColours) - roundFactor) {
                        rgba = 255 - (255 / numColours) - roundFactor;
                    }
                    //rgba <<= 8;
                    //
                    //rgba = (((short)rgba) & 0x000000ff) << 24;
                    rgba = (rgba) | (rgba << 8) | (rgba << 16) | 0xff000000;
                    //System.out.println("rgba: " + Integer.toHexString(rgba));

                    monochromeImage.setRGB(mi, mj, rgba);
                }
            }

            /////getScale(dPoints, maxValue);
        } catch (Exception e) {
            System.out.println("Error generating log scale circle:");
            e.printStackTrace(System.out);
        }
    }

    public File loadData(String filename) {
        return new File(filename);
    }

    public void loadData(File filename) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(filename));
            Vector v = new Vector();
            //System.out.println("reading file...");

            while (true) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                //System.out.println("> " + line);
                v.add(line);
            }
            in.close();

            //System.out.println("Setting min-max");
            setMinMax(v);
            generateClasses(v);

            //drawOuput();
        } catch (Exception e) {
            System.out.println("Opps, error reading file: " + e.toString());
            e.printStackTrace(System.out);
        }
    }

    private void generateClasses(Vector<String> v) {
        setMinMax(null);
        int width = backgroundImage.getWidth();
        int height = backgroundImage.getHeight();
        //System.out.println("bounding box: " + minX + "," + minY + "," + maxX + "," + maxY);
        System.out.println("Adding " + v.size() + " points to base image...");
        //System.out.println("Adding to base image...");
        int dPoints[][] = new int[width][height];
        for (int i = 0; i < v.size(); i++) {
            String strpts[] = (v.get(i)).split(",");
            double cx = Double.parseDouble(strpts[0]);
            double cy = Double.parseDouble(strpts[1]);
            Point p = translate(cx, cy);

            int pradius = radius * radius;

            for (int ci = (int) (p.x - radius); ci <= (p.x + radius); ci++) {
                for (int cj = (int) (p.y - radius); cj <= (p.y + radius); cj++) {
                    if (ci >= 0 && ci < width && cj >= 0 && cj < height) {
                        double d = Math.pow((p.x - ci), 2) + Math.pow((p.y - cj), 2);
                        if ((int) d <= pradius) {
                            // applying gradient to this circle so outter influence is low
                            // and at the peak it's maximum
                            dPoints[ci][cj] += numColours - ((d * numColours) / pradius);
                        }
                    }
                }
            }

            //addDotImage(p, .75f);
        }

        generateLogScaleCircle(dPoints);
    }

    public void generateClasses(double[] v) {
        setMinMax(null);

        int width = backgroundImage.getWidth();
        int height = backgroundImage.getHeight();
        //System.out.println("bounding box: " + minX + "," + minY + "," + maxX + "," + maxY);
        System.out.println("Adding " + (v.length / 2) + " points to base image...");
        //System.out.println("Adding to base image...");
        int dPoints[][] = new int[width][height];
        for (int i = 0; i < v.length; i += 2) {
            double cx = v[i];
            double cy = v[i + 1];

            Point p = translate(cx, cy);

            int pradius = radius * radius;

            for (int ci = (int) (p.x - radius); ci <= (p.x + radius); ci++) {
                for (int cj = (int) (p.y - radius); cj <= (p.y + radius); cj++) {
                    if (ci >= 0 && ci < width && cj >= 0 && cj < height) {
                        double d = Math.pow((p.x - ci), 2) + Math.pow((p.y - cj), 2);
                        if ((int) d <= pradius) {
                            // applying gradient to this circle so outter influence is low
                            // and at the peak it's maximum
                            dPoints[ci][cj] += numColours - ((d * numColours) / pradius);
                        }
                    }
                }
            }

            //addDotImage(p, .75f);
        }

        generateLogScaleCircle(dPoints);
    }

    public void generatePoints(double[] v) {
        setMinMax(null);

        int width = backgroundImage.getWidth();
        int height = backgroundImage.getHeight();
        //System.out.println("bounding box: " + minX + "," + minY + "," + maxX + "," + maxY);
        System.out.println("Adding " + (v.length / 2) + " points to base image...");
        //System.out.println("Adding to base image...");
        int dPoints[][] = new int[width][height];
        for (int i = 0; i < v.length; i += 2) {
            double cx = v[i];
            double cy = v[i + 1];

            Point p = translate(cx, cy);

            addDotImage(p);
        }
    }

    private void getScale(int dPoints[][], int maxValue) {

        System.out.println("generating scale...");

        int scale[] = new int[numColours - 1];
        scale[0] = maxValue;
        for (int i = 1; i < scale.length - 1; i++) {
            scale[i] = (int) Math.pow(Math.E, ((numColours - i) * (Math.log((double) maxValue) / numColours)));
            //scale[i] = (int)Math.pow(Math.E,  scale[i]);
        }
        scale[scale.length - 1] = 0;

//        String sdata = "";
//        int slength = colorImage.getWidth();
//
//        for (int j = 0; j < scale.length; j++) {
//            System.out.println(j + " - " + scale[j]);
//            //sdata += "    " + scale[j] + "    ";
//            sdata += "";
//        }
//
//        System.out.println("Displaying scale: " + sdata);

        try {
            File ciOut = new File(baseDir.getAbsolutePath() + "/../output/legend_" + baseFile + ".png");
            Graphics cg = colorImage.getGraphics();
            cg.setColor(Color.BLACK);
            String sdata = "";
            int slength = colorImage.getWidth();

            for (int j = 0; j < scale.length; j++) {
                System.out.println(j + " - " + scale[j]);
                //sdata += "    " + scale[j] + "    ";
                //System.out.println("writing ");
                cg.drawChars(Integer.toString(scale[j]).toCharArray(), 0, sdata.length(), slength - j, 10);
            }
            //cg.drawChars(sdata.toCharArray(), 0, sdata.length(), 5, 10);
            ImageIO.write(colorImage, "png", ciOut);
        } catch (Exception e) {
            System.out.println("Unable to write colorImage:");
            e.printStackTrace(System.out);
        }


    }

    public void drawOuput(String outfile, boolean colorize) {
        try {

            //File miOut = new File(baseDir.getAbsolutePath() + "/monochromeImage2.png");
            //ImageIO.write(monochromeImage, "png", miOut);


            //System.out.println("generating heat map image...");
            //heatmapImage = colorize(colorOp);
            if (colorize) {
                heatmapImage = doColorize();
            } else {
                heatmapImage = monochromeImage;
            }


            //File hmbOut = new File(baseDir.getAbsolutePath() + "/heatmapImage.png");
            //ImageIO.write(heatmapImage, "png", hmbOut);

            //System.out.println("writing heat map image...");
            Graphics2D g = (Graphics2D) backgroundImage.getGraphics();
            g.drawImage(makeColorTransparent(heatmapImage, Color.WHITE), 0, 0, null);

            File hmOut = new File(outfile);
            ImageIO.write(backgroundImage, "png", hmOut);
            //File hmOut2 = new File(outfile + "monochromeImage.png");
            //ImageIO.write(monochromeImage, "png", hmOut2);
            System.out.println("done");

        } catch (Exception ex) {
            ex.printStackTrace(System.out);
        }
    }

    public static String toRgbText(int rgb) {
        // clip input value.

        if (rgb > 0xFFFFFF) {
            rgb = 0xFFFFFF;
        }
        if (rgb < 0) {
            rgb = 0;
        }

        String str = "000000" + Integer.toHexString(rgb); //$NON-NLS-1$
        return "#" + str.substring(str.length() - 6); //$NON-NLS-1$
    }

    public static void main(String[] args) {
        System.out.println("Generating HeaMap");

        File baseDataDir = new File("/Users/ajay/projects/tmp/heatmap/data/");
        File[] datafiles = baseDataDir.listFiles();
        for (File datafile : datafiles) {
            if (datafile.isDirectory()) {
                continue;
            }
            // "/Users/ajay/projects/tmp/heatmap/occurrencesMacropusRufus.csv"
            String pfname = datafile.getName();
            pfname = pfname.substring(0, pfname.lastIndexOf(".")) + ".png";
            String fname = baseDataDir.getAbsolutePath() + "/../output/" + pfname;
            System.out.println("Generating heatmap for : " + datafile.getName());
            HeatMap hm = new HeatMap(baseDataDir, datafile.getName());
            hm.loadData(datafile);
            hm.drawOuput(fname, true);
        }


//        System.out.println("0x33: " + toRgbText(0x33));
//        System.out.println("0xff: " + toRgbText(0xff));
//        System.out.println("0xffffffff: " + toRgbText(0xffffffff));
//        System.out.println("0xff000000: " + toRgbText(0x00000000));
    }
}