import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rasterises the flat, single-colour-fill SVGs used by this plugin. Deliberately minimal:
 * the icons only use absolute moveto / cubic curveto / closepath, plus a fill colour and a
 * translate transform per path, so a full SVG engine is not needed.
 */
public final class SvgToPng
{
    private static final Pattern PATH = Pattern.compile("<path\\b([^>]*)>");
    private static final Pattern ATTR = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern NUMBER = Pattern.compile("-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?");

    public static void main(String[] args) throws Exception
    {
        String svgFile = args[0];
        String pngFile = args[1];
        int size = Integer.parseInt(args[2]);

        String svg = new String(Files.readAllBytes(Paths.get(svgFile)), StandardCharsets.UTF_8);
        double srcWidth = attrValue(svg.substring(0, svg.indexOf('>') + 1), "width", 512);
        double srcHeight = attrValue(svg.substring(0, svg.indexOf('>') + 1), "height", 512);

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / srcWidth, size / srcHeight);

        Matcher m = PATH.matcher(svg);
        int drawn = 0;
        while (m.find())
        {
            String attrs = m.group(1);
            String d = attr(attrs, "d");
            if (d == null)
            {
                continue;
            }
            Color fill = parseColor(attr(attrs, "fill"));
            double[] translate = parseTranslate(attr(attrs, "transform"));

            Graphics2D pathG = (Graphics2D) g.create();
            pathG.translate(translate[0], translate[1]);
            pathG.setColor(fill);
            pathG.fill(buildPath(d));
            pathG.dispose();
            drawn++;
        }
        g.dispose();

        ImageIO.write(image, "png", new File(pngFile));
        System.out.println("wrote " + pngFile + " (" + size + "x" + size + ", " + drawn + " paths)");
    }

    /** Supports the M / C / Z subset the icons are drawn with; anything else is an error. */
    private static Path2D buildPath(String d)
    {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        List<Double> nums = new ArrayList<>();
        char command = 0;
        int i = 0;
        while (i < d.length())
        {
            char c = d.charAt(i);
            if (Character.isLetter(c))
            {
                flush(path, command, nums);
                command = c;
                i++;
            }
            else if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t')
            {
                i++;
            }
            else
            {
                Matcher m = NUMBER.matcher(d);
                if (!m.find(i) || m.start() != i)
                {
                    throw new IllegalArgumentException("Unparsable path data at index " + i);
                }
                nums.add(Double.parseDouble(m.group()));
                i = m.end();
            }
        }
        flush(path, command, nums);
        return path;
    }

    private static void flush(Path2D.Double path, char command, List<Double> nums)
    {
        if (command == 0)
        {
            nums.clear();
            return;
        }
        switch (command)
        {
            case 'M':
                for (int i = 0; i + 1 < nums.size(); i += 2)
                {
                    if (i == 0)
                    {
                        path.moveTo(nums.get(i), nums.get(i + 1));
                    }
                    else
                    {
                        path.lineTo(nums.get(i), nums.get(i + 1));
                    }
                }
                break;
            case 'C':
                for (int i = 0; i + 5 < nums.size(); i += 6)
                {
                    path.curveTo(nums.get(i), nums.get(i + 1), nums.get(i + 2),
                            nums.get(i + 3), nums.get(i + 4), nums.get(i + 5));
                }
                break;
            case 'Z':
            case 'z':
                path.closePath();
                break;
            default:
                throw new IllegalArgumentException("Unsupported path command: " + command);
        }
        nums.clear();
    }

    private static double[] parseTranslate(String transform)
    {
        if (transform == null)
        {
            return new double[] { 0, 0 };
        }
        Matcher m = Pattern.compile("translate\\(([^)]*)\\)").matcher(transform);
        if (!m.find())
        {
            return new double[] { 0, 0 };
        }
        String[] parts = m.group(1).split("[,\\s]+");
        double x = Double.parseDouble(parts[0].trim());
        double y = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 0;
        return new double[] { x, y };
    }

    private static Color parseColor(String fill)
    {
        if (fill == null || fill.isEmpty() || "none".equalsIgnoreCase(fill))
        {
            return Color.BLACK;
        }
        if (fill.startsWith("#"))
        {
            String hex = fill.substring(1);
            if (hex.length() == 3)
            {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1)
                        + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            return new Color(Integer.parseInt(hex, 16));
        }
        return Color.BLACK;
    }

    private static String attr(String attrs, String name)
    {
        Matcher m = ATTR.matcher(attrs);
        while (m.find())
        {
            if (m.group(1).equals(name))
            {
                return m.group(2);
            }
        }
        return null;
    }

    private static double attrValue(String tag, String name, double fallback)
    {
        String value = attr(tag, name);
        return value == null ? fallback : Double.parseDouble(value);
    }
}
