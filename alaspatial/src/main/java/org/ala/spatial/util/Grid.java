package org.ala.spatial.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 * Grid.java
 * Created on June 24, 2005, 4:12 PM
 * 
 * @author Robert Hijmans, rhijmans@berkeley.edu
 * 
 * Updated 15/2/2010, Adam
 * 
 * Interface for .gri/.grd files for now
 */
public class Grid { //  implements Serializable    
    static ArrayList<Grid> all_grids = new ArrayList<Grid>();

    final double noDataValueDefault = -3.4E38;

    public Boolean byteorderLSB; // true if file is LSB (Intel)
    public int ncols, nrows;
    public double nodatavalue;
    public Boolean valid;
    public double[] values;
    public double xmin, xmax, ymin, ymax;
    public double xres, yres;
    public String datatype;
    // properties
    public double minval, maxval;
    byte nbytes;
    String filename;
    float [] grid_data = null;
    

    /**
     * loads grd for gri file reference
     * @param fname full path and file name without file extension
     * of .gri and .grd files to open
     */
    public Grid(String fname) { // construct Grid from file
        filename = fname;
        File grifile = new File(filename + ".gri");
        if(!grifile.exists()) grifile = new File(filename + ".GRI");
        File grdfile = new File(filename + ".grd");
        if(!grdfile.exists()) grdfile = new File(filename + ".GRD");
        if (grdfile.exists() && grifile.exists()) {
            readgrd(filename);

            //update xres/yres when xres == 1
            if(xres == 1){
                xres = (xmax - xmin) / nrows;
                yres = (ymax - ymin) / ncols;
            }
        } else {
            //log error
            System.out.println("cannot find GRID: " + fname);

        }
    }

    Grid(String fname, boolean keepAvailable) { // construct Grid from file
        filename = fname;
        File grifile = new File(filename + ".gri");
        if(!grifile.exists()) grifile = new File(filename + ".GRI");
        File grdfile = new File(filename + ".grd");
        if(!grdfile.exists()) grdfile = new File(filename + ".GRD");
        if (grdfile.exists() && grifile.exists()) {
            readgrd(filename);

            //update xres/yres when xres == 1
            if(xres == 1){
                xres = (xmax - xmin) / nrows;
                yres = (ymax - ymin) / ncols;
            }
        } else {
            //log error
        }

        if(keepAvailable){
            Grid.addGrid(this);
        }
    }

    static void removeAvailable(){
        synchronized(all_grids){
            while(all_grids.size() > 0){
                all_grids.remove(0);
            }
        }
    }

    static void addGrid(Grid g){
        synchronized(all_grids){
            if(all_grids.size() == TabulationSettings.max_grids_load){
                all_grids.remove(0);
            }
            all_grids.add(g);
        }
    }

    static public Grid getGrid(String filename) {
        synchronized(all_grids){
            for(int i=0;i<all_grids.size();i++){
                if(filename.equals(all_grids.get(i).filename)){
                    //get and add to the end of grid list
                    Grid g = all_grids.get(i);
                    all_grids.remove(i);
                    all_grids.add(g);
                    return g;
                }
            }
            return new Grid(filename, true);
        }        
    }

    public static Grid getGridStandardized(String filename) {
        synchronized(all_grids){
            for(int i=0;i<all_grids.size();i++){
                if(filename.equals(all_grids.get(i).filename)){
                    //get and add to the end of grid list
                    Grid g = all_grids.get(i);
                    all_grids.remove(i);
                    all_grids.add(g);
                    return g;
                }
            }
        
            Grid g = new Grid(filename, true);
            float [] d = g.getGrid();
            double range = g.maxval - g.minval;
            for(int i=0;i<d.length;i++){
                d[i] = (float)((d[i] - g.minval) / range);
            }
            return g;
        }
    }


//transform to file position
    public int getcellnumber(double x, double y) {
        if (x < xmin || x > xmax || y < ymin || y > ymax) //handle invalid inputs
        {
            return -1;
        }

        int col = (int) ((x - xmin) / xres);
        int row = this.nrows - 1 - (int) ((y - ymin) / yres);

        //limit each to 0 and ncols-1/nrows-1
        if (col < 0) {
            col = 0;
        }
        if (row < 0) {
            row = 0;
        }
        if (col >= ncols) {
            col = ncols - 1;
        }
        if (row >= nrows) {
            row = nrows - 1;
        }
        return (row * ncols + col);
    }

    private void setdatatype(String s) {
        s = s.toUpperCase();

        // Expected from grd file
        if (s.equals("INT1BYTE")) {
            datatype = "BYTE";
        } else if (s.equals("INT2BYTES")) {
            datatype = "SHORT";
        } else if (s.equals("INT4BYTES")) {
            datatype = "INT";
        } else if (s.equals("INT8BYTES")) {
            datatype = "LONG";
        } else if (s.equals("FLT4BYTES")) {
            datatype = "FLOAT";
        } else if (s.equals("FLT8BYTES")) {
            datatype = "DOUBLE";
        } // shorthand for same
        else if (s.equals("INT1B") || s.equals("BYTE")) {
            datatype = "BYTE";
        } else if (s.equals("INT1U")) {
            datatype = "UBYTE";
        } else if (s.equals("INT2B") || s.equals("INT16") || s.equals("INT2S")) {
            datatype = "SHORT";
        } else if (s.equals("INT4B")) {
            datatype = "INT";
        } else if (s.equals("INT8B") || s.equals("INT32")) {
            datatype = "LONG";
        } else if (s.equals("FLT4B") || s.equals("FLOAT32")  || s.equals("FLT4S")) {
            datatype = "FLOAT";
        } else if (s.equals("FLT8B")) {
            datatype = "DOUBLE";
        } // if you rather use Java keywords...
        else if (s.equals("BYTE")) {
            datatype = "BYTE";
        } else if (s.equals("SHORT")) {
            datatype = "SHORT";
        } else if (s.equals("INT")) {
            datatype = "INT";
        } else if (s.equals("LONG")) {
            datatype = "LONG";
        } else if (s.equals("FLOAT")) {
            datatype = "FLOAT";
        } else if (s.equals("DOUBLE")) {
            datatype = "DOUBLE";
        } // some backwards compatibility
        else if (s.equals("INTEGER")) {
            datatype = "INT";
        } else if (s.equals("SMALLINT")) {
            datatype = "INT";
        } else if (s.equals("SINGLE")) {
            datatype = "FLOAT";
        } else if (s.equals("REAL")) {
            datatype = "FLOAT";
        } else {
            System.out.println("GRID unknown type: " + s);
            datatype = "UNKNOWN";
        }

        if (datatype.equals("BYTE") || datatype.equals("UBYTE")) {
            nbytes = 1;
        } else if (datatype.equals("SHORT")) {
            nbytes = 2;
        } else if (datatype.equals("INT")) {
            nbytes = 4;
        } else if (datatype.equals("LONG")) {
            nbytes = 8;
        } else if (datatype.equals("SINGLE")) {
            nbytes = 4;
        } else if (datatype.equals("DOUBLE")) {
            nbytes = 8;
        } else {
            nbytes = 0;
        }
    }

    private void readgrd(String filename) {
        IniReader ir = null;
        if((new File(filename + ".grd")).exists()){
            ir = new IniReader(filename + ".grd");
        }else {
            ir = new IniReader(filename + ".GRD");
        }

        setdatatype(ir.getStringValue("Data", "DataType"));
        //System.out.println("grd datatype=" + datatype);
        maxval = ir.getDoubleValue("Data", "MaxValue");
        minval = ir.getDoubleValue("Data", "MinValue");
        ncols = ir.getIntegerValue("GeoReference", "Columns");
        nrows = ir.getIntegerValue("GeoReference", "Rows");
        xmin = ir.getDoubleValue("GeoReference", "MinX");
        ymin = ir.getDoubleValue("GeoReference", "MinY");
        xmax = ir.getDoubleValue("GeoReference", "MaxX");
        ymax = ir.getDoubleValue("GeoReference", "MaxY");
        xres = ir.getDoubleValue("GeoReference", "ResolutionX");
        yres = ir.getDoubleValue("GeoReference", "ResolutionY");
        if (ir.valueExists("Data", "NoDataValue")) {
            nodatavalue = ir.getDoubleValue("Data", "NoDataValue");
        } else {
            nodatavalue = Double.NaN;
        }

        String s = ir.getStringValue("Data", "ByteOrder");

        byteorderLSB = true;
        if (s != null && s.length() > 0) {
            if (s.equals("MSB")) {
                byteorderLSB = false;
            }// default is windows (LSB), not linux or Java (MSB)
        }

    }

    public float[] getGrid() {
        if (grid_data != null) {
            return grid_data;
        }
        int length = nrows * ncols;

        float[] ret = new float[length];

        int i, j;
        RandomAccessFile afile;
        File f2 = new File(filename + ".GRI");

        try { //read of random access file can throw an exception
            if(!f2.exists()){
                afile = new RandomAccessFile(filename + ".gri", "r");
            } else {
                afile = new RandomAccessFile(filename + ".GRI", "r");
            }
            
            byte [] b = new byte[(int)afile.length()];
            afile.read(b);
            ByteBuffer bb = ByteBuffer.wrap(b);
            afile.close();

            if(byteorderLSB) {
                bb.order(ByteOrder.LITTLE_ENDIAN);
            }

            if (datatype == "UBYTE") {
                for (i = 0; i < length; i++) {
                    ret[i] = bb.get();
                    if(ret[i] < 0)
                        ret[i] += 256;
                }
            } else if (datatype == "BYTE") {
                for (i = 0; i < length; i++) {
                    ret[i] = bb.get();
                }
            } else if (datatype == "SHORT") {
                for (i = 0; i < length; i++) {
                    ret[i] = bb.getShort();
                }
            } else if (datatype == "INT") {
                for (i = 0; i < length; i++) {
                    ret[i] = bb.getInt();
                }
            } else if (datatype == "LONG") {
                for (i = 0; i < length; i++) {
                    ret[i] = bb.getLong();
                }
            } else if (datatype == "FLOAT") {
                for (i = 0; i < length; i++) {
                    ret[i] = bb.getFloat();
                }
            } else if (datatype == "DOUBLE") {
                for (i = 0; i < length; i++) {
                    ret[i] = (float)bb.getDouble();
                }
            } else {
                // / should not happen; catch anyway...
                for (i = 0; i < length; i++) {
                    ret[i] = Float.NaN;
                }
            }
            //replace not a number
            for (i = 0; i < length; i++) {
                if ((float) ret[i] == (float) nodatavalue) {
                    ret[i] = Float.NaN;
                }
            }
        } catch (Exception e) {
            //log error - probably a file error
            System.out.println("GRID: " + e.toString());
            e.printStackTrace();
        }
        grid_data = ret;
        return ret;
    }
  
    /**
     * for grid cutter
     *
     * writes out a list of double (same as getGrid() returns) to a file
     *
     * byteorderlsb
     * data type, FLOAT
     *
     * @param newfilename
     * @param dfiltered
     */
    void writeGrid(String newfilename, double[] dfiltered, double xmin, double ymin, double xmax, double ymax, double xres, double yres, int nrows, int ncols) {
        int size, i, length = dfiltered.length, pos;
        double maxvalue = Double.MAX_VALUE*-1;
        double minvalue = Double.MAX_VALUE;
       
        //write data as whole file
        RandomAccessFile afile;        
        try { //read of random access file can throw an exception
            afile = new RandomAccessFile(newfilename + ".gri", "rw");
            
            size = 4;
            byte[] b = new byte[size * length];
            ByteBuffer bb = ByteBuffer.wrap(b);

            if (byteorderLSB) {
                bb.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                bb.order(ByteOrder.BIG_ENDIAN);
            }
            System.out.println("WRITING FLOAT: " + length + " records to " + newfilename);
            for (i = 0; i < length; i++) {
                if (Double.isNaN(dfiltered[i])) {
                    bb.putFloat((float) noDataValueDefault);
                } else {
                    if (minvalue > dfiltered[i]) minvalue = dfiltered[i];
                    if (maxvalue < dfiltered[i]) maxvalue = dfiltered[i];
                    bb.putFloat((float) dfiltered[i]);
                }
            }

            afile.write(b);

            afile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


        writeHeader(newfilename,xmin,ymin,xmax,ymax,xres,yres,nrows,ncols,minvalue,maxvalue);

    }

    void writeGrid(String newfilename, float[] dfiltered, double xmin, double ymin, double xmax, double ymax, double xres, double yres, int nrows, int ncols) {
        int size, i, length = dfiltered.length, pos;
        double maxvalue = Double.MAX_VALUE*-1;
        double minvalue = Double.MAX_VALUE;

        //write data as whole file
        RandomAccessFile afile;
        try { //read of random access file can throw an exception
            afile = new RandomAccessFile(newfilename + ".gri", "rw");

            size = 4;
            byte[] b = new byte[size * length];
            ByteBuffer bb = ByteBuffer.wrap(b);

            if (byteorderLSB) {
                bb.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                bb.order(ByteOrder.BIG_ENDIAN);
            }
            System.out.println("WRITING FLOAT: " + length + " records to " + newfilename);
            for (i = 0; i < length; i++) {
                if (Double.isNaN(dfiltered[i])) {
                    bb.putFloat((float) noDataValueDefault);
                } else {
                    if (minvalue > dfiltered[i]) minvalue = dfiltered[i];
                    if (maxvalue < dfiltered[i]) maxvalue = dfiltered[i];
                    bb.putFloat((float) dfiltered[i]);
                }
            }

            afile.write(b);

            afile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


        writeHeader(newfilename,xmin,ymin,xmax,ymax,xres,yres,nrows,ncols,minvalue,maxvalue);

    }

    void writeHeader(String newfilename, double xmin, double ymin, double xmax, double ymax, double xres, double yres, int nrows, int ncols, double minvalue, double maxvalue) {
        try{
            FileWriter fw = new FileWriter(newfilename + ".grd");

            fw.append("[General]");
            fw.append("\r\n").append("Title=").append(newfilename);
            fw.append("\r\n").append("[GeoReference]");
            fw.append("\r\n").append("Projection=GEOGRAPHIC");
            fw.append("\r\n").append("Datum=WGS84");
            fw.append("\r\n").append("Mapunits=DEGREES");
            fw.append("\r\n").append("Columns=").append(String.valueOf(ncols));
            fw.append("\r\n").append("Rows=").append(String.valueOf(nrows));
            fw.append("\r\n").append("MinX=").append(String.format("%.2f",xmin));
            fw.append("\r\n").append("MaxX=").append(String.format("%.2f",xmax));
            fw.append("\r\n").append("MinY=").append(String.format("%.2f",ymin));
            fw.append("\r\n").append("MaxY=").append(String.format("%.2f",ymax));
            fw.append("\r\n").append("ResolutionX=").append(String.valueOf(xres));
            fw.append("\r\n").append("ResolutionY=").append(String.valueOf(yres));
            fw.append("\r\n").append("[Data]");
            fw.append("\r\n").append("DataType=FLT4BYTES");
            fw.append("\r\n").append("MinValue=").append(String.valueOf(minvalue));
            fw.append("\r\n").append("MaxValue=").append(String.valueOf(maxvalue));
            fw.append("\r\n").append("NoDataValue=").append(String.valueOf(noDataValueDefault));
            fw.append("\r\n").append("Transparent=0");
            fw.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * do get values of grid for provided points.
     * 
     * loads whole grid file as double[] in process 
     * 
     * @param points
     * @return
     */
    public float[] getValues2(double[][] points) {
        if (points == null || points.length == 0) {
            System.out.println("exit getValues2:" + points);
            return null;
        }

        //init output structure
        float[] ret = new float[points.length];

        //load whole grid
        float[] grid = getGrid();

        int length = points.length;
        int i, pos;
      
        System.out.println(filename + ", " + datatype + ":" + points.length);

        //points loop
        for (i = 0; i < length; i++) {
            pos = getcellnumber(points[i][0], points[i][1]);
            if (pos >= 0) {
                ret[i] = grid[pos];
            } else {
                ret[i] = Float.NaN;
            }
        }

        return ret;
    }

    float[] getGrid(double xmin, double ymin, double xmax, double ymax) {
        //expects largest y at the top
        //expects input ranges inside of grid ranges

        int width = (int)((xmax - xmin)/xres);
        int height = (int) ((ymax - ymin)/yres);
        int startx = (int)((xmin - this.xmin)/xres);
        int endx = startx + width;
        int starty = (int)((ymin - this.ymin)/yres);
        int endy = starty + height;

        int length = width * height;

        float[] ret = new float[length];
        int pos = 0;

        int i, j;
        RandomAccessFile afile;
        File f2 = new File(filename + ".GRI");

        int size = 4;
        if (datatype.equals("BYTE") || datatype.equals("UBYTE")) {
            size = 1;
        } else if (datatype.equals("SHORT")) {
            size = 2;
        } else if (datatype.equals("INT")) {
            size = 4;
        } else if (datatype.equals("LONG")) {
            size = 8;
        } else if (datatype.equals("FLOAT")) {
            size = 4;
        } else if (datatype.equals("DOUBLE")) {
            size = 8;
        }

        try { //read of random access file can throw an exception
            if(!f2.exists()){
                afile = new RandomAccessFile(filename + ".gri", "r");
            } else {
                afile = new RandomAccessFile(filename + ".GRI", "r");
            }

            //seek to first raster
            afile.seek(this.ncols * starty * size);

            //read relevant rasters
            int readSize = this.ncols * height * size;
            int readLen = this.ncols * height;
            byte [] b = new byte[readSize];
            afile.read(b);
            ByteBuffer bb = ByteBuffer.wrap(b);
            afile.close();

            if(byteorderLSB) {
                bb.order(ByteOrder.LITTLE_ENDIAN);
            }

            if (datatype == "BYTE") {
                for (i = 0; i < readLen; i++) {
                    int x = i%this.ncols;
                    if(x < startx || x >= endx){
                        bb.get();
                    } else {
                        ret[pos++] = bb.get();
                    }
                }
            } else if (datatype == "UBYTE") {
                for (i = 0; i < readLen; i++) {
                    int x = i%this.ncols;
                    if(x < startx || x >= endx){
                        bb.get();
                    } else {
                        ret[pos] = bb.get();
                        if(ret[pos] < 0)
                            ret[pos] += 256;
                        pos++;
                    }
                }
            } else if (datatype == "SHORT") {
                for (i = 0; i < readLen; i++) {
                    int x = i%this.ncols;
                    if(x < startx || x >= endx){
                        bb.getShort();
                    } else {
                        ret[pos++] = bb.getShort();
                    }
                }
            } else if (datatype == "INT") {
                for (i = 0; i < readLen; i++) {
                    int x = i%this.ncols;
                    if(x < startx || x >= endx){
                        bb.getInt();
                    } else {
                        ret[pos++] = bb.getInt();
                    }
                }
            } else if (datatype == "LONG") {
                for (i = 0; i < readLen; i++) {
                    int x = i%this.ncols;
                    if(x < startx || x >= endx){
                        bb.getLong();
                    } else {
                        ret[pos++] = bb.getLong();
                    }
                }
            } else if (datatype == "FLOAT") {
                for (i = 0; i < readLen; i++) {
                    int x = i%this.ncols;
                    if(x < startx || x >= endx){
                        bb.getFloat();
                    } else {
                        ret[pos++] = bb.getFloat();
                    }
                }
            } else if (datatype == "DOUBLE") {
                for (i = 0; i < readLen; i++) {
                    int x = i%this.ncols;
                    if(x < startx || x >= endx){
                        bb.getDouble();
                    } else {
                        ret[pos++] = (float) bb.getDouble();
                    }
                }
            } else {
                // / should not happen; catch anyway...
                for (i = 0; i < length; i++) {
                    ret[i] = Float.NaN;
                }
            }
            //replace not a number
            for (i = 0; i < length; i++) {
                if ((float) ret[i] == (float) nodatavalue) {
                    ret[i] = Float.NaN;
                }
            }
        } catch (Exception e) {
            //log error - probably a file error
            System.out.println("GRID: " + e.toString());
            e.printStackTrace();
        }
        grid_data = ret;
        return ret;
    }
/*
    float[] getValues2(double xmin, double xmax, double ymin, double ymax) {
        int xrange = (int) Math.ceil((xmax - xmin) / TabulationSettings.grd_xdiv);
        int yrange = (int) Math.ceil((ymax - ymin) / TabulationSettings.grd_ydiv);

        //init output structure
        int len = xrange * yrange;
        float[] ret = new float[len];
        for(int i=0;i<len;i++){
            ret[i] = Float.NaN;
        }

        if(xmin < this.xmin){
            istart = 0;
            xoff = (int) Math.ceil((xmax - xmin) / TabulationSettings.grd_xdiv)
        }
        int x,y;
        for(int j=0;j<yrange;j++){
            y = ?;
            for(int i=0;i<xrange;i++){
                ret[y++] = grid[i];
            }
        }

        //load whole grid
        float[] grid = getGrid();

        int length = points.length;
        int i, pos;

        System.out.println(filename + ", " + datatype + ":" + points.length);

        //points loop
        for (i = 0; i < length; i++) {
            pos = getcellnumber(points[i][0], points[i][1]);
            if (pos >= 0) {
                ret[i] = grid[pos];
            } else {
                ret[i] = Float.NaN;
            }
        }

        return ret;
    }*/
}