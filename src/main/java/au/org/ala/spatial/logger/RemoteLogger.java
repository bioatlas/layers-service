package au.org.ala.spatial.logger;

import au.org.ala.spatial.util.CommonData;
import au.org.ala.spatial.util.Util;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jasig.cas.client.authentication.AttributePrincipal;
import org.springframework.beans.factory.annotation.Required;
import org.zkoss.lang.Threads;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Properties;

/**
 * Helper class to send logging information to the logger-service
 *
 * @author ajay
 */
public class RemoteLogger {

    private static Logger logger = Logger.getLogger(RemoteLogger.class);

    String logger_service = "";
    String appid = "";

    private void init() {
        logger_service = CommonData.settings.getProperty("logging_url");
        appid = CommonData.settings.getProperty("app_id");
    }

    public void logMapSpecies(String name, String lsid, String area, String extra) {
        logMapSpecies(name, lsid, area, "Species", extra);
    }

    public void logMapSpecies(String name, String lsid, String area, String type, String extra) {
        sendToServer(type, name, lsid, area, "", extra, "mapped", "0", "");
    }

    public void logMapArea(String name, String type, String area) {
        logMapArea(name, type, area, "");
    }

    public void logMapArea(String name, String type, String area, String extra) {
        logMapArea(name, type, area, "", "");
    }

    public void logMapArea(String name, String type, String area, String layer, String extra) {
        sendToServer(type, name, "", area, layer, extra, "mapped", "0", "");
    }

    public void logMapAnalysis(String name, String type, String area, String species, String layers, String pid, String options, String status) {
        sendToServer(type, name, species, area, layers, options, status, "0", pid);
    }

    public void logMapAnalysisUpdateStatus(String pid, String status) {
        sendToServer(pid, status);
    }

    private int sendToServer(String type, String name, String lsid, String area, String layers, String extra, String status, String privacy, String pid) {
        try {
//            StringBuffer sbProcessUrl = new StringBuffer();
//            sbProcessUrl.append(logger_service);
//            sbProcessUrl.append("/log/action?").append("?");
//            sbProcessUrl.append("email=guest@ala.org.au").append("&");
//            sbProcessUrl.append("appid=").append(appid).append("&");
//            sbProcessUrl.append("userip=").append(userip).append("&");
//            sbProcessUrl.append("type=").append(type).append("&");
//            sbProcessUrl.append("name=").append(name).append("&");
//            sbProcessUrl.append("specieslsid=").append(lsid).append("&");
//            //sbProcessUrl.append("area=").append(area).append("&");
//            sbProcessUrl.append("status=mapped").append("&");
//            sbProcessUrl.append("privacy=0");

            if (StringUtils.isBlank(logger_service)) {
                init();
            }

            String sessionid = ((HttpSession) Sessions.getCurrent().getNativeSession()).getId();

            String userip = Executions.getCurrent().getHeader("x-forwarded-for");
            if (StringUtils.isBlank(userip)) {
                userip = Executions.getCurrent().getRemoteAddr();
                if (StringUtils.isBlank(userip)) {
                    userip = "";
                }
            }

            String useremail = Util.getUserEmail();

            logger.debug("Sending log to: " + logger_service + "/log/action");
            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod(logger_service + "/log/action");
            post.addRequestHeader("Accept", "application/json");

            String category1 = "", category2 = "";
            String[] types = type.split("-");
            category1 = StringUtils.capitalize(types[0].trim());
            if (types.length > 1) {
                category2 = StringUtils.capitalize(types[1].trim());
            }

            if (StringUtils.isBlank(lsid)) {
                lsid = "";
            }
            if (StringUtils.isBlank(pid)) {
                pid = "";
            }
            if (StringUtils.isBlank(area)) {
                area = "";
            }

            post.addParameter("email", useremail);
            post.addParameter("appid", appid);
            post.addParameter("userip", userip);
            post.addParameter("sessionid", sessionid);
            post.addParameter("type", type);
            post.addParameter("category1", category1);
            post.addParameter("category2", category2);
            post.addParameter("name", name);
            post.addParameter("processid", pid);
            post.addParameter("specieslsid", lsid);
            post.addParameter("layers", layers);
            post.addParameter("status", status);
            post.addParameter("privacy", privacy);
            post.addParameter("area", area);
            post.addParameter("extra", extra);

            logger.debug("logging " + type + " action for user session " + sessionid + " for user " + useremail + " from " + userip);
            return client.executeMethod(post);

        } catch (Exception e) {
            logger.error("Error sending logging information to server:", e);
        }

        return -1;
    }

    private int sendToServer(String pid, String status) {
        try {

            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(logger_service + "/log/update/" + pid + "/" + status);
            get.addRequestHeader("Accept", "application/json");

            logger.debug("logging status update on " + pid);
            return client.executeMethod(get);

        } catch (Exception e) {
            logger.error("Error sending logging information to server:", e);
        }

        return -1;
    }

    public JSONObject getLogCSV() {
        init();

        try {
            if (Util.isLoggedIn()) {
                String url = logger_service + "/app/types/tool.json?"
                        + "email=" + URLEncoder.encode(Util.getUserEmail(), "UTF-8")
                        + "&appid=" + URLEncoder.encode(appid, "UTF-8");

                HttpClient client = new HttpClient();
                GetMethod get = new GetMethod(url);

                get.addRequestHeader("Accept", "application/json");

                client.executeMethod(get);

                logger.debug("get: " + url + ", response: " + get.getResponseBodyAsString());

                return JSONObject.fromObject(get.getResponseBodyAsString());
            }
        } catch (Exception e) {
            logger.error("Error getting logging information from server:", e);
        }

        return null;
    }

    public JSONObject getLogItem(String logId) {
        init();

        try {

            if (Util.isLoggedIn()) {
                String url = logger_service + "/log/view.json?"
                        + "id=" + logId;
                //+ "&email=" + URLEncoder.encode(Util.getUserEmail(), "UTF-8")
                //+ "&appid=" + URLEncoder.encode(appid,"UTF-8");


                HttpClient client = new HttpClient();
                GetMethod get = new GetMethod(url);

                get.addRequestHeader("Accept", "application/json");

                client.executeMethod(get);

                logger.debug("get: " + url + ", response: " + get.getResponseBodyAsString());

                return JSONObject.fromObject(get.getResponseBodyAsString());
            }

        } catch (Exception e) {
            logger.error("Error getting logging information from server:", e);
        }

        return null;
    }
}