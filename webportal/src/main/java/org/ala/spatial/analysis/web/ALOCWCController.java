/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ala.spatial.analysis.web;

import au.org.emii.portal.composer.MapComposer;

import au.org.emii.portal.composer.UtilityComposer;
import au.org.emii.portal.settings.SettingsSupplementary;
import org.ala.spatial.util.LayersUtil;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;


import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.zkoss.zhtml.Messagebox;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Button;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.ListitemRenderer;
import org.zkoss.zul.SimpleListModel;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Image;
import org.zkoss.zul.Window;

/**
 *
 * @author ajay
 */
public class ALOCWCController extends UtilityComposer {

    private static final String GEOSERVER_URL = "geoserver_url";
    private static final String GEOSERVER_USERNAME = "geoserver_username";
    private static final String GEOSERVER_PASSWORD = "geoserver_password";
    private static final String SAT_URL = "sat_url";
    private Listbox lbenvlayers;
    private Combobox cbEnvLayers;
    private Label lblNoLayersSelected;
    private Listbox lbSelLayers;
    private Button btnDeleteSelected;
    private Textbox groupCount;
    private Button btnGenerate;
    private List<String> selectedLayers;
    private MapComposer mc;
    private String geoServer = "http://ec2-175-41-187-11.ap-southeast-1.compute.amazonaws.com";  // http://localhost:8080
    private String satServer = geoServer;
    private SettingsSupplementary settingsSupplementary = null;
    private Image legend;
    String user_polygon = "";
    Textbox selectionGeomALOC;
    int generation_count = 1;
    String pid;
    
    String layerLabel;
    
    LayersUtil layersUtil;

    @Override
    public void afterCompose() {
        super.afterCompose();

        mc = getThisMapComposer();
        if (settingsSupplementary != null) {
            geoServer = settingsSupplementary.getValue(GEOSERVER_URL);
            satServer = settingsSupplementary.getValue(SAT_URL);
        }
        
        layersUtil = new LayersUtil(mc,satServer);

        setupEnvironmentalLayers();

        selectedLayers = new Vector<String>();
    }

    /**
     * Iterate thru' the layer list setup in the @doAfterCompose method
     * and setup the listbox
     */
    private void setupEnvironmentalLayers() {
        try {
            String[] aslist = layersUtil.getEnvironmentalLayers();

            if (aslist.length > 0) {

                lbenvlayers.setItemRenderer(new ListitemRenderer() {

                    public void render(Listitem li, Object data) {
                        li.setWidth(null);
                        new Listcell((String) data).setParent(li);
                    }
                });

                lbenvlayers.setModel(new SimpleListModel(aslist));
            }


        } catch (Exception e) {
            System.out.println("error setting up env list");
            e.printStackTrace(System.out);
        }
    }

    public void onChange$cbEnvLayers(Event event) {
        String new_value = "";

        new_value = cbEnvLayers.getValue();
        if (new_value.equals("")) {
            return;
        }
        new_value = (new_value.equals("")) ? "" : new_value.substring(0, new_value.indexOf("(")).trim();
        System.out.println("new value: " + new_value);

        if (selectedLayers.contains(new_value)) {
            System.out.println("is not new");
            return;
        } else {
            System.out.println("is new");
            selectedLayers.add(new_value);
        }

        //lbSelLayers.appendItem(new_value, new_value);

        lbSelLayers.setModel(new SimpleListModel(selectedLayers));

        toggleVisibleComponents();

    }

    public void onClick$btnDeleteSelected(Event event) {
        Iterator it = lbSelLayers.getSelectedItems().iterator();
        while (it.hasNext()) {
            Listitem li = (Listitem) it.next();
            selectedLayers.remove(li.getLabel());
        }

        lbSelLayers.setModel(new SimpleListModel(selectedLayers));

        toggleVisibleComponents();

    }

    private void toggleVisibleComponents() {
        if (selectedLayers.size() == 0) {
            lbSelLayers.setVisible(false);
            lblNoLayersSelected.setVisible(true);
        } else {
            lbSelLayers.setVisible(true);
            lblNoLayersSelected.setVisible(false);
        }

    }

    public void onDoInit(Event event) throws Exception {
        runclassification();
        Clients.showBusy("", false);
    }

    public void onClick$btnGenerate(Event event) {
        Clients.showBusy("Classification running...", true);
        Events.echoEvent("onDoInit", this, event.toString());
    }

    public void runclassification() {
        try {
            StringBuffer sbenvsel = new StringBuffer();

            /*
            if (selectedLayers.size() > 0) {
            Iterator it = selectedLayers.iterator();
            while (it.hasNext()) {
            sbenvsel.append(it.next());
            if (it.hasNext()) {
            sbenvsel.append(":");
            }
            }
            } else {
            Messagebox.show("Please select some environmental layers","ALA Spatial Toolkit", Messagebox.OK, Messagebox.EXCLAMATION);
            return;
            }
             *
             */
            if (lbenvlayers.getSelectedCount() > 0) {
                Iterator it = lbenvlayers.getSelectedItems().iterator();
                int i = 0;
                while (it.hasNext()) {
                    Listitem li = (Listitem) it.next();

                    sbenvsel.append(li.getLabel());
                    if (it.hasNext()) {
                        sbenvsel.append(":");
                    }

                }
            } else {
                Messagebox.show("Please select some environmental layers", "ALA Spatial Toolkit", Messagebox.OK, Messagebox.EXCLAMATION);
                return;
            }

            StringBuffer sbProcessUrl = new StringBuffer();
            sbProcessUrl.append(satServer + "/alaspatial/ws/aloc/processgeo?");
            sbProcessUrl.append("gc=" + URLEncoder.encode(groupCount.getValue(), "UTF-8"));
            sbProcessUrl.append("&envlist=" + URLEncoder.encode(sbenvsel.toString(), "UTF-8"));
            if (user_polygon.length() > 0) {
                sbProcessUrl.append("&points=" + URLEncoder.encode(user_polygon, "UTF-8"));
            } else {
                sbProcessUrl.append("&points=" + URLEncoder.encode("none", "UTF-8"));
            }

            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(sbProcessUrl.toString()); // testurl
            //get.addRequestHeader("Accept", "application/json, text/javascript, */*");
            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            String slist = get.getResponseBodyAsString();

            System.out.println("Got response from ALOCWSController: \n" + slist);

            String mapurl = geoServer + "/geoserver/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:aloc_class_" + slist + "&styles=&srs=EPSG:4326&TRANSPARENT=true&FORMAT=image%2Fpng";

            String legendurl = geoServer
                    + "/geoserver/wms?REQUEST=GetLegendGraphic&VERSION=1.0.0&FORMAT=image/png&WIDTH=10&HEIGHT="
                    + (Integer.parseInt((groupCount.getValue())))
                    + "&LAYER=ALA:aloc_class_" + slist
                    + "&STYLE=aloc_" + slist;

            //get the current MapComposer instance
            //MapComposer mc = getThisMapComposer();

            //mc.addWMSLayer("ALOC " + slist, mapurl, (float) 0.5);
            //String label = "ALOC (groups=" + groupCount.getValue() + ") classification#" + generation_count;
            layerLabel = "Classification #" + generation_count + " - " + groupCount.getValue() + " groups";
           /* mc.addWMSLayer(label, mapurl, (float) 0.5, "", legendurl);*/

            generation_count++;

            pid = slist;
            
            loadMap();
            
            java.util.Map args = new java.util.HashMap();                                                     
        	args.put("pid",pid);
        	args.put("layer",layerLabel);
    		Window win = (Window) Executions.createComponents(
                    "/WEB-INF/zul/AnalysisClassificationLegend.zul", null, args);
        	win.doModal();
        	
        } catch (Exception ex) {
            //Logger.getLogger(ALOCWCController.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Opps!: ");
            ex.printStackTrace(System.out);
        }

    }

    /**
     * Gets the main pages controller so we can add a
     * layer to the map
     * @return MapComposer = map controller class
     */
    private MapComposer getThisMapComposer() {

        MapComposer mapComposer = null;
        //Page page = maxentWindow.getPage();
        Page page = getPage();
        mapComposer = (MapComposer) page.getFellow("mapPortalPage");

        return mapComposer;
    }

    /**
     * Activate the polygon selection tool on the map
     * @param event
     */
    public void onClick$btnPolygonSelection(Event event) {
        //MapComposer mc = getThisMapComposer();

        mc.getOpenLayersJavascript().addPolygonDrawingToolALOC();
    }

    /**
     * clear the polygon selection tool on the map
     * @param event
     */
    public void onClick$btnPolygonSelectionClear(Event event) {
        user_polygon = "";
        selectionGeomALOC.setValue("");

        //MapComposer mc = getThisMapComposer();

        mc.getOpenLayersJavascript().removePolygonALOC();
    }

    /**
     * 
     * @param event
     */
    public void onChange$selectionGeomALOC(Event event) {
        try {

            user_polygon = convertGeoToPoints(selectionGeomALOC.getValue());

        } catch (Exception e) {//FIXME
            e.printStackTrace();
        }
    }

    String convertGeoToPoints(String geometry) {
        if (geometry == null) {
            return "";
        }
        geometry = geometry.replace(" ", ":");
        geometry = geometry.replace("POLYGON((", "");
        geometry = geometry.replace(")", "");
        return geometry;
    }
    
    /**
     * populate sampling screen with values from active layers
     * 
     * TODO: run this on 'tab' open
     */
    public void callPullFromActiveLayers(){
    	//get list of env/ctx layers
    	String [] layers = layersUtil.getActiveEnvCtxLayers();    	
    	
      	/* set as selected each envctx layer found */
    	if (layers != null) {
    		List<Listitem> lis = lbenvlayers.getItems();
	    	for (int i = 0; i < lis.size(); i++) {
	    		for (int j = 0; j < layers.length; j++) {
	    			if(lis.get(i).getLabel().equalsIgnoreCase(layers[j])) {
	    				lbenvlayers.addItemToSelection(lis.get(i));
	    				break;
	    			}
	    		}
	    	}    	    	
    	}
    }
    
    private void loadMap() {             
        String uri = satServer + "/alaspatial/output/layers/" + pid + "/img.png";
        float opacity = Float.parseFloat("0.75");

        List<Double> bbox = new ArrayList<Double>();
        bbox.add(112.0);
        bbox.add(-44.0000000007);
        bbox.add(154.00000000084);
        bbox.add(-9.0);

        mc.addImageLayer(pid, layerLabel, uri, opacity, bbox);
    }
}
