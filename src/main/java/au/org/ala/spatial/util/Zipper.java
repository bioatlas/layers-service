package au.org.ala.spatial.util;

import org.apache.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author ajay
 */
public class Zipper {
    private static Logger logger = Logger.getLogger(Zipper.class);

    public static Map unzipFile(String name, InputStream data, String basepath) {
        try {
            Map output = new HashMap();
            String id = String.valueOf(System.currentTimeMillis());
            //String outputpath = "/data/ala/runtime/output/layers/" + id + "/";
            //String outputpath = "/Users/ajay/projects/tmp/useruploads/" + id + "/";
            String outputpath = basepath + id + "/";

            String zipfilename = name.substring(0, name.lastIndexOf("."));
            outputpath += zipfilename + "/";
            File outputDir = new File(outputpath);
            outputDir.mkdirs();

            ZipInputStream zis = new ZipInputStream(data);
            ZipEntry ze = null;
            String shpfile = "";
            String type = "";

            while ((ze = zis.getNextEntry()) != null) {
                String fname = outputpath + ze.getName();
                File destFile = new File(fname);
                if (destFile.isHidden()) {
                    continue;
                }
                destFile.getParentFile().mkdirs();
                logger.debug("ze.file: " + ze.getName());
                if (ze.getName().endsWith(".shp")) {
                    shpfile = ze.getName();
                    type = "shp";
                }
                if (!ze.isDirectory()) {
                    copyInputStream(zis, new BufferedOutputStream(new FileOutputStream(fname)));
                }
                zis.closeEntry();
            }
            zis.close();

//            if (type.equalsIgnoreCase("shp")) {
//                logger.debug("Uploaded file is a shapefile. Loading...");
//                //loadUserShapefile(new File(outputpath + shpfile));
//            } else {
//                logger.debug("Unknown file type. ");
//                //showMessage("Unknown file type. Please upload a valid CSV, KML or Shapefile. ");
//            }

            output.put("type", type);
            output.put("file", outputpath + shpfile);

            return output;

        } catch (Exception e) {
            //showMessage("Unable to load your file. Please try again.");
            logger.error("unable to load user kml: ", e);

        }

        return null;
    }

    private static void copyInputStream(InputStream in, OutputStream out) throws IOException, Exception {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) > -1) {
            out.write(buffer, 0, len);
        }

        // no need to close the input stream as it gets closed
        // in the caller function.
        // just close the output stream.
        out.close();

    }

    public static void zipDirectory(String dirpath, String outpath) {
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outpath));
            zipDir(dirpath, zos, dirpath);
            //close the stream
            zos.close();
        } catch (Exception e) {
            //handle exception
        }
    }

    private static void zipDir(String dir2zip, ZipOutputStream zos, String parentDir) {
        try {
            File zipDir = new File(dir2zip);
            //get a listing of the directory content
            String[] dirList = zipDir.list();
            byte[] readBuffer = new byte[2156];
            int bytesIn = 0;
            for (int i = 0; i < dirList.length; i++) {
                File f = new File(zipDir, dirList[i]);
                if (f.isDirectory()) {
                    String filePath = f.getPath();
                    zipDir(filePath, zos, parentDir);
                    continue;
                }
                FileInputStream fis = new FileInputStream(f);
                String fileToAdd = f.getName();
                ZipEntry anEntry = new ZipEntry(fileToAdd);
                logger.debug("adding: " + anEntry.getName());
                zos.putNextEntry(anEntry);
                while ((bytesIn = fis.read(readBuffer)) != -1) {
                    zos.write(readBuffer, 0, bytesIn);
                }
                //close the Stream
                fis.close();
            }
        } catch (Exception e) {
            //handle exception
        }
    }

    public static void zipFiles(String[] files, String outpath) {
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outpath));

            byte[] readBuffer = new byte[2156];
            int bytesIn = 0;
            for (int i = 0; i < files.length; i++) {
                File f = new File(files[i]);
                FileInputStream fis = new FileInputStream(f);
                String fileToAdd = f.getName();
                ZipEntry anEntry = new ZipEntry(fileToAdd);
                logger.debug("adding: " + anEntry.getName());
                zos.putNextEntry(anEntry);
                while ((bytesIn = fis.read(readBuffer)) != -1) {
                    zos.write(readBuffer, 0, bytesIn);
                }
                //close the Stream
                fis.close();
            }

            zos.close();
        } catch (Exception e) {
            logger.error("error zipping to: " + outpath, e);
        }
    }
}