package au.org.emii.portal.composer;

import au.org.emii.portal.databinding.ActiveLayerRenderer;
import au.org.emii.portal.request.DesktopState;
import au.org.emii.portal.wms.GenericServiceAndBaseLayerSupport;
import au.org.emii.portal.net.HttpConnection;
import au.org.emii.portal.motd.MOTD;
import au.org.emii.portal.menu.MapLayer;
import au.org.emii.portal.javascript.OpenLayersJavascript;
import au.org.emii.portal.session.PortalSession;
import au.org.emii.portal.wms.RemoteMap;
import au.org.emii.portal.session.StringMedia;
import au.org.emii.portal.settings.Settings;
import au.org.emii.portal.settings.SettingsSupplementary;
import au.org.emii.portal.lang.LanguagePack;
import au.org.emii.portal.menu.MapLayerMetadata;
import au.org.emii.portal.util.GeoJSONUtilities;
import au.org.emii.portal.util.LayerUtilities;
import au.org.emii.portal.util.LayerSelection;
import au.org.emii.portal.util.PortalSessionIO;
import au.org.emii.portal.util.PortalSessionUtilities;
import au.org.emii.portal.util.SessionPrint;
import au.org.emii.portal.value.BoundingBox;
import au.org.emii.portal.web.SessionInitImpl;
import au.org.emii.portal.wms.WMSStyle;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import org.geotools.kml.KML;
import org.geotools.kml.KMLConfiguration;
import java.awt.Color;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ala.spatial.analysis.web.SpeciesAutoComplete;
import org.ala.spatial.analysis.web.ContextualMenu;
import org.ala.spatial.analysis.web.HasMapLayer;
import org.ala.spatial.data.LegendObject;
import org.ala.spatial.data.Query;
import org.ala.spatial.data.QueryUtil;
import org.ala.spatial.util.CommonData;
import org.ala.spatial.util.LayersUtil;
import org.ala.spatial.util.ScatterplotData;
import org.ala.spatial.util.ShapefileUtils;
import org.ala.spatial.data.BiocacheQuery;
import org.ala.spatial.data.UploadQuery;
import org.ala.spatial.util.SelectedArea;
import org.ala.spatial.util.UserData;
import org.ala.spatial.util.Util;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.MDC;
import org.geotools.xml.Encoder;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.HtmlMacroComponent;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.SuspendNotAllowedException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.ForwardEvent;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zk.ui.util.SessionInit;
import org.zkoss.zul.Caption;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.Html;
import org.zkoss.zul.Iframe;
import org.zkoss.zul.West;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModelList;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Window;

/**
 * ZK composer for the index.zul page
 *
 *
 * @author geoff
 *
 */
public class MapComposer extends GenericAutowireAutoforwardComposer {

    protected DesktopState desktopState = new DesktopState();
    private SettingsSupplementary settingsSupplementary = null;
    private static final String MENU_DEFAULT_WIDTH = "menu_default_width";
    private static final String MENU_MINIMISED_WIDTH = "menu_minimised_width";
    public static final String POINTS_CLUSTER_THRESHOLD = "points_cluster_threshold";
    private static final long serialVersionUID = 1L;
    private RemoteMap remoteMap = null;

    /*
     * Autowired controls
     */
    private Iframe rawMessageIframeHack;
    private Div rawMessageHackHolder;
    Listbox activeLayersList;
    private Div layerControls;
    private West menus;
    private Div westContent;
    private Div westMinimised;
    /*
     * User data object to allow for the saving of maps and searches
     */
    private LanguagePack languagePack = null;
    private MOTD motd = null;
    private OpenLayersJavascript openLayersJavascript = null;
    private HttpConnection httpConnection = null;
    ActiveLayerRenderer activeLayerRenderer = null;
    private PortalSessionUtilities portalSessionUtilities = null;
    private Settings settings = null;
    private GenericServiceAndBaseLayerSupport genericServiceAndBaseLayerSupport = null;
    HtmlMacroComponent contextualMenu;
    public String tbxPrintHack;
    int mapZoomLevel = 4;
    Hashtable activeLayerMapProperties;
    Label lblSelectedLayer;
    String useSpeciesWMSCache = "on";
    ArrayList<LayerSelection> selectedLayers = new ArrayList<LayerSelection>();

    /*
     * for capturing layer loaded events signaling listeners
     */
    String tbxLayerLoaded;
    HashMap<String, EventListener> layerLoadedChangeEvents = new HashMap<String, EventListener>();

    void motd() {
        if (motd.isMotdEnabled()) {
            logger.debug("displayling MOTD");
            MOTDComposer composer = (MOTDComposer) Executions.createComponents("/WEB-INF/zul/MOTD.zul", this, null);
            composer.doOverlapped();
        }
    }

    public void onClick$removeAllLayers() {
        if (safeToPerformMapAction()) {
            List<MapLayer> activeLayers = getPortalSession().getActiveLayers();
            StringBuffer script = new StringBuffer();
            while (activeLayers.size() > 0) {
                MapLayer mapLayer = activeLayers.get(0);
                script.append(openLayersJavascript.removeMapLayer(mapLayer));

                // skip executing JS and reseting the layer controls - do
                // them at the end
                deactiveLayer(mapLayer, false, false);
            }
            updateLayerControls();
            openLayersJavascript.execute(
                    openLayersJavascript.iFrameReferences
                    + script.toString());
        }
    }

    public void safeToLoadMap(Event event) {
        mapLoaded("true");

        //listen for map extents changes
        EventListener el = new EventListener() {

            public void onEvent(Event event) throws Exception {
                onReloadLayers(null);
            }
        };
        getLeftmenuSearchComposer().addViewportEventListener("onReloadLayers", el);
    }

    public void zoomToExtent(MapLayer selectedLayer) {

        if (selectedLayer != null && selectedLayer.isDisplayed()) {
            logger.debug("zooming to extent " + selectedLayer.getId());
            if (selectedLayer.getType() == LayerUtilities.GEOJSON
                    || selectedLayer.getType() == LayerUtilities.WKT
                    || selectedLayer.getType() == LayerUtilities.KML) {
                openLayersJavascript.zoomGeoJsonExtentNow(selectedLayer);
            } else {
                openLayersJavascript.zoomLayerExtent(selectedLayer);
            }
        }
    }

    public void applyChange(MapLayer selectedLayer) {
        if (selectedLayer != null && selectedLayer.isDisplayed()) {
            /* different path for each type layer
             * 1. symbol
             * 2. classification legend
             * 3. prediction legend
             * 4. other (wms)
             */
            if (selectedLayer.isDynamicStyle()) {

                Color c = new Color(selectedLayer.getRedVal(), selectedLayer.getGreenVal(), selectedLayer.getBlueVal());
                String hexColour = Integer.toHexString(c.getRGB() & 0x00ffffff);
                String rgbColour = "rgb(" + String.valueOf(selectedLayer.getRedVal()) + "," + selectedLayer.getGreenVal() + "," + selectedLayer.getBlueVal() + ")";
                selectedLayer.setEnvColour(rgbColour);

                if (selectedLayer.getType() == LayerUtilities.GEOJSON) {
                    openLayersJavascript.redrawFeatures(selectedLayer);
                } else if (selectedLayer.getType() == LayerUtilities.WKT) {
                    openLayersJavascript.redrawWKTFeatures(selectedLayer);
                } else {
                    String envString = "";
                    if (selectedLayer.getColourMode().equals("-1")) {
                        envString += "color:" + hexColour;
                    } else {
                        LegendObject lo = (LegendObject) selectedLayer.getData("legendobject");
                        if (lo != null && lo.getColourMode() != null) {
                            envString += "colormode:" + lo.getColourMode();
                        } else {
                            envString += "colormode:" + selectedLayer.getColourMode();
                        }
                    }
                    envString += ";name:circle;size:" + selectedLayer.getSizeVal();

                    //Opacity now handled only by openlayers
//                    envString += ";opacity:" + selectedLayer.getOpacity();

                    if (selectedLayer.getHighlight() != null && selectedLayer.getHighlight().length() > 0
                            && !selectedLayer.getColourMode().equals("grid")) {
                        //Highlight now handled in a second layer
                    } else if (selectedLayer.getSizeUncertain()) {
                        envString += ";uncertainty:1";
                    }
                    selectedLayer.setEnvParams(envString + ";opacity:1");

                    if (selectedLayer.hasChildren()) {
                        MapLayer highlightLayer = selectedLayer.getChild(0);
                        if (highlightLayer.getName().equals(selectedLayer.getName() + "_highlight")) {
                            //apply sel to envString
                            String highlightEnv = "color:000000;size:" + selectedLayer.getSizeVal() + ";opacity:0";
                            highlightLayer.setOpacity(1);
                            if (selectedLayer.getHighlight() != null && selectedLayer.getHighlight().length() > 0
                                    && !selectedLayer.getColourMode().equals("grid")) {
                                highlightLayer.setEnvParams(highlightEnv + ";sel:" + selectedLayer.getHighlight().replace(";", "%3B"));
                                highlightLayer.setData("highlight", "show");
                            } else {
                                highlightLayer.setData("highlight", "hide");
                                highlightLayer.setEnvParams(highlightEnv);
                            }
                        }
                    }

                    reloadMapLayerNowAndIndexes(selectedLayer);
                }
            } else if (selectedLayer.getSelectedStyle() != null) {
                /* 1. classification legend has uri with ".zul" content
                 * 2. prediction legend works here                 *
                 */
                selectedLayer.setOpacity(selectedLayer.getOpacity());
                String legendUri = selectedLayer.getSelectedStyle().getLegendUri();
                if (legendUri.indexOf(".zul") >= 0) {
                    addImageLayer(selectedLayer.getId(),
                            selectedLayer.getName(),
                            selectedLayer.getUri(),
                            selectedLayer.getOpacity(),
                            null, LayerUtilities.ALOC);  //bbox is null, not required for redraw
                } else {
                    //redraw
                    reloadMapLayerNowAndIndexes(selectedLayer);
                }
            } else {// if (selectedLayer.getCurrentLegendUri() != null) {
                //redraw wms layer if opacity changed
                reloadMapLayerNowAndIndexes(selectedLayer);
            }
        }
    }

    void reloadMapLayerNowAndIndexes(MapLayer selectedLayer) {
        if (safeToPerformMapAction()) {
            PortalSession portalSession = (PortalSession) Executions.getCurrent().getDesktop().getSession().getAttribute("portalSession");

            openLayersJavascript.execute(
                    openLayersJavascript.iFrameReferences
                    + openLayersJavascript.reloadMapLayer(selectedLayer)
                    + openLayersJavascript.updateMapLayerIndexes(
                    portalSession.getActiveLayers()));
        }
    }

    public void mapSpeciesFromAutocomplete(SpeciesAutoComplete sac, SelectedArea sa) {
        // check if the species name is not valid
        // this might happen as we are automatically mapping
        // species without the user pressing a button
        if (sac.getSelectedItem() == null
                || sac.getSelectedItem().getAnnotatedProperties() == null
                || sac.getSelectedItem().getAnnotatedProperties().size() == 0) {
            return;
        }

        String taxon = sac.getValue();
        String rank = "";

        String spVal = sac.getSelectedItem().getDescription();
        if (spVal.trim().contains(": ")) {
            taxon = spVal.trim().substring(spVal.trim().indexOf(":") + 1, spVal.trim().indexOf("-")).trim() + " (" + taxon + ")";
            rank = spVal.trim().substring(0, spVal.trim().indexOf(":"));
        } else {
            rank = StringUtils.substringBefore(spVal, " ").toLowerCase();
            System.out.println("mapping rank and species: " + rank + " - " + taxon);
        }
        if (rank.equalsIgnoreCase("scientific name") || rank.equalsIgnoreCase("scientific")) {
            rank = "taxon";
        }

        String lsid = (String) (sac.getSelectedItem().getAnnotatedProperties().get(0));

        Query query = QueryUtil.get(lsid, this, false);
        Query q = QueryUtil.queryFromSelectedArea(query, sa, false);

        mapSpecies(q, taxon, rank, 0, LayerUtilities.SPECIES, sa.getWkt(), -1);

        System.out.println(">>>>> " + taxon + ", " + rank + " <<<<<");
    }

    /**
     * Reorder the active layers list based on a d'n'd event
     * @param dragged
     * @param dropped
     */
    public void reorderList(Listitem dragged, Listitem dropped) {
        logger.debug(dragged.getLabel() + " dropped on " + dropped.getLabel());

        // get the position in the list
        List<MapLayer> activeLayers = getPortalSession().getActiveLayers();
        int droppedIndex = activeLayers.indexOf(dropped.getValue());

        ListModelList model = (ListModelList) activeLayersList.getModel();
        model.remove(dragged.getValue());
        model.add(droppedIndex, dragged.getValue());

        // tell openLayers to change zIndexs
        openLayersJavascript.updateMapLayerIndexesNow(activeLayers);

        redrawLayersList();

        // hide legend controls
        //hideLayerControls(null);
        refreshContextualMenu();
    }

    /**
     * Remove a maplayer from the active layers list and then
     * reinsert it at the same spot - should cause this part
     * of the list to be re-rendered.
     *
     * After re-rendering, reselect the corresponding item in
     * its listbox, as operations such as changing opacity
     * and animation require a layer is selected
     * @param mapLayer
     */
    public void refreshActiveLayer(MapLayer mapLayer) {
        ListModelList model = (ListModelList) activeLayersList.getModel();
        int index = model.indexOf(mapLayer);
        if (index >= 0) {
            model.remove(index);
            model.add(index, mapLayer);

            activeLayersList.setSelectedIndex(index);
        }
    }

    public void activateLink(String uri, String label, boolean isExternal) {
        activateLink(uri, label, isExternal, "");
    }

    public void activateLink(String uri, String label, boolean isExternal, String downloadPid) {
        //close any prevously opened externalcontentwindow
        try {
            Component c = getFellowIfAny("externalContentWindow");
            if (c != null) {
                System.out.println("found externalContentWindow, closing");
                c.detach();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        Window externalContentWindow = (Window) Executions.createComponents("WEB-INF/zul/ExternalContent.zul", layerControls, null);

        if (isExternal) {
            // change browsers current location
            Clients.evalJavaScript("window.location.href ='" + uri + "';");
        } else {

            Iframe iframe = (Iframe) externalContentWindow.getFellow("externalContentIframe");
            Html html = (Html) externalContentWindow.getFellow("externalContentHTML");

            if (uri.charAt(0) == '*') {
                //html content
                uri = uri.substring(1);

                //url
                iframe.setHeight("0px");
                iframe.setSrc("");

                String content = "";
                if (label.equalsIgnoreCase("download")) {
                    StringBuilder sbContent = new StringBuilder();
                    sbContent.append("<p id='termsOfUseDownload'>");
                    sbContent.append("By downloading this content you are agreeing to use it in accordance with the Atlas");
                    sbContent.append("<a href='http://www.ala.org.au/about/terms-of-use/#TOUusingcontent'>Terms of Use</a>");
                    sbContent.append("and individual <a href=' http://www.ala.org.au/support/faq/#q29'>Data Provider Terms</a>.");
                    sbContent.append("<br/><br/>");
                    sbContent.append("Please provide the following <b>optional</b> details before downloading:");
                    sbContent.append("</p>");
                    sbContent.append("    <form id='downloadForm' onSubmit='downloadSubmitButtonClick(); return false;'>");
                    sbContent.append("        <input type='hidden' name='url' id='downloadUrl' value='" + uri + "'/>");
                    //sbContent.append("        <input type='hidden' name='url' id='downloadChecklistUrl' value='http://biocache.ala.org.au/ws/occurrences/facets/download?q=text:macropus rufus'/>");
                    //sbContent.append("        <input type='hidden' name='url' id='downloadFieldGuideUrl' value='/occurrences/fieldguide/download?q=text:macropus rufus'/>");
                    sbContent.append("        <fieldset>");
                    sbContent.append("            <p><label for='email'>Email</label>");
                    sbContent.append("                <input type='text' name='email' id='email' value='' size='30'  />");
                    sbContent.append("            </p>");
                    sbContent.append("            <p><label for='filename'>File Name</label>");
                    sbContent.append("                <input type='text' name='filename' id='filename' value='data' size='30'  />");
                    sbContent.append("            </p>");
                    sbContent.append("            <p><label for='reason' style='vertical-align: top'>Download Reason</label>");
                    sbContent.append("                <textarea name='reason' rows='5' cols='30' id='reason'  ></textarea>");
                    sbContent.append("            </p>");
                    sbContent.append("            <input type='submit' value='Download All Records' id='downloadSubmitButton'/>&nbsp;");
                    //sbContent.append("            <input type='submit' value='Download All Records' id='downloadSubmitButton'/>&nbsp;");
                    //sbContent.append("            <input type='submit' value='Download Species Checklist' id='downloadCheckListSubmitButton'/>&nbsp;");
                    //sbContent.append("            <input type='submit' value='Download Species Field Guide' id='downloadFieldGuideSubmitButton'/>&nbsp;");
                    //sbContent.append("            <input type='reset' value='Cancel' onClick='$.fancybox.close();'/>");
                    sbContent.append("        </fieldset>");
                    sbContent.append("    </form>");

                    content = sbContent.toString();
                } else {
                    content = uri;
                }


                //content
                html.setContent(content);
                html.setStyle("overflow: scroll");

                //for the 'reset window' button
                ((ExternalContentComposer) externalContentWindow).src = "";

                //update linked button
                ((Toolbarbutton) externalContentWindow.getFellow("breakout")).setVisible(false);
            } else {
                //url
                iframe.setHeight("100%");
                iframe.setSrc(uri);

                //content
                html.setContent("");

                //for the 'reset window' button
                ((ExternalContentComposer) externalContentWindow).src = uri;

                //update linked button
                ((Toolbarbutton) externalContentWindow.getFellow("breakout")).setHref(uri);
                ((Toolbarbutton) externalContentWindow.getFellow("breakout")).setVisible(true);
            }

            if (StringUtils.isNotBlank(downloadPid)) {
                ((Toolbarbutton) externalContentWindow.getFellow("download")).setHref(CommonData.satServer + "/ws/download/" + downloadPid);
                ((Toolbarbutton) externalContentWindow.getFellow("download")).setVisible(true);
            } else {
                ((Toolbarbutton) externalContentWindow.getFellow("download")).setHref("");
                ((Toolbarbutton) externalContentWindow.getFellow("download")).setVisible(false);
            }

            // use the link description as the popup caption
            ((Caption) externalContentWindow.getFellow("caption")).setLabel(
                    label);
            externalContentWindow.setPosition("center");
            //externalContentWindow.setHeight("60%");
            //externalContentWindow.setWidth("50%");
            //externalContentWindow.doOverlapped();
            try {
                externalContentWindow.doModal();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean activateLayer(MapLayer mapLayer, boolean doJavaScript) {
        return activateLayer(mapLayer, doJavaScript, false);
    }

    /**
     * Activate a map layer on the map
     * @param layer MapLayer instance to activate
     * @param doJavaScript set false to defer execution of JavaScript
     * which actually adds the layer to the openlayers menu
     *
     * @return true if the layer was added successfully, otherwise false
     */
    public boolean activateLayer(MapLayer mapLayer, boolean doJavaScript, boolean skipTree) {
        List<MapLayer> activeLayers = getPortalSession().getActiveLayers();
        boolean layerAdded = false;

        /* switch to the ListModelList if we are currently using simplelistmodel
         * to display the 'no layers selected' message
         *
         * If the model is already an instance of ListModelList then
         * the model should already have the data it needs so just
         * fire the update event.
         */
        if (!(activeLayersList.getModel() instanceof ListModelList)) {
            logger.debug("changing model for Active Layers to ListModelList");
            /* this is the first item being added to the list so we make
             * it a new ListModelList instance based on live data
             */
            activeLayersList.setModel(new ListModelList(activeLayers, true));

        }

        if (!activeLayers.contains(mapLayer) && mapLayer.isDisplayable()) {

            /* assume we want to display on the map straight away - set checkbox
             * to true
             */
            activeLayersList.setItemRenderer(activeLayerRenderer);


            /* use the MODEL facade to add the new layer (it's not smart enough
             * to detect the change otherwise.
             *
             * We always add to the top of the list so that newly actived
             * map layers display above existing ones
             */

            ((ListModelList) activeLayersList.getModel()).add(0, mapLayer);

            //select this map layer
            activeLayersList.setSelectedIndex(0);

            // update the map
            if (doJavaScript) {
                openLayersJavascript.activateMapLayerNow(mapLayer);
            }

            updateLayerControls();
            layerAdded = true;
        } else {
            logger.debug(
                    "not displaying map layer because its already listed or is marked non-displayable");
        }

        adjustActiveLayersList();

        refreshContextualMenu();

        return layerAdded;

    }

    /**
     * Remove an item from the list of active layers and put it back in the
     * tree menu of available layers
     * @param itemToRemove
     */
    public void deactiveLayer(MapLayer itemToRemove, boolean updateMapAndLayerControls, boolean recursive) {
        deactiveLayer(itemToRemove, updateMapAndLayerControls, recursive, false);
    }

    public void deactiveLayer(MapLayer itemToRemove, boolean updateMapAndLayerControls, boolean recursive, boolean updateOnly) {
        if (itemToRemove != null) {
            Query q = (Query) itemToRemove.getData("query");
            if (q != null && q instanceof UploadQuery) {
                String pid = ((UploadQuery) q).getQ();

                Hashtable<String, UserData> htUserSpecies = (Hashtable) getMapComposer().getSession().getAttribute("userpoints");
                if (htUserSpecies != null) {
                    htUserSpecies.remove(pid);
                }
            }

            boolean deListedInActiveLayers = false;

            // update the active layers list
            List<MapLayer> activeLayers = getPortalSession().getActiveLayers();
            if (activeLayers != null) {
                logger.debug("obtained activelayers arraylist from session, count: " + activeLayers.size());
                if (activeLayers.size() > 0) {
                    ListModelList listmodel = (ListModelList) activeLayersList.getModel();
                    if (listmodel != null) {
                        listmodel.remove(itemToRemove);

                        if (activeLayers.size() == 0) {
                            displayEmptyActiveLayers(activeLayersList);
                            lblSelectedLayer.setValue("No layers added");
                        }
                    }
                } else {
                    logger.debug("active layers list is empty, so not updating it");
                }
            }

            /* only the items in the active menu will have been marked
             * as being displayed (gets done by the changeSelection()
             * method), so we must set the listedInActiveLayers flag
             * ourself if we skipped this stage because the item is
             * in a menu that's not being displayed
             */
            if (!deListedInActiveLayers) {
                itemToRemove.setListedInActiveLayers(false);
            }

            if (updateMapAndLayerControls) {
                if (!updateOnly) {
                    // tell openlayers to kill the layer
                    openLayersJavascript.removeMapLayerNow(itemToRemove);

                }
                updateLayerControls();
                removeFromSession(itemToRemove.getName());
            }

            // descend children if we were called recursively
            if (recursive && itemToRemove.hasChildren()) {
                for (MapLayer child : itemToRemove.getChildren()) {
                    deactiveLayer(child, updateMapAndLayerControls, recursive);
                }
            }
        }

        refreshContextualMenu();

        // hide layer controls
        //hideLayerControls(null);
    }

    /**
     * Remove an item from the list of active layers and put it back in the
     * tree menu of available layers
     * @param itemToRemove
     */
    public void removeFromList(MapLayer itemToRemove) {
        if (itemToRemove != null) {
            boolean deListedInActiveLayers = false;

            // update the active layers list
            List<MapLayer> activeLayers = getPortalSession().getActiveLayers();
            if (activeLayers != null) {
                logger.debug("obtained activelayers arraylist from session, count: " + activeLayers.size());
                if (activeLayers.size() > 0) {
                    ListModelList listmodel = (ListModelList) activeLayersList.getModel();
                    if (listmodel != null) {
                        listmodel.remove(itemToRemove);

                        if (activeLayers.size() == 0) {
                            displayEmptyActiveLayers(activeLayersList);
                        }
                    }
                } else {
                    logger.debug("active layers list is empty, so not updating it");
                }
            }

            /* only the items in the active menu will have been marked
             * as being displayed (gets done by the changeSelection()
             * method), so we must set the listedInActiveLayers flag
             * ourself if we skipped this stage because the item is
             * in a menu that's not being displayed
             */
            if (!deListedInActiveLayers) {
                itemToRemove.setListedInActiveLayers(false);
            }

        }

        refreshContextualMenu();

        // hide layer controls
        //hideLayerControls(null);
    }

    /**
     * A simple message dialogue
     * @param message Full text of message to show
     */
    public void showMessage(String message) {
        ErrorMessageComposer window = (ErrorMessageComposer) Executions.createComponents("WEB-INF/zul/ErrorMessage.zul", null, null);
        window.setMessage(message);
        window.doOverlapped();
    }
    /**
     * A simple message dialogue to display over AnalysisToolComposer
     * @param message Full text of message to show
     */
    boolean mp = true;

    public void showMessage(String message, Component parent) {
        ErrorMessageComposer window = (ErrorMessageComposer) Executions.createComponents("WEB-INF/zul/ErrorMessage.zul", parent, null);
        window.setMessage(message);
        if (mp) {
            try {
                window.doModal();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            window.doOverlapped();
        }
    }

    /**
     * Show a message dialogue.  Initially a short message is
     * shown but the user can click 'show details' to get a more
     * informative message.
     *
     * A default message title is obtained from the config file
     * @param message
     * @param messageDetail
     */
    public void showMessage(String message, String messageDetail) {
        showMessage(languagePack.getLang("default_message_title"), message, messageDetail);
    }

    /**
     * Show a message dialogue.  Initially a short message is
     * shown but the user can click 'show details' to get a more
     * informative message.
     *
     * A title must be provided for the popup box
     * @param title
     * @param message
     * @param messageDetail
     */
    public void showMessage(String title, String message, String messageDetail) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("messageTitle", title);
        params.put("message", message);
        params.put("messageDetail", messageDetail);

        Window window = (Window) Executions.createComponents("WEB-INF/zul/ErrorMessageWithDetail.zul", null, params);
        window.doOverlapped();
    }

    /**
     * This is a fixed size (because of zk limitations) message
     * dialogue featuring:
     * 	o	title
     * 	o	brief description
     * 	o	detailed description (hidden by default)
     * 	o	link to raw data (hidden by default)
     * 	o	Iframe full of raw data (hidden by default)
     * There are lots of parameters for this method so if you want
     * to use it, it's easiest to write a wrapper and just call that
     * when you have a problem.
     *
     * This is not a general purpose error message - everything
     * is fixed sizes (has to be or the iframe doesn't display
     * properly.  If you want a general purpose error message, use
     * the showMessageNow() calls
     *
     * @param title TEXT of the title or null to use default from config file
     * @param message TEXT in config file of the error message
     * @param messageDetail TEXT to display if user clicks 'show detail'
     * @param rawMessageTitle TEXT to display before raw output or null to ignore
     * @param rawMessage TEXT of raw error message or null to ignore
     */
    public void showMessage(String title,
            String message,
            String messageDetail,
            String linkTitle,
            String linkHref,
            String rawMessageTitle,
            String rawMessage) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("messageTitle", title);
        params.put("message", message);
        params.put("messageDetail", messageDetail);
        params.put("linkTitle", linkTitle);
        params.put("linkHref", linkHref);
        params.put("rawMessageTitle", rawMessageTitle);

        // this will be the inside of the iframe...

        /* StringMedia is not allowed to be null (will give an error)
         * so if no data was available, give the user a message
         */
        if (rawMessage == null) {
            rawMessage = languagePack.getLang("null_raw_data");
        }
        StringMedia rawErrorMessageMedia = new StringMedia(rawMessage);

        /* have to store the raw error message in the user's session
         * temporarily to prevent getting a big zk severe error if
         * the iframe is requested after it is supposed to have been
         * dereferenced
         */
        getPortalSession().setRawErrorMessageMedia(rawErrorMessageMedia);
        rawMessageIframeHack.setContent(rawErrorMessageMedia);

        /* the raw text can't go in with the params or it gets
         * escaped by zk - you have to do weird things with
         * iframes and the media class instead
         */

        Window window;
        window = (Window) Executions.createComponents("WEB-INF/zul/ErrorMessageWithDetailAndRawData.zul", null, params);

        /* now we grab the hidden iframe in index.zul and move it into
         * our message box - again this is to prevent a massive zk error
         * if the iframe content is requested after it's supposed to have
         * gone
         */
        Component holder = window.getFellow("rawMessageHolder");
        rawMessageIframeHack.setParent(holder);
        window.doOverlapped();

        /* at this point the user has closed the message box - we
         * only have one more thing to do to stop the big zk error
         * and that is to grab the iframe back off the error message
         * window and put it back where we found it in the index.zul
         * page - not pretty or efficient but works a treat!
         */
        rawMessageIframeHack.setParent(rawMessageHackHolder);
    }

    /**
     * Add a map layer to the user defined map layers group (My Layers)
     * @param layer
     *
     *
     */
    public void addUserDefinedLayerToMenu(MapLayer mapLayer, boolean activate) {
        if (safeToPerformMapAction()) {
            // activate the layer in openlayers and display in active layers without
            // updating the tree (because its not displayed)
            activateLayer(mapLayer, true, true);

            // we must tell any future tree menus that the map layer is already
            // displayed as we didn't use changeSelection()
            mapLayer.setListedInActiveLayers(true);

            logger.debug("leaving addUserDefinedLayerToMenu");
        }
    }

    /**
     * Initial building of the tree menu and active layers list based on
     * the values obtained from the current session.
     *
     * JavaScript for loading default map layers and setting the default
     * zoombox is in SessionInit.java
     */
    public void load() {
        logger.debug("entering loadMapLayers");

        motd();

        PortalSession portalSession = getPortalSession();
        List<MapLayer> activeLayers = portalSession.getActiveLayers();

        // model and renderer for active layers list
        ListModelList activeLayerModel = new ListModelList(activeLayers, true);


        // tell the list about them...
        if (activeLayers.size() == 0) {
            activeLayers.add(remoteMap.createLocalLayer(LayerUtilities.MAP, "Map options"));
        }

        activeLayersList.setModel(activeLayerModel);
        adjustActiveLayersList();
        activeLayersList.setItemRenderer(activeLayerRenderer);
        activeLayersList.setSelectedIndex(activeLayerModel.size() - 1);

        updateLayerControls();

        //showCurrentMenu();

        //activateNavigationTab(portalSession.getCurrentNavigationTab());
        //maximise();

    }

    /**
     * Display an empty list in the active layers box - usually this will
     * consist of a single list element with a value along the lines of
     * 'please select map layers'
     * @param activeLayersList
     */
    void displayEmptyActiveLayers(Listbox activeLayersList) {
        //logger.debug("will display empty active layers list");
        //activeLayersList.setItemRenderer(new EmptyActiveLayersRenderer());
        //activeLayersList.setModel(new SimpleListModel(new String[]{languagePack.getLang("active_layers_empty")}));
    }

    /**
     * Add a WMS layer identified by the given parameters to the menu system
     * and activate it
     * @param label Name of map layer
     * @param uri URI for the WMS service
     * @param opacity 0 for invisible, 1 for solid
     * @param filter filter
     * @param legend URI for map layer legend
     */
    public MapLayer addWMSLayer(String label, String uri, float opacity, String metadata, String legendUri, int subType, String cqlfilter, String envParams) {
        return addWMSLayer(label, uri, opacity, metadata, legendUri, subType, cqlfilter, envParams, null);
    }

    public MapLayer addWMSLayer(String label, String uri, float opacity, String metadata, String legendUri, int subType, String cqlfilter, String envParams, Query q) {
        MapLayer mapLayer = null;
        if (safeToPerformMapAction()) {
            if (portalSessionUtilities.getUserDefinedById(getPortalSession(), uri) == null) {
                mapLayer = remoteMap.createAndTestWMSLayer(label, uri, opacity);
                if (q != null) {
                    mapLayer.setData("query", q);
                }
                if (mapLayer == null) {
                    // fail
                    //errorMessageBrokenWMSLayer(imageTester);
                    logger.info("adding WMS layer failed ");
                } else {
                    //ok
                    mapLayer.setSubType(subType);
                    mapLayer.setCql(cqlfilter);
                    mapLayer.setEnvParams(envParams);
                    if (metadata != null) {
                        uri = CommonData.geoServer + "/wms?REQUEST=GetLegendGraphic&VERSION=1.0.0&FORMAT=image/png&WIDTH=20&HEIGHT=9&LAYER=" + mapLayer.getLayer();
                        mapLayer.setDefaultStyleLegendUri(uri);
                        if (mapLayer.getMapLayerMetadata() == null) {
                            mapLayer.setMapLayerMetadata(new MapLayerMetadata());
                        }
                        mapLayer.getMapLayerMetadata().setMoreInfo(metadata + "\n" + label);
                    }
                    if (legendUri != null) {
                        WMSStyle style = new WMSStyle();
                        style.setName("Default");
                        style.setDescription("Default style");
                        style.setTitle("Default");
                        style.setLegendUri(legendUri);
                        mapLayer.addStyle(style);
                        mapLayer.setSelectedStyleIndex(1);
                        logger.info("adding WMSStyle with legendUri: " + legendUri);
                        mapLayer.setDefaultStyleLegendUriSet(true);
                    }

                    addUserDefinedLayerToMenu(mapLayer, true);
                }
            } else {
                // fail
                showMessage(languagePack.getLang("wms_layer_already_exists"));
                logger.info(
                        "refusing to add a new layer with URI " + uri
                        + " because it already exists in the menu");
            }
        }
        return mapLayer;
    }

    public MapLayer getMapLayer(String label) {
        // check if layer already present
        List udl = getPortalSession().getActiveLayers();
        Iterator iudl = udl.iterator();
        System.out.println("session active layers: " + udl.size() + " looking for: " + label);
        while (iudl.hasNext()) {
            MapLayer ml = (MapLayer) iudl.next();
            System.out.println("layer: " + ml.getName() + " - " + ml.getId() + " - " + ml.getNameJS());
            if (ml.getName().equals(label)) {
                return ml;
            }
        }

        return null;
    }

    public MapLayer getMapLayerDisplayName(String label) {
        // check if layer already present
        List udl = getPortalSession().getActiveLayers();
        Iterator iudl = udl.iterator();
        System.out.println("session active layers: " + udl.size() + " looking for: " + label);
        while (iudl.hasNext()) {
            MapLayer ml = (MapLayer) iudl.next();
            System.out.println("layer: " + ml.getName() + " - " + ml.getId() + " - " + ml.getNameJS());
            if (ml.getDisplayName().equals(label)) {
                return ml;
            }
        }

        return null;
    }

    public void removeLayer(String label) {
        if (safeToPerformMapAction()) {
            MapLayer mapLayer = getMapLayer(label);
            if (mapLayer != null) {
                deactiveLayer(mapLayer, true, false);
            } else {
                // fail
                showMessage(languagePack.getLang("wms_layer_remove_error"));
                logger.info("unable to remove layer with label" + label);
            }
        }
    }

    public MapLayer addImageLayer(String id, String label, String uri, float opacity, List<Double> bbox, int subType) {
        // check if layer already present
        MapLayer imageLayer = getMapLayer(label);

        if (safeToPerformMapAction()) {
            if (imageLayer == null) {
                System.out.println("activating new layer");

                //start with a new MapLayer
                imageLayer = new MapLayer();
                //set its type
                imageLayer.setType(LayerUtilities.IMAGELAYER);
                //the name is what will appear in the active layer list
                imageLayer.setName(label);

                imageLayer.setSubType(subType);

                //where do i find the image at the moment it is only enabled for png
                imageLayer.setId(label);

                //the combination of the next two is used by openlayers to create a unique name
                //its a bit dull using the ural and layer name but its a hangover from thinking
                //everything in the world is a WMS layer
                imageLayer.setLayer("wms.png");
                imageLayer.setUri(uri);

                //set the layers opacity 0 - 1
                imageLayer.setOpacity(opacity); // (float) 0.75

                //need the bbox so the map knows where to put it
                //bbox info is stored in here
                MapLayerMetadata md = new MapLayerMetadata();
                md.setBbox(bbox);
                //remember to add the MapLayerMetadata to the MapLayer
                imageLayer.setMapLayerMetadata(md);

                //needs to be true or the map won't bother rendering it
                imageLayer.setDisplayable(true);

                //call this to add it to the map and also put it in the active layer list
                activateLayer(imageLayer, true, true);

            } else {
                System.out.println("refreshing exisiting layer");
                imageLayer.setUri(uri); // + "&_lt=" + System.currentTimeMillis());
                imageLayer.setOpacity(opacity); // (float) 0.75

                openLayersJavascript.reloadMapLayerNow(imageLayer);
            }
        }

        return imageLayer;
    }

    /*
     * Public utility methods to interogate the state of the form controls
     */
    /**
     * Return the MapLayer instance associated with the item currently selected
     * in the active layers listbox or null if no item is currently selected.
     *
     * If nothing is selected and alertOnNoSelection is set true, show the user
     * a message and return null
     *
     * @return selected MapLayer instance or null if there is nothing selected
     */
    public MapLayer getActiveLayersSelection(boolean alertOnNoSelection) {
        MapLayer mapLayer = null;

        // only one item can be selected at a time
        Listitem selected = (Listitem) activeLayersList.getSelectedItem();

        if (selected != null) {
            mapLayer = (MapLayer) selected.getValue();
        } else if (alertOnNoSelection) {
            showMessage(languagePack.getLang("active_layer_not_selected"));

            // OK let's see who called us so we can debug this...
            Exception e = new RuntimeException();
            if (e.getStackTrace().length >= 2) {
                StackTraceElement s = e.getStackTrace()[1];
                logger.error(
                        "***FIXME*** no active layer selected - getActiveLayersSelection() invoked by "
                        + s.getClassName() + "::"
                        + s.getMethodName()
                        + " on line "
                        + s.getLineNumber());
            }
        }

        return mapLayer;
    }

    public void toggleActiveLayer(boolean bCheck) {
        Listitem selected = (Listitem) activeLayersList.getSelectedItem();

        if (selected != null) {
            for (Object cell : ((Listitem) selected).getChildren()) {
                logger.debug("cell :" + cell);
                // CHILDREN COUNT is ALWAYS 1
                if (cell instanceof Listcell) {
                    Listcell listcell = (Listcell) cell;

                    logger.debug("cell :" + listcell.getLabel());
                    for (Object innercell : listcell.getChildren()) {
                        // NEVER GET HERE
                        if (innercell instanceof Checkbox) {
                            logger.debug("InnerCell = Checkbox");
                            ((Checkbox) innercell).setChecked(bCheck);
                            Events.sendEvent(((Checkbox) innercell), new Event("onCheck", ((Checkbox) innercell)));

                        }
                    }
                }
            }
        }
    }

    /**
     * Simple test to see whether we have active layers in the active layers
     * list or not
     * @return
     */
    public boolean haveActiveLayers() {
        boolean haveActiveLayers;
        if (getPortalSession().getActiveLayers().size() > 0) {
            haveActiveLayers = true;
        } else {
            haveActiveLayers = false;
        }
        return haveActiveLayers;
    }

    public void mapLoaded(String text) {
        boolean loaded = Boolean.parseBoolean(text);
        getPortalSession().setMapLoaded(loaded);

        if (loaded) {
            //openLayersJavascript.execute("window.mapFrame.loadBaseMap();");
            openLayersJavascript.setAdditionalScript("window.mapFrame.loadBaseMap();");

            System.out.println("map is now loaded. let's try mapping.");
            MapLayer ml = loadUrlParameters();

            if (ml == null) {
                openLayersJavascript.useAdditionalScript();
            }
        }
    }

    /**
     * Check if its safe to do things to the map - if it's not,
     * show a popup box
     * @return
     */
    public boolean safeToPerformMapAction() {
        boolean safe;
        if (openLayersJavascript.mapLoaded()) {
            safe = true;
        } else {
            showMessage(languagePack.getLang("map_load_error"));
            safe = false;
        }
        return safe;
    }

    /**
     * Extract the value of custom attribute systemId from the passed
     * in ForwardEvent instance
     * @param event
     * @return value of custom attribute systemId
     */
    private String getSystemId(ForwardEvent event) {
        return (String) event.getOrigin().getTarget().getAttribute("systemId");
    }

    //-- AfterCompose --//
    @Override
    public void afterCompose() {
        super.afterCompose();

        //window settings, for printing
        applyWindowParams();

        // showtime!
        load();

        //active layers list

    }

    /**
     * apply window parameters
     *
     * p = width in pixels,height in pixels,longitude1,latitude1,longitude2,latitude2
     */
    void applyWindowParams() {
        String s = (String) Executions.getCurrent().getParameter("p");

        //TODO: validate params
        if (s != null) {
            String[] pa = s.split(",");
            setWidth(pa[0] + "px");

            if (pa.length > 1) {
                setHeight(pa[1] + "px");
            }
        }
    }

    private Map getUserParameters(String userParams) {
        if (StringUtils.isBlank(userParams) || userParams.equals("{}")) {
            return null;
        }

        Executions.getCurrent().getArg();
        Map<String, String> uparams = new HashMap();

        // check if the string is a proper params
        if (userParams.startsWith("{") && userParams.endsWith("}")) {
            userParams = userParams.substring(1, userParams.lastIndexOf("}"));
        }

        String[] auparams = userParams.split(",");
        System.out.println("Got " + auparams.length + " parameters");
        for (int ip = 0; ip < auparams.length; ip++) {
            String[] uparam = auparams[ip].split("=");
            if (uparam.length > 1) {
                uparams.put(uparam[0].trim(), uparam[1].trim());
            }
        }

        return uparams;
    }

    public void echoMapSpeciesByLSID(Event event) {
        String lsid = (String) event.getData();
        try {
            mapSpecies(QueryUtil.get(lsid, this, true), null, "species", -1, LayerUtilities.SPECIES, null, -1);
        } catch (Exception e) {
            //try again
            Events.echoEvent("echoMapSpeciesByLSID", this, lsid);
        }
    }

    private MapLayer loadUrlParameters() {
        String params = null;
        Cookie[] cookies = ((HttpServletRequest) Executions.getCurrent().getNativeRequest()).getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("analysis_layer_selections")) {
                    try {
                        String[] s = URLDecoder.decode(cookie.getValue(), "UTF-8").split("\n");
                        for (int i = 0; i < s.length; i++) {
                            String[] ls = s[i].split(" // ");
                            selectedLayers.add(new LayerSelection(ls[0], ls[1]));
                        }
                        break;
                    } catch (Exception e) {
                    }

                }
            }
        }
        try {
            params = Executions.getCurrent().getDesktop().getQueryString();
            System.out.println("User params: " + params);

            List<Entry<String, String>> userParams = getQueryParameters(params);
            StringBuilder sb = new StringBuilder();
            String qc = null;
            String bs = null;
            String ws = null;
            String wkt = null;
            if (userParams != null) {
                for (int i = 0; i < userParams.size(); i++) {
                    String key = userParams.get(i).getKey();
                    String value = userParams.get(i).getValue();

                    if (key.equals("wmscache")) {
                        useSpeciesWMSCache = value;
                    }

                    if (key.contains("species_lsid")) {
                        Events.echoEvent("echoMapSpeciesByLSID", this, value);
                        return null;
                    } else {
                        if (key.equals("q") || key.equals("fq")) {
                            if(value.equals("*:*")) {
                                continue;
                            }
                            if (sb.length() > 0) {
                                sb.append(" AND ");
                            }
                            //wrap value in " when appropriate
                            //Not key:[..
                            //Not key:*
                            //Not key:"...
                            //Not key:... AND key:....
                            //Not key:... OR key:...
                            //Not key:.."..
                            int p = value.indexOf(':');
                            if(p > 0 && p +1 < value.length()
                                    && value.charAt(p+1) != '['
                                    && value.charAt(p+1) != '*'
                                    && value.charAt(p+1) != '"'
                                    && !value.contains(" AND ")
                                    && !value.contains(" OR ")
                                    && !value.contains("\"")) {
                                value = value.substring(0,p+1) + "\""
                                        + value.substring(p+1) + "\"";
                            }
                            sb.append("(").append(value).append(")");
                        } else if (key.equals("qc")) {
                            qc = "&qc=" + URLEncoder.encode(value, "UTF-8");
                        } else if (key.equals("bs")) {
                            bs = value;
                        } else if (key.equals("ws")) {
                            ws = value;
                        } else if (key.equals("wkt")) {
                            wkt = value;
                        }
                    }
                }

                System.out.println("url query: " + sb.toString());
                if (sb.toString().length() > 0) {
                    String query = sb.toString();
                    boolean showWarning = true;
                    if (query.contains(" AND geospatial_kosher:true")) {
                        query = query.replace(" AND geospatial_kosher:true", "");
                        showWarning = false;
                    } else if (query.contains("geospatial_kosher:true AND ")) {
                        query = query.replace("geospatial_kosher:true AND ", "");
                        showWarning = false;
                    }

                    BiocacheQuery q = new BiocacheQuery(null, wkt, sb.toString(), null, true, bs, ws);
                    
                    if (qc != null) {
                        q.setQc(qc);
                    }

                    if (getMapLayerDisplayName(q.getSolrName()) == null) {
                        if (showWarning) {
//                            showMessage("You are mapping a species layer that may have fewer occurrences than you expect.\n\n"
//                                    + q.getOccurrenceCount() + " occurrences with valid coordinates found.");
                        }

                        List<Double> bbox = q.getBBox();
                        String script = "map.zoomToExtent(new OpenLayers.Bounds("
                                + bbox.get(0) + "," + bbox.get(1) + "," + bbox.get(2) + "," + bbox.get(3) + ")"
                                + ".transform("
                                + "  new OpenLayers.Projection('EPSG:4326'),"
                                + "  map.getProjectionObject()));";
                        openLayersJavascript.setAdditionalScript(script);

                        return mapSpecies(q, q.getSolrName(), "species", q.getOccurrenceCount(), LayerUtilities.SPECIES, null, -1);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Opps error loading url parameters: " + params);
            e.printStackTrace(System.out);
        }

        return null;
    }

    public void onActivateLink(ForwardEvent event) {
        Component component = event.getOrigin().getTarget();
        Object oLink = component.getAttribute("link");
        if (oLink.getClass().getName().equals("java.lang.String")) {
            String uri = (String) oLink;
            String label = (String) component.getAttribute("label");
            this.activateLink(uri, label, false);
        }
    }

    public MapLayer addKMLLayer(String label, String name, String uri) {
        return addKMLLayer(label, name, uri, false);
    }

    public MapLayer addKMLLayer(String label, String name, String uri, boolean forceReload) {
        MapLayer mapLayer = null;

        if (safeToPerformMapAction()) {
            MapLayer gjLayer = getMapLayer(label);
            if (forceReload) {
                if (gjLayer != null) {
                    System.out.println("removing existing layer: " + gjLayer.getName());
                    openLayersJavascript.setAdditionalScript(
                            openLayersJavascript.removeMapLayer(gjLayer));
                } //else {
                mapLayer = remoteMap.createKMLLayer(label, name, uri);
                if (mapLayer == null) {
                    // fail
                    //hide error, might be clustering zoom in; showMessage("No mappable features available");
                    logger.info("adding KML layer failed ");
                } else {
                    mapLayer.setDisplayable(true);
                    mapLayer.setOpacity((float) 0.6);
                    mapLayer.setQueryable(true);
                    mapLayer.setDynamicStyle(true);

                    activateLayer(mapLayer, true, true);

                    // we must tell any future tree menus that the map layer is already
                    // displayed as we didn't use changeSelection()
                    mapLayer.setListedInActiveLayers(true);
                }
                //}
            } else {
                //if (portalSessionUtilities.getUserDefinedById(getPortalSession(), uri) == null) {
                boolean okToAdd = false;
                MapLayer mlExisting = getMapLayer(label);
                if (mlExisting == null) {
                    okToAdd = true;
                } else if (!mlExisting.getUri().equals(uri)) {
                    // check if it's a different url
                    // if it is, then it is ok to add
                    // and assume the previous is removed.
                    okToAdd = true;
                } else {
                }

                if (okToAdd) {
                    mapLayer = remoteMap.createKMLLayer(label, name, uri);
                    if (mapLayer == null) {
                        // fail
                        //hide error, might be clustering zoom in; showMessage("No mappable features available");
                        logger.info("adding KML layer failed ");
                    } else {
                        mapLayer.setDisplayable(true);
                        mapLayer.setOpacity((float) 0.6);
                        mapLayer.setQueryable(true);
                        mapLayer.setDynamicStyle(true);

                        activateLayer(mapLayer, true, true);

                        // we must tell any future tree menus that the map layer is already
                        // displayed as we didn't use changeSelection()
                        mapLayer.setListedInActiveLayers(true);
                    }
                } else {
                    //need to cleanup any additional scripts outstanding
                    openLayersJavascript.useAdditionalScript();

                    // fail
                    //showMessage("GeoJSON layer already exists");
                    logger.info(
                            "refusing to add a new layer with URI " + uri
                            + " because it already exists in the menu");
                }
            }
        }

        return mapLayer;
    }

    public MapLayer addWKTLayer(String wkt, String label, String displayName) {
        MapLayer mapLayer = null;

        if (safeToPerformMapAction()) {
            if (portalSessionUtilities.getUserDefinedById(getPortalSession(), label) == null) {
                mapLayer = remoteMap.createWKTLayer(wkt, label);
                mapLayer.setDisplayName(displayName);
                if (mapLayer == null) {
                    // fail
                    showMessage("No mappable features available");
                    logger.info("adding WKT layer failed ");
                } else {
                    mapLayer.setDisplayable(true);
                    mapLayer.setOpacity((float) 0.4);
                    mapLayer.setQueryable(true);
                    mapLayer.setDynamicStyle(true);

                    activateLayer(mapLayer, true, true);

                    // we must tell any future tree menus that the map layer is already
                    // displayed as we didn't use changeSelection()
                    mapLayer.setListedInActiveLayers(true);
                }
            } else {
                // fail
                showMessage("WKT layer already exists");
                logger.info(
                        "refusing to add a new layer with name " + label
                        + " because it already exists in the menu");
            }
        }

        return mapLayer;
    }

    public MapLayer addGeoJSON(String labelValue, String uriValue) {
        return this.addGeoJSONLayer(labelValue, uriValue, "", false, false);
    }

    public MapLayer addGeoJSONLayer(String label, String uri, String params, boolean forceReload, boolean points_type) {
        MapLayer mapLayer = null;

        if (safeToPerformMapAction()) {
            MapLayer gjLayer = getMapLayer(label);
            if (forceReload) {
                if (gjLayer != null) {
                    System.out.println("removing existing layer: " + gjLayer.getName());
                    openLayersJavascript.setAdditionalScript(
                            openLayersJavascript.removeMapLayer(gjLayer));
                } //else {
                mapLayer = remoteMap.createGeoJSONLayer(label, uri, points_type, nextColour());
                if (mapLayer == null) {
                    // fail
                    //hide error, might be clustering zoom in; showMessage("No mappable features available");
                    logger.info("adding GEOJSON layer failed ");
                } else {
                    mapLayer.setDisplayable(true);
                    mapLayer.setOpacity((float) 0.6);
                    mapLayer.setQueryable(true);
                    mapLayer.setDynamicStyle(true);

                    activateLayer(mapLayer, true, true);

                    // we must tell any future tree menus that the map layer is already
                    // displayed as we didn't use changeSelection()
                    mapLayer.setListedInActiveLayers(true);
                }
                //}
            } else {
                //if (portalSessionUtilities.getUserDefinedById(getPortalSession(), uri) == null) {
                boolean okToAdd = false;
                MapLayer mlExisting = getMapLayer(label);
                if (mlExisting == null) {
                    okToAdd = true;
                } else if (!mlExisting.getUri().equals(uri)) {
                    // check if it's a different url
                    // if it is, then it is ok to add
                    // and assume the previous is removed.
                    okToAdd = true;
                } else {
                }

                if (okToAdd) {
                    mapLayer = remoteMap.createGeoJSONLayer(label, uri, points_type, activeLayerMapProperties, nextColour());
                    if (mapLayer == null) {
                        // fail
                        //hide error, might be clustering zoom in; showMessage("No mappable features available");
                        logger.info("adding GEOJSON layer failed ");
                    } else {
                        mapLayer.setDisplayable(true);
                        mapLayer.setOpacity((float) 0.6);
                        mapLayer.setQueryable(true);
                        mapLayer.setDynamicStyle(true);

                        activateLayer(mapLayer, true, true);

                        // we must tell any future tree menus that the map layer is already
                        // displayed as we didn't use changeSelection()
                        mapLayer.setListedInActiveLayers(true);
                    }
                } else {
                    //need to cleanup any additional scripts outstanding
                    openLayersJavascript.useAdditionalScript();

                    // fail
                    //showMessage("GeoJSON layer already exists");
                    logger.info(
                            "refusing to add a new layer with URI " + uri
                            + " because it already exists in the menu");
                }
            }
        }

        return mapLayer;
    }

    /**
     * Destroy session and reload page
     *
     * - added confirmation message, needs to be event driven since
     * message box always returning '1' with current zk settings.
     */
    public void onClick$reloadPortal() {
        // user confirmation for whole map reset
        try {
            Messagebox.show("Reset map to initial empty state, with no layers and default settings?", "Reset Map",
                    Messagebox.YES | Messagebox.NO, Messagebox.QUESTION, new EventListener() {

                @Override
                public void onEvent(Event event) throws Exception {
                    if (((Integer) event.getData()).intValue() != Messagebox.YES) {
                        //no selected
                        return;
                    } else {
                        //reset map
                        reloadPortal();
                        return;
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * split from button for yes/no event processing
     *
     * see onClick$reloadPortal()
     */
    void reloadPortal() {
        // grab the portaluser instance so that logged in users stay logged in...
        PortalSession portalSession = getPortalSession();
        //PortalUser portalUser = portalSession.getPortalUser();

        // remove the old species filters
        getSession().removeAttribute("speciesfilters");

        // create a new session from the master session
        SessionInit sessionInit = new SessionInitImpl();
        try {
            sessionInit.init(Sessions.getCurrent(), null);
        } catch (Exception ex) {
            logger.error("Error creating replacement session information after user clicked reload portal", ex);

        } // check PortalSession is not null - if it is, something is badly wrong
        // so redirect to error page
        portalSession = getPortalSession();
        if (portalSession == null) {
            logger.error("Null portal session created after clicking reset map, sending user to error page");
            try {
                Executions.getCurrent().forward("/WEB-INF/jsp/Error.jsp");
            } catch (IOException ex) {
                logger.error("error redirecting to error page", ex);
            }
        } else {
            // all good - put the portal user back in, then force a page reload
            Executions.getCurrent().sendRedirect(null);
        }
    }

    /**
     * Reload all species layers
     */
    public void onReloadLayers(Event event) {
        String tbxReloadLayers;
        boolean mapZoomChanged = true;

        if (event == null) {
            mapZoomChanged = (mapZoomLevel != getLeftmenuSearchComposer().getZoom());
            mapZoomLevel = getLeftmenuSearchComposer().getZoom();

            tbxReloadLayers = (new StringBuffer()).append("z=").append(String.valueOf(mapZoomLevel)).append("&amp;b=").append(getLeftmenuSearchComposer().getViewportBoundingBox().toString()).toString();
        } else {
            tbxReloadLayers = (String) event.getData();

            //update this.mapZoomLevel
            try {
                int mapZoomLevelOld = mapZoomLevel;
                String s = tbxReloadLayers;
                int s1 = s.indexOf("z=");
                int s2 = s.indexOf("&");
                if (s1 >= 0) {
                    if (s2 >= 0) {
                        mapZoomLevel = Integer.parseInt(s.substring(s1 + 2, s2));
                    } else {
                        mapZoomLevel = Integer.parseInt(s.substring(s1 + 2));
                    }
                }
                mapZoomChanged = (mapZoomLevelOld != mapZoomLevel);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //System.out.println("tbxReloadLayers.getValue(): " + tbxReloadLayers);

        }
        System.out.println("tbxReloadLayers.getValue(): " + tbxReloadLayers);
        // iterate thru' active map layers
        List udl = getPortalSession().getActiveLayers();
        Iterator iudl = udl.iterator();
        Vector<String> processedLayers = new Vector();
        String reloadScript = "";
        while (iudl.hasNext()) {
            MapLayer ml = (MapLayer) iudl.next();
            if (processedLayers.contains(ml.getName())) {
                System.out.println(ml.getName() + " already processed.");
                continue;
            }
            System.out.println("checking reload layer: " + ml.getName() + " - " + ml.getId() + " - " + ml.getNameJS() + " -> type: " + ml.getType() + "," + ml.getGeometryType());

            processedLayers.add(ml.getName());
        }

        if (reloadScript.length() > 0) {
            openLayersJavascript.execute(
                    openLayersJavascript.iFrameReferences
                    + reloadScript);
        }
    }

    /**
     * generate pdf for from innerHTML of printHack txtbox
     *
     * dependant on whatever the ends up being on the map tab
     *
     * uses wkhtmltoimage (wkhtmltopdf not working?)
     *
     *
     */
    public void onClick$onPrint(Event event) {
        tbxPrintHack = (String) event.getData();
        PrintingComposer composer = (PrintingComposer) Executions.createComponents("/WEB-INF/zul/Printing.zul", this, null);
        try {
            composer.doModal();
        } catch (InterruptedException ex) {
            Logger.getLogger(MapComposer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SuspendNotAllowedException ex) {
            Logger.getLogger(MapComposer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * watch for layer loaded signals
     */
    public void onLayerLoaded(Event event) {
        tbxLayerLoaded = (String) event.getData();
        for (EventListener el : layerLoadedChangeEvents.values()) {
            try {
                el.onEvent(null);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public String getLayerLoaded() {
        return tbxLayerLoaded;
    }

    public void addLayerLoadedEventListener(String eventName, EventListener eventListener) {
        layerLoadedChangeEvents.put(eventName, eventListener);
    }

    public void removeLayerLoadedEventListener(String eventName) {
        layerLoadedChangeEvents.remove(eventName);
    }

    public void setWestWidth(String width) {
        menus.setWidth(width);
    }

    /**
     * Maximise map display area - currently just hides the left menumapNavigationTabContent
     * @param maximise
     */
    void maximise() {
        boolean maximise = getPortalSession().isMaximised();

        if (maximise) {
            menus.setWidth(settingsSupplementary.getValue(MENU_MINIMISED_WIDTH));
            menus.setBorder("none");
            menus.setStyle("overflow: hidden");
        } else {
            menus.setWidth(settingsSupplementary.getValue(MENU_DEFAULT_WIDTH));
            menus.setBorder("normal");
            menus.setStyle("overflow: auto");
        }
        westContent.setVisible(!maximise);
        westMinimised.setVisible(maximise);
        menus.setSplittable(!maximise);
    }

    public MapLayer mapSpecies(Query sq, String species, String rank, int count, int subType, String wkt, int setGrid) {

        if (species == null) {
            species = sq.getName();
            rank = sq.getRank();
        }

        if (count < 0) {
            count = sq.getOccurrenceCount();
        }

        //use # of points cutoff;
        boolean grid;
        if (setGrid == 0) {
            grid = false;
        } else if (setGrid == 1) {
            grid = true;
        } else {
            grid = sq.getOccurrenceCount() > settingsSupplementary.getValueAsInt(POINTS_CLUSTER_THRESHOLD);
        }
        MapLayer ml = mapSpeciesFilter(sq, species, rank, count, subType, wkt, grid);

        if (ml != null) {
            MapLayerMetadata md = ml.getMapLayerMetadata();
            if (md == null) {
                md = new MapLayerMetadata();
                ml.setMapLayerMetadata(md);
            }
            //md.setSpeciesLsid(sq.getShortQuery());
            md.setSpeciesDisplayName(species);
            md.setSpeciesRank(rank);
            md.setOccurrencesCount(count);  //for Active Area mapping

            updateUserLogMapSpecies(sq.toString());

            updateLayerControls();
        }

        return ml;
    }

    void addLsidBoundingBoxToMetadata(MapLayerMetadata md, Query query) {
        //get bounding box for lsid
        List<Double> bb = query.getBBox();

        md.setBbox(bb);
    }

    private void loadDistributionMap(String lsids, String taxon, String wkt) {
        //test for a valid lsid match
        String[] wmsNames = CommonData.getSpeciesDistributionWMS(lsids);
        String[] metadata = CommonData.getSpeciesDistributionMetadata(lsids);
        if (wmsNames != null && wmsNames.length > 0 && (wkt == null || wkt.equals(CommonData.WORLD_WKT))) {
            //add all
            if (wmsNames.length > 1) {
                for (int i = 0; i < wmsNames.length; i++) {
                    addWMSLayer(taxon + " map " + (i + 1), wmsNames[i], 0.35f, metadata[i], null, LayerUtilities.SPECIES, null, null);
                }
            } else if (wmsNames.length == 1) {
                addWMSLayer(taxon + " map", wmsNames[0], 0.35f, metadata[0], null, LayerUtilities.SPECIES, null, null);
            }
        } else if (wmsNames != null && wmsNames.length > 0 && wkt != null && !wkt.equals(CommonData.WORLD_WKT)) {
            try {
                HttpClient client = new HttpClient();
                PostMethod post = new PostMethod(CommonData.layersServer + "/distributions"); // testurl
                post.addParameter("wkt", wkt);
                post.addParameter("lsids", lsids);
                post.addRequestHeader("Accept", "application/json, text/javascript, */*");
                int result = client.executeMethod(post);
                if (result == 200) {
                    String txt = post.getResponseBodyAsString();
                    JSONArray ja = JSONArray.fromObject(txt);
                    ArrayList<String> found = new ArrayList();
                    for (int i = 0; i < ja.size(); i++) {
                        JSONObject jo = ja.getJSONObject((i));
                        if (jo.containsKey("wmsurl")) {
                            found.add(jo.getString("wmsurl"));
                        }
                    }
                    if (found.size() > 1) {
                        for (int i = 0; i < wmsNames.length; i++) {
                            addWMSLayer(taxon + " map " + (i + 1), found.get(i), 0.35f, "", null, LayerUtilities.SPECIES, null, null);
                        }
                    } else if (found.size() == 1) {
                        addWMSLayer(taxon + " map", found.get(0), 0.35f, "", null, LayerUtilities.SPECIES, null, null);
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    MapLayer mapSpeciesFilter(Query q, String species, String rank, int count, int subType, String wkt, boolean grid) {
        String filter = q.getQ();

        if (q instanceof BiocacheQuery) {
            String lsids = ((BiocacheQuery) q).getLsids();
            if (lsids != null && lsids.length() > 0) {
                loadDistributionMap(lsids, species, wkt);
            }
        }

        MapLayer ml = mapSpeciesWMSByFilter(getNextAreaLayerName(species), filter, subType, q, grid);

        if (ml != null) {
            addToSession(ml.getName(), filter);

//            String infoUrl = getSettingsSupplementary().getValue(SPECIES_METADATA_URL).replace("_lsid_", q.getSingleLsid());
            MapLayerMetadata md = ml.getMapLayerMetadata();
            if (md == null) {
                md = new MapLayerMetadata();
                ml.setMapLayerMetadata(md);
            }
//            md.setMoreInfo(infoUrl + "\n" + species);
//            md.setSpeciesLsid(sq.getShortQuery());
            md.setSpeciesDisplayName(species);
//            md.setSpeciesDisplayLsid(sq.getShortQuery());
            md.setSpeciesRank(rank);
            md.setOccurrencesCount(count);

            ml.setClustered(false);

            if (grid) {
                ml.setColourMode("grid");
            }

            addLsidBoundingBoxToMetadata(md, q);

            refreshContextualMenu();
        }

        return ml;
    }

    MapLayer mapSpeciesWMSByFilter(String label, String filter, int subType, Query query, boolean grid) {
        String uri;
        String layerName = "ALA:occurrences";
        String sld = "species_point";

        //int hash = Math.abs(label.hashCode());
        int hash = nextColour();
        int r = (hash >> 16) & 0x000000ff;
        int g = (hash >> 8) & 0x000000ff;
        int b = (hash) & 0x000000ff;

        int uncertaintyCheck = 0; //0 == false default

        int size = 3;
        float opacity = (float) 0.6;

        if (activeLayerMapProperties != null) {
            r = ((Integer) activeLayerMapProperties.get("red")).intValue();
            b = ((Integer) activeLayerMapProperties.get("blue")).intValue();
            g = ((Integer) activeLayerMapProperties.get("green")).intValue();
            size = ((Integer) activeLayerMapProperties.get("size")).intValue();
            opacity = ((Float) activeLayerMapProperties.get("opacity")).floatValue();
            uncertaintyCheck = ((Integer) activeLayerMapProperties.get("uncertainty")).intValue();
        }

        if (subType == LayerUtilities.SCATTERPLOT) {
            //set defaults for scatterplot
            r = 0;
            g = 0;
            b = 255;
            opacity = 1;
            size = 4;
        }

        Color c = new Color(r, g, b);
        String hexColour = Integer.toHexString(c.getRGB() & 0x00ffffff);
        String envString = "";
        if (grid) {
            //colour mode is in 'filter' but need to move it to envString
            envString += "colormode:grid";
        } else {
            envString = "color:" + hexColour;
        }
        envString += ";name:circle;size:" + size + ";opacity:1";// + opacity;
        if (uncertaintyCheck > 0) {
            envString += ";uncertainty:1";
        }

        uri = query.getUrl();
        uri += "service=WMS&version=1.1.0&request=GetMap&styles=&format=image/png";
        uri += "&layers=ALA:occurrences";
        uri += "&transparent=true"; // "&env=" + envString +
        uri += (query.getQc() == null ? "" : query.getQc());
        uri += "&CACHE=" + useSpeciesWMSCache;
        uri += "&CQL_FILTER=";

        System.out.println("Mapping: " + label + " with " + uri + filter);

        try {
            if (safeToPerformMapAction()) {
                if (getMapLayer(label) == null) {
                    MapLayer ml = addWMSLayer(label, uri + filter, opacity, null, null, subType, "", envString, query);
                    if (ml != null) {
                        ml.setDynamicStyle(true);
                        ml.setEnvParams(envString);
                        ml.setGeometryType(GeoJSONUtilities.POINT); // for the sizechooser

                        ml.setBlueVal(b);
                        ml.setGreenVal(g);
                        ml.setRedVal(r);
                        ml.setSizeVal(3);
                        ml.setOpacity(opacity);

                        ml.setClustered(false);

                        MapLayerMetadata md = ml.getMapLayerMetadata();
                        if (md == null) {
                            md = new MapLayerMetadata();
                            ml.setMapLayerMetadata(md);
                        }

                        ml.setData("query", query);

                        updateLayerControls();

                        //create highlight layer
                        MapLayer mlHighlight = (MapLayer) ml.clone();
                        mlHighlight.setName(ml.getName() + "_highlight");
                        ml.addChild(mlHighlight);

                        return ml;
                    } else {
                        // fail
                        //hide error, might be clustering zoom in;  showMessage("No mappable features available");
                        logger.info("adding WMS layer failed ");
                    }
                } else {
                    //need to cleanup any additional scripts outstanding
                    openLayersJavascript.useAdditionalScript();

                    // fail
                    //showMessage("GeoJSON layer already exists");
                    logger.info(
                            "refusing to add a new layer with URI " + uri
                            + " because it already exists in the menu");
                }
            }
        } catch (Exception ex) {
            System.out.println("error mapSpeciesByNameRank:");
            ex.printStackTrace(System.out);
        }
        return null;
    }

    public LeftMenuSearchComposer getLeftmenuSearchComposer() {
        return (LeftMenuSearchComposer) getFellow("leftMenuSearch").getFellow("leftSearch");
    }

    public DesktopState getDesktopState() {
        return desktopState;
    }

    public void setDesktopState(DesktopState desktopState) {
        this.desktopState = desktopState;
    }

    public Iframe getRawMessageIframeHack() {
        return rawMessageIframeHack;
    }

    public void setRawMessageIframeHack(Iframe rawMessageIframeHack) {
        this.rawMessageIframeHack = rawMessageIframeHack;
    }

    public Div getRawMessageHackHolder() {
        return rawMessageHackHolder;
    }

    public void setRawMessageHackHolder(Div rawMessageHackHolder) {
        this.rawMessageHackHolder = rawMessageHackHolder;
    }

    public LanguagePack getLanguagePack() {
        return languagePack;
    }

    public void setLanguagePack(LanguagePack languagePack) {
        this.languagePack = languagePack;
    }

    public MOTD getMotd() {
        return motd;
    }

    public void setMotd(MOTD motd) {
        this.motd = motd;
    }

    public SettingsSupplementary getSettingsSupplementary() {
        return settingsSupplementary;
    }

    public void setSettingsSupplementary(SettingsSupplementary settingsSupplementary) {
        this.settingsSupplementary = settingsSupplementary;
    }

    public OpenLayersJavascript getOpenLayersJavascript() {
        return openLayersJavascript;
    }

    public void setOpenLayersJavascript(OpenLayersJavascript openLayersJavascript) {
        this.openLayersJavascript = openLayersJavascript;
    }

    public HttpConnection getHttpConnection() {
        return httpConnection;
    }

    public void setHttpConnection(HttpConnection httpConnection) {
        this.httpConnection = httpConnection;
    }

    public RemoteMap getRemoteMap() {
        return remoteMap;
    }

    public void setRemoteMap(RemoteMap remoteMap) {
        this.remoteMap = remoteMap;
    }

    public ActiveLayerRenderer getActiveLayerRenderer() {
        return activeLayerRenderer;
    }

    public void setActiveLayerRenderer(ActiveLayerRenderer activeLayerRenderer) {
        this.activeLayerRenderer = activeLayerRenderer;
    }

    public PortalSessionUtilities getPortalSessionUtilities() {
        return portalSessionUtilities;
    }

    public void setPortalSessionUtilities(PortalSessionUtilities portalSessionUtilities) {
        this.portalSessionUtilities = portalSessionUtilities;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public boolean isLayerControlVisible() {
        return layerControls.isVisible();
    }

    SessionPrint print(String header, double grid, String format, int resolution, boolean preview) {
        //tbxPrintHack is 'screen width, screen height, map extents'
        String p = tbxPrintHack;
        System.out.println("tbxPrintHack:" + p);
        String[] ps = p.split(",");

        String server;
        server = settingsSupplementary.getValue("print_server_url");


        //session id/cookie JSESSIONID=
        String jsessionid = "";
        try {
            //get cookie
            for (Cookie c : ((HttpServletRequest) Executions.getCurrent().getNativeRequest()).getCookies()) {
                if (c.getName().equalsIgnoreCase("JSESSIONID")) {
                    System.out.println("printcookie:" + c.getValue());
                    jsessionid = c.getValue();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //width
        String width = "1024"; //default
        if (ps.length > 1) {
            width = ps[0];
        }

        //height
        String height = "800"; //default
        if (ps.length > 2) {
            height = ps[1];
        }

        //zoom (minlong, minlat, maxlong, maxlat)
        String zoom = "112,-44,154,-9"; //default
        if (ps.length > 5) {
            zoom = ps[2] + "," + ps[3] + "," + ps[4] + "," + ps[5];
        }

        //lhs panel width
        //append to zoom for now > String lhsWidth = "0"; //default
        if (ps.length > 6) {
            zoom += "," + ps[6]; //lhsWidth = ps[6];
        } else {
            zoom += ",350"; //default
        }
        //base map type
        if (ps.length > 7) {
            zoom += "," + ps[7];
        } else {
            zoom += ",normal";
        }


        //unique id
        String uid = String.valueOf(System.currentTimeMillis());

        String pth = this.settingsSupplementary.getValue("print_output_path");

        String htmlpth = pth;
        String htmlurl = settingsSupplementary.getValue("print_output_url");

        try {
            SessionPrint pp = new SessionPrint(server, height, width, htmlpth, htmlurl, uid, jsessionid, zoom, header, grid, format, resolution, this);

            if (!preview) {
                pp.print();

                File f = new File(pp.getImageFilename());
                System.out.println("img (" + pp.getImageFilename() + ") exists: " + f.exists());

                if (format.equalsIgnoreCase("png")) {
                    Filedownload.save(new File(pp.getImageFilename()), "image/png");
                } else if (format.equalsIgnoreCase("pdf")) {
                    Filedownload.save(new File(pp.getImageFilename()), "application/pdf");
                } else {
                    Filedownload.save(new File(pp.getImageFilename()), "image/jpeg");
                }
            }


            StringBuffer sbParams = new StringBuffer();
            sbParams.append("header: " + header);
            sbParams.append("grid: " + grid);
            sbParams.append("format: " + format);
            sbParams.append("resolution: " + resolution);
            sbParams.append("preview: " + preview);
            Map attrs = new HashMap();
            attrs.put("actionby", "user");
            attrs.put("actiontype", "print");
            attrs.put("lsid", "");
            attrs.put("useremail", "spatialuser");
            attrs.put("processid", "");
            attrs.put("sessionid", jsessionid);
            attrs.put("layers", "");
            attrs.put("method", "print");
            attrs.put("params", sbParams.toString());
            attrs.put("downloadfile", pp.getImageFilename());
            updateUserLog(attrs, "print");

            return pp;
        } catch (Exception e) {
            e.printStackTrace();
        }

        showMessage("Error generating export");

        return null;
    }

    /*
     * for Events.echoEvent("openUrl",mapComposer,url as string)
     */
    public void openUrl(Event event) {
        String s = (String) event.getData();

        System.out.println("\n\n******\n\ns: " + s + "\n\n******\n\n");

        String url = "";
        String header = "";
        String download = "";
        String[] data = s.split("\n");
        if (!ArrayUtils.isEmpty(data)) {

            System.out.println("data.length: " + data.length);

            if (data.length == 1) {
                url = data[0];
            }
            if (data.length == 2) {
                url = data[0];
                header = data[1];

                // now the 'header' might be a 'header' or 'download link'
                if (header.startsWith("pid:")) {
                    download = header;
                    header = "";
                }
            }
            if (data.length == 3) {
                url = data[0];
                header = data[1];
                download = data[2];
            }

            if (download.length() > 0) {
                download = download.substring(4);
            }

            activateLink(url, header, false, download);
        }

    }

    /*
     * for Events.echoEvent("openHTML",mapComposer,url as string)
     *
     * first line of String input is header
     */
    public void openHTML(Event event) {
        String html = (String) event.getData();
        String[] lines = html.split("\n");
        String header = lines[0];
        String url = "*" + html.substring(header.length()).trim();
        activateLink(url, header, false);
    }

    public String getViewArea() {
        //default to: current view to be dynamically returned on usage
        BoundingBox bb = getMapComposer().getLeftmenuSearchComposer().getViewportBoundingBox();

        String wkt = "POLYGON(("
                + bb.getMinLongitude() + " " + bb.getMinLatitude() + ","
                + bb.getMinLongitude() + " " + bb.getMaxLatitude() + ","
                + bb.getMaxLongitude() + " " + bb.getMaxLatitude() + ","
                + bb.getMaxLongitude() + " " + bb.getMinLatitude() + ","
                + bb.getMinLongitude() + " " + bb.getMinLatitude() + "))";

        return wkt;
    }

    public Session getSession() {
        return Sessions.getCurrent();
    }

    public int getMapZoom() {
        return mapZoomLevel;
    }

    public void addToSession(String species, String filter) {
        Map speciesfilters = (Map) getSession().getAttribute("speciesfilters");
        if (speciesfilters == null) {
            speciesfilters = new HashMap();
        }
        speciesfilters.put(species, filter);
        getSession().setAttribute("speciesfilters", speciesfilters);
    }

    public void removeFromSession(String species) {
        Map speciesfilters = (Map) Sessions.getCurrent().getAttribute("speciesfilters");
        if (speciesfilters != null) {
            speciesfilters.remove(species);
            getSession().setAttribute("speciesfilters", speciesfilters);
        }
    }

    public void updateUserLogMapSpecies(String lsid) {
        Map attrs = new HashMap();
        attrs.put("actionby", "user");
        attrs.put("actiontype", "map");
        attrs.put("lsid", lsid);
        attrs.put("useremail", "spatialuser");
        attrs.put("processid", "");
        attrs.put("sessionid", "");
        attrs.put("layers", "");
        attrs.put("method", "");
        attrs.put("params", "");
        attrs.put("downloadfile", "");
        updateUserLog(attrs, "species");
    }

    public void updateUserLogMapLayer(String layers) {
        updateUserLogMapLayer("", layers);
    }

    public void updateUserLogMapLayer(String type, String layers) {
        Map attrs = new HashMap();
        attrs.put("actionby", "user");
        attrs.put("actiontype", "map");
        attrs.put("lsid", "");
        attrs.put("useremail", "spatialuser");
        attrs.put("processid", "");
        attrs.put("sessionid", "");
        attrs.put("layers", layers);
        attrs.put("method", "");
        attrs.put("params", "");
        attrs.put("downloadfile", "");
        updateUserLog(attrs, "layer: " + type);
    }

    public void updateUserLogAnalysis(String method, String params) {
        updateUserLogAnalysis(method, params, "");
    }

    public void updateUserLogAnalysis(String method, String params, String layers) {
        updateUserLogAnalysis(method, params, layers, "");
    }

    public void updateUserLogAnalysis(String method, String params, String layers, String msg) {
        updateUserLogAnalysis(method, params, layers, "", "", msg);
    }

    public void updateUserLogAnalysis(String method, String params, String layers, String download, String pid, String msg) {
        Map attrs = new HashMap();
        attrs.put("actionby", "user");
        attrs.put("actiontype", "analysis");
        attrs.put("lsid", "");
        attrs.put("useremail", "spatialuser");
        attrs.put("processid", pid);
        attrs.put("sessionid", "");
        attrs.put("layers", layers);
        attrs.put("method", method);
        attrs.put("params", params);
        attrs.put("downloadfile", download);
        updateUserLog(attrs, "analysis: " + msg);
    }

    public void updateUserLog(Map attrs) {
        updateUserLog(attrs, "");
    }

    public void updateUserLog(Map attrs, String msg) {
        Iterator it = attrs.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            if (key != null && attrs != null && attrs.get(key) != null) {
                MDC.put(key, attrs.get(key));
            }
        }
        //MDC.put("userip", Executions.getCurrent().getRemoteAddr());
        String userip = Executions.getCurrent().getHeader("x-forwarded-for");
        if (StringUtils.isBlank(userip)) {
            userip = "";
        }
        MDC.put("userip", userip);

        logger.info(msg);
        MDC.clear();
    }

    /**
     * get Active Area as WKT string, from a layer name
     *
     * @param layer name of layer as String
     * @param register_shape true to register the shape with alaspatial shape register
     * @return
     */
    String getLayerGeoJsonAsWkt(String layer) {
        try {
            //class_name is same as layer name
            return Util.wktFromJSON(getMapLayer(layer).getGeoJSON());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getViewArea();
    }

    public void loadScatterplot(ScatterplotData data, String lyrName) {
        MapLayer ml = mapSpecies(data.getQuery(), data.getSpeciesName(), "species", 0, LayerUtilities.SCATTERPLOT, null, 0);
        ml.setDisplayName(lyrName);
        ml.setSubType(LayerUtilities.SCATTERPLOT);
        ml.setData("scatterplotData", data);
        addUserDefinedLayerToMenu(ml, true);
        updateLayerControls();
    }
    /*
     * remove it + map it
     */

    public MapLayer activateLayerForScatterplot(ScatterplotData data, String rank) {
        List udl = getMapComposer().getPortalSession().getActiveLayers();
        Iterator iudl = udl.iterator();
        MapLayer mapLayer = null;
        boolean unselectLayer = false;
        boolean remapLayer = true;

        //find layer matching data.getLsid()
        while (iudl.hasNext()) {
            MapLayer ml = (MapLayer) iudl.next();
            MapLayerMetadata md = ml.getMapLayerMetadata();
            if (ml.isSpeciesLayer()
                    && ml.getData("query").equals(data.getQuery().toString())) {

                mapLayer = ml;

                if (ml.isClustered() || !ml.isDisplayed()) {
                    //removeLayer;
                    openLayersJavascript.setAdditionalScript(openLayersJavascript.iFrameReferences
                            + openLayersJavascript.removeMapLayer(ml));
                    unselectLayer = true;
                } else {
                    remapLayer = false;
                }

                break;
            }
        }

        if (unselectLayer && mapLayer != null) {
            try {
                deactiveLayer(mapLayer, true, false, true);
            } catch (Exception e) {
            }
        }

        if (remapLayer) {
            //map as WMS points layer
            mapLayer = mapSpecies(data.getQuery(), data.getSpeciesName(),
                    (mapLayer != null && mapLayer.getMapLayerMetadata() != null) ? mapLayer.getMapLayerMetadata().getSpeciesRank() : "species",
                    (mapLayer != null && mapLayer.getMapLayerMetadata() != null) ? mapLayer.getMapLayerMetadata().getOccurrencesCount() : 0,
                    LayerUtilities.SPECIES, null, -1);
            if (mapLayer != null) {
                MapLayerMetadata md = mapLayer.getMapLayerMetadata();
                if (md == null) {
                    md = new MapLayerMetadata();
                    mapLayer.setMapLayerMetadata(md);
                }
                md.setSpeciesDisplayName(data.getSpeciesName());
                md.setSpeciesRank(rank);

                updateUserLogMapSpecies(data.getQuery().getQ());
            }
        }

        // reopen the layer controls for this layer
        try {
            refreshActiveLayer(mapLayer);

            for (int i = 0; i < activeLayersList.getItemCount(); i++) {
                Listitem item = (Listitem) activeLayersList.getItemAtIndex(i);
                if (mapLayer == (MapLayer) item.getValue()) {
                    activeLayersList.setSelectedIndex(i);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mapLayer;
    }

    /*
     * remove it + map it
     */
    public void removeLayerHighlight(ScatterplotData data, String rank) {
        List udl = getMapComposer().getPortalSession().getActiveLayers();
        Iterator iudl = udl.iterator();
        MapLayer mapLayer = null;
        while (iudl.hasNext()) {
            MapLayer ml = (MapLayer) iudl.next();
            MapLayerMetadata md = ml.getMapLayerMetadata();
            if (ml.isSpeciesLayer()
                    && ml.getData("query").toString().equals(data.getQuery().toString())
                    && ml.getHighlight() != null) {

                ml.setHighlight(null);

                if (!ml.isClustered() && ml.isDisplayed()) {
                    applyChange(ml);
                }

                break;
            }
        }
    }

    public West getMenus() {
        return menus;
    }

    public String getNextAreaLayerName(String layerPrefix) {
        if (getMapLayer(layerPrefix) == null && getMapLayerDisplayName(layerPrefix) == null) {
            return layerPrefix;
        }

        layerPrefix += " ";
        int i = 1;
        while (getMapLayer(layerPrefix + i) != null
                || getMapLayerDisplayName(layerPrefix + i) != null) {
            i++;
        }
        return layerPrefix + i;
    }

    public String getNextActiveAreaLayerName(String areaName) {
        if (areaName == null) {
            areaName = "Active area";
        } else if (areaName.trim().equals("")) {
            areaName = "Active area";
        }
        String layerPrefix = "Occurrences in " + areaName + " ";

        return layerPrefix;
    }

    public void onClick$btnAddSpecies(Event event) {
        openModal("WEB-INF/zul/AddSpecies.zul", null);
    }

    public void onClick$btnAddArea(Event event) {
        openModal("WEB-INF/zul/AddArea.zul", null);
    }

    public void onClick$btnAddLayer(Event event) {
        openModal("WEB-INF/zul/AddLayer.zul", null);
    }

    public void onClick$btnAddMaxent(Event event) {
        openModal("WEB-INF/zul/AddToolMaxent.zul", null);
    }

    public void onClick$btnAddSampling(Event event) {
        openModal("WEB-INF/zul/AddToolSampling.zul", null);
    }

    public void onClick$btnAddAloc(Event event) {
        openModal("WEB-INF/zul/AddToolALOC.zul", null);
    }

    public void onClick$btnAddScatterplot(Event event) {
        openModal("WEB-INF/zul/AddToolScatterplot.zul", null);
    }

    public void onClick$btnAreaReport(Event event) {
        openModal("WEB-INF/zul/AddToolAreaReport.zul", null);
    }

    public void onClick$btnSpeciesList(Event event) {
        openModal("WEB-INF/zul/AddToolSpeciesList.zul", null);
    }

    public Window openModal(String page, Hashtable<String, Object> params) {
        Window window = (Window) Executions.createComponents(page, this, params);

        try {
            window.doModal();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return window;
    }

    void openOverlapped(String page) {
        Window window = (Window) Executions.createComponents(page, this, null);
        try {
            window.doOverlapped();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public GenericServiceAndBaseLayerSupport getGenericServiceAndBaseLayerSupport() {
        return genericServiceAndBaseLayerSupport;
    }

    public void updateLayerControls() {
        //remove any scatterplot legend
        Component c = getFellowIfAny("scatterplotlayerlegend");
        if (c != null) {
            c.detach();
        }

        //remove children
        for (int i = layerControls.getChildren().size() - 1; i >= 0; i--) {
            try {
                ((Component) layerControls.getChildren().get(i)).detach();
            } catch (Exception e) {
            }
        }

        MapLayer selectedLayer = this.getActiveLayersSelection(false);
        if (selectedLayer == null) {
            if (activeLayersList.getItemCount() > 0) {
                activeLayersList.setSelectedIndex(0);
                selectedLayer = (MapLayer) activeLayersList.getModel().getElementAt(0);
            } else {
                return;
            }
        }

        lblSelectedLayer.setValue(selectedLayer.getDisplayName());

        String page = "";
        Window window = null;
        switch (selectedLayer.getType()) {
            case LayerUtilities.MAP:
                page = "WEB-INF/zul/MapSettings.zul";
                break;
            case LayerUtilities.SCATTERPLOT:
                page = "WEB-INF/zul/Scatterplot.zul";
                break;
            case LayerUtilities.TABULATION:
                page = "WEB-INF/zul/AnalysisTabulation.zul";
                break;
            default:
                if (selectedLayer.getSubType() == LayerUtilities.SCATTERPLOT) {
                    page = "WEB-INF/zul/Scatterplot.zul";
                } else {
                    showLayerDefault(selectedLayer);
                    return;
                }
        }

        window = (Window) Executions.createComponents(page, layerControls, null);
        try {
            ((HasMapLayer) window).setMapLayer(selectedLayer);
        } catch (Exception e) {
        }
        try {
            window.doEmbedded();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    LayerLegendComposer2 llc2;
    public MapLayer llc2MapLayer;

    void showLayerDefault(MapLayer ml) {
        if (ml.getType() == LayerUtilities.MAP) {
            Window window = (Window) Executions.createComponents("WEB-INF/zul/MapOptions.zul", layerControls, null);
            try {
                window.doEmbedded();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        llc2MapLayer = ml;
        llc2 = (LayerLegendComposer2) Executions.createComponents("WEB-INF/zul/LayerLegend2.zul", layerControls, null);
        llc2.init(
                ml,
                (Query) ml.getData("query"),
                ml.getRedVal(),
                ml.getGreenVal(),
                ml.getBlueVal(),
                ml.getSizeVal(),
                (int) (ml.getOpacity() * 100),
                ml.getColourMode(),
                (ml.getColourMode().equals("grid")) ? 0 : ((ml.isClustered()) ? 1 : 2),
                ml.getSizeUncertain(),
                new EventListener() {

                    @Override
                    public void onEvent(Event event) throws Exception {
                        updateFromLegend();
                    }
                });

        try {
            llc2.doEmbedded();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void updateFromLegend() {
        MapLayer ml = llc2MapLayer;

        //layer on map settings
        if (llc2.getRed() != ml.getRedVal()
                || llc2.getGreen() != ml.getGreenVal()
                || llc2.getBlue() != ml.getBlueVal()
                || llc2.getSize() != ml.getSizeVal()
                || llc2.getOpacity() != (int) (ml.getOpacity() * 100)
                || (ml.getColourMode() != null && !ml.getColourMode().equals(llc2.getColourMode()))
                || (ml.isClustered() && llc2.getPointType() != 1)
                || ml.getSizeUncertain() != llc2.getUncertainty()) {

            ml.setRedVal(llc2.getRed());
            ml.setGreenVal(llc2.getGreen());
            ml.setBlueVal(llc2.getBlue());
            ml.setSizeVal(llc2.getSize());
            ml.setOpacity(llc2.getOpacity() / 100.0f);
            ml.setColourMode(llc2.getColourMode());
            ml.setClustered(llc2.getPointType() == 0);
            ml.setSizeUncertain(llc2.getUncertainty());

            applyChange(ml);
        }

        //layer in menu settings
        if (!ml.getDisplayName().equals(llc2.getDisplayName())) {
            ml.setDisplayName(llc2.getDisplayName());

            //selection label
            lblSelectedLayer.setValue(llc2.getDisplayName());

            redrawLayersList();
        }
    }

    public void redrawLayersList() {
        int idx = activeLayersList.getSelectedIndex();
        List<MapLayer> activeLayers = getPortalSession().getActiveLayers();
        activeLayersList.setModel(new ListModelList(activeLayers, true));
        adjustActiveLayersList();
        activeLayersList.setSelectedIndex(idx);
        llc2.txtLayerName.setValue(llc2MapLayer.getDisplayName());
        lblSelectedLayer.setValue(llc2MapLayer.getDisplayName());
    }

    public void onSelect$activeLayersList(Event event) {
        updateLayerControls();

        refreshContextualMenu();
    }

    public List<MapLayer> getPolygonLayers() {
        ArrayList<MapLayer> list = new ArrayList<MapLayer>();
        List<MapLayer> allLayers = getPortalSession().getActiveLayers();
        for (int i = 0; i < allLayers.size(); i++) {
            if (allLayers.get(i).isPolygonLayer()) {
                list.add(allLayers.get(i));
            }
        }

        return list;
    }

    public List<MapLayer> getSpeciesLayers() {
        ArrayList<MapLayer> list = new ArrayList<MapLayer>();
        List<MapLayer> allLayers = getPortalSession().getActiveLayers();
        for (int i = 0; i < allLayers.size(); i++) {
            if (allLayers.get(i).getData("query") != null){ 
                    //&& allLayers.get(i).getSubType() != LayerUtilities.SCATTERPLOT) {
                list.add(allLayers.get(i));
            }
        }

        return list;
    }

    public List<MapLayer> getGridLayers() {
        ArrayList<MapLayer> list = new ArrayList<MapLayer>();
        List<MapLayer> allLayers = getPortalSession().getActiveLayers();
        for (int i = 0; i < allLayers.size(); i++) {
            if (allLayers.get(i).isGridLayer()
                    && allLayers.get(i).getSubType() != LayerUtilities.MAXENT
                    && allLayers.get(i).getSubType() != LayerUtilities.ALOC) {
                list.add(allLayers.get(i));
            }
        }

        return list;
    }

    public List<MapLayer> getContextualLayers() {
        ArrayList<MapLayer> list = new ArrayList<MapLayer>();
        List<MapLayer> allLayers = getPortalSession().getActiveLayers();
        for (int i = 0; i < allLayers.size(); i++) {
            if (allLayers.get(i).isContextualLayer()
                    && allLayers.get(i).getSubType() != LayerUtilities.ALOC) {
                list.add(allLayers.get(i));
            }
        }

        return list;
    }

    public boolean isSelectedLayer(MapLayer ml) {
        MapLayer selectedLayer = getActiveLayersSelection(false);
        return selectedLayer == ml;
    }

    public void refreshContextualMenu() {
        ((ContextualMenu) contextualMenu.getFellow("contextualMenuWindow")).refresh();
    }

    /**
     * Searches the occurrences at a given point and then maps the polygon feature
     * found at the location (for the current top contextual layer).
     * @param event triggered by the usual javascript trickery
     */
//    public void onSearchSpeciesPoint(Event event) {
//    }
    public void exportArea(Event event) {
        openModal("WEB-INF/zul/ExportLayer.zul", null);
    }

//    public void exportAreaAs(String type) {
//        MapLayer ml = llc2MapLayer;
//        if (ml.isPolygonLayer() && ml.getSubType() != LayerUtilities.ENVIRONMENTAL_ENVELOPE) {
//            exportAreaAs(type, "", null);
//        } else {
//            //Messagebox.show("The selected layer is not an area. Please select an appropriate layer to export", "Export layer", Messagebox.OK, Messagebox.EXCLAMATION);
//        }
//
//    }
    public void exportAreaAs(String type, String name, SelectedArea sa) {
        String EXPORT_BASE_DIR = "/data/ala/runtime/output/export/";
        try {
            String id = String.valueOf(System.currentTimeMillis());

            File shpDir = new File(EXPORT_BASE_DIR + id + File.separator);
            shpDir.mkdirs();

            String contentType = LayersUtil.LAYER_TYPE_ZIP;
            //String outfile = ml.getDisplayName().replaceAll(" ", "_")+("shp".equals(type)?"Shapefile":type.toUpperCase())+".zip";
            String outfile = name.replaceAll(" ", "_");
            if ("shp".equals(type)) {
                File shpfile = new File(EXPORT_BASE_DIR + id + File.separator + outfile + "_Shapefile.shp");
                ShapefileUtils.saveShapefile(shpfile, sa.getWkt());
                //contentType = LayersUtil.LAYER_TYPE_ZIP;
                outfile += "_Shapefile.zip";
            } else if ("kml".equals(type)) {

                StringBuffer sbKml = new StringBuffer();
                sbKml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append("\r");
                sbKml.append("<kml xmlns=\"http://earth.google.com/kml/2.2\">").append("\r");
                sbKml.append("<Document>").append("\r");
                sbKml.append("  <name>Spatial Portal Active Area</name>").append("\r");
                sbKml.append("  <description><![CDATA[Active area saved from the ALA Spatial Portal: http://spatial.ala.org.au/]]></description>").append("\r");
                sbKml.append("  <Style id=\"style1\">").append("\r");
                sbKml.append("    <LineStyle>").append("\r");
                sbKml.append("      <color>40000000</color>").append("\r");
                sbKml.append("      <width>3</width>").append("\r");
                sbKml.append("    </LineStyle>").append("\r");
                sbKml.append("    <PolyStyle>").append("\r");
                sbKml.append("      <color>73FF0000</color>").append("\r");
                sbKml.append("      <fill>1</fill>").append("\r");
                sbKml.append("      <outline>1</outline>").append("\r");
                sbKml.append("    </PolyStyle>").append("\r");
                sbKml.append("  </Style>").append("\r");
                sbKml.append("  <Placemark>").append("\r");
                sbKml.append("    <name>").append(name).append("</name>").append("\r");
                sbKml.append("    <description><![CDATA[<div dir=\"ltr\">").append(name).append("<br></div>]]></description>").append("\r");
                sbKml.append("    <styleUrl>#style1</styleUrl>").append("\r");

                //Remove first line of kmlGeometry, <?xml...>
                Geometry geom = new WKTReader().read(sa.getWkt());
                Encoder encoder = new Encoder(new KMLConfiguration());
                encoder.setIndenting(true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                encoder.encode(geom, KML.Geometry, baos);
                String kmlGeometry = new String(baos.toByteArray());
                sbKml.append(kmlGeometry.substring(kmlGeometry.indexOf('\n')));

                sbKml.append("  </Placemark>").append("\r");
                sbKml.append("</Document>").append("\r");
                sbKml.append("</kml>").append("\r");

                File shpfile = new File(EXPORT_BASE_DIR + id + File.separator + outfile + "_KML.kml");
                BufferedWriter wout = new BufferedWriter(new FileWriter(shpfile));
                wout.write(sbKml.toString());
                wout.close();
                //contentType = LayersUtil.LAYER_TYPE_KML;
                outfile += "_KML.zip";
            } else if ("wkt".equals(type)) {
                File shpfile = new File(EXPORT_BASE_DIR + id + File.separator + outfile + "_WKT.txt");
                BufferedWriter wout = new BufferedWriter(new FileWriter(shpfile));
                wout.write(sa.getWkt());
                wout.close();
                //contentType = LayersUtil.LAYER_TYPE_PLAIN;
                outfile += "_WKT.zip";
            }

            String downloadUrl = CommonData.satServer;
            downloadUrl += "/ws/download/" + id;
            Filedownload.save(new URL(downloadUrl).openStream(), contentType, outfile);
        } catch (Exception e) {
            System.out.println("Unable to export user area");
            e.printStackTrace(System.out);
        }
    }

    public void onBaseMap(Event event) {
        String newBaseMap = (String) event.getData();
        getPortalSession().setBaseLayer(newBaseMap);

    }

    public String getBaseMap() {
        return getPortalSession().getBaseLayer();
    }

    void adjustActiveLayersList() {
        if (activeLayersList != null && activeLayersList.getItems() != null) {
            for (Listitem li : (List<Listitem>) activeLayersList.getItems()) {
                if (li.getValue() != null
                        && ((MapLayer) li.getValue()).getName().equals("Map options")) {
                    li.setDraggable("false");
                    li.setDroppable("false");
                }
            }
        }
    }

    public void saveSession() {
        String id = PortalSessionIO.writePortalSession(getPortalSession(), settingsSupplementary.getValue("session_path"), null);
        showMessage("Saved session: " + settingsSupplementary.getValue("print_server_url")
                + "?session=" + id);
    }

    public void importAnalysis(Event event) {
        openModal("WEB-INF/zul/ImportAnalysis.zul", null);
    }
    static int currentColourIdx = 0;

    public int nextColour() {
        int colour = LegendObject.colours[currentColourIdx % LegendObject.colours.length];

        currentColourIdx++;

        return colour;
    }

    private List<Entry<String, String>> getQueryParameters(String params) {
        if (params == null || params.length() == 0) {
            return null;
        }

        ArrayList<Entry<String, String>> list = new ArrayList<Entry<String, String>>();
        for (String s : params.split("&")) {
            String[] keyvalue = s.split("=");
            if (keyvalue.length >= 2) {
                String key = keyvalue[0];
                String value = keyvalue[1];
                try {
                    value = URLDecoder.decode(value, "UTF-8");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                list.add(new HashMap.SimpleEntry<String, String>(key, value));
            }
        }

        return list;
    }

    public ArrayList<LayerSelection> getLayerSelections() {
        return selectedLayers;
    }

    public void addLayerSelection(LayerSelection ls) {
        //check for duplication
        for (int i = 0; i < selectedLayers.size(); i++) {
            if (selectedLayers.get(i).equalsList(ls)) {
                selectedLayers.get(i).setLastUse(System.currentTimeMillis());
                return;
            }
        }

        //add
        selectedLayers.add(ls);

        //if more than 10, remove the recod with oldest use
        if (selectedLayers.size() > 20) {
            int oldestIdx = 0;
            for (int i = 1; i < selectedLayers.size(); i++) {
                if (selectedLayers.get(i).getLastUse()
                        < selectedLayers.get(oldestIdx).getLastUse()) {
                    oldestIdx = i;
                }
            }
            selectedLayers.remove(oldestIdx);
        }
    }
}
