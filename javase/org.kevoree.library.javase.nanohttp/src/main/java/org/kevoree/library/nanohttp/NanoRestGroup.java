package org.kevoree.library.nanohttp;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService;
import org.kevoree.framework.AbstractGroupType;
import org.kevoree.framework.Constants;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.javase.nanohttp.NodeNetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 16/02/12
 * Time: 09:37
 */

@DictionaryType({
        @DictionaryAttribute(name = "port", defaultValue = "8000", optional = true, fragmentDependant = true),
        @DictionaryAttribute(name = "ip", defaultValue = "0.0.0.0", optional = true, fragmentDependant = true)
})
@GroupType
@Library(name = "JavaSE", names = "Android")
public class NanoRestGroup extends AbstractGroupType {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private NanoHTTPD server = null;
    //	private ModelSerializer modelSaver = new ModelSerializer();
    private KevoreeModelHandlerService handler = null;
    private boolean starting;

    ExecutorService poolUpdate = Executors.newSingleThreadExecutor();

    @Start
    public void startRestGroup() throws IOException {
        poolUpdate = Executors.newSingleThreadExecutor();
        handler = this.getModelService();
        int port = Integer.parseInt(this.getDictionary().get("port").toString());
        Object addressObject = this.getDictionary().get("ip");
        String address = "0.0.0.0";
        if (addressObject != null) {
            address = addressObject.toString();
        }
        server = new NanoHTTPD(new InetSocketAddress(InetAddress.getByName(address), port)) {
            //        server = new NanoHTTPD(port) {
            @Override
            public Response serve(String uri, String method, Properties header, Properties parms, Properties files, InputStream body) {
                if ("POST".equals(method)) {
                    if (uri.endsWith("/model/current") || uri.endsWith("/model/current/zip")) {
                        try {
                            logger.debug("Model receive, process to load");
                            ContainerRoot model = null;
                            if (uri.endsWith("zip")) {
                                model = KevoreeXmiHelper.loadCompressedStream(body);
                                logger.debug("Load  model From ZIP Stream");
                            } else {
                                model = KevoreeXmiHelper.loadStream(body);
                                logger.debug("Load  model From XMI Stream");
                            }
                            body.close();
                            logger.debug("Model loaded,send to core");
                            String srcNodeName = "";
                            Boolean externalSender = true;
                            Enumeration e = parms.propertyNames();
                            while (e.hasMoreElements()) {
                                String value = (String) e.nextElement();
                                if (value.endsWith("nodesrc")) {
                                    srcNodeName = parms.getProperty(value);
                                }
                            }
                            for (ContainerNode subNode : getModelElement().getSubNodesForJ()) {
                                if (subNode.getName().trim().equals(srcNodeName.trim())) {
                                    externalSender = false;
                                }
                            }

                            //DO NOT NOTIFY ALL WHEN REC FROM THIS GROUP
                            final Boolean finalexternalSender = externalSender;
                            final ContainerRoot finalModel = model;
                            Runnable t = new Runnable() {
                                @Override
                                public void run() {
                                    if (!finalexternalSender) {
                                        getModelService().unregisterModelListener(getModelListener());
                                    }
                                    handler.atomicUpdateModel(finalModel);
                                    if (!finalexternalSender) {
                                        getModelService().registerModelListener(getModelListener());
                                    }
                                }
                            };
                            poolUpdate.submit(t);

                            return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, "<ack nodeName=\"" + getNodeName() + "\" />");
                        } catch (Exception e) {
                            logger.error("Error while loading model ",e);
                            return new NanoHTTPD.Response(HTTP_BADREQUEST, MIME_HTML, "Error while uploading model");
                        }
                    }
                } else if ("GET".equals(method)) {
                    if (uri.endsWith("/model/current")) {
                        String msg = KevoreeXmiHelper.saveToString(handler.getLastModel(), false);
                        return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, msg);
                    }
                    if (uri.endsWith("/model/current/zip")) {
                        ByteArrayOutputStream st = new ByteArrayOutputStream();
                        KevoreeXmiHelper.saveCompressedStream(st, handler.getLastModel());
                        ByteArrayInputStream resultStream = new ByteArrayInputStream(st.toByteArray());
                        return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, resultStream);
                    }
                }
                return new NanoHTTPD.Response(HTTP_BADREQUEST, MIME_XML, "ONLY GET OR POST METHOD SUPPORTED");
            }
        };

        //logger.info("Rest service start on port ->" + port);
        starting = true;

    }

    @Stop
    public void stopRestGroup() {
        poolUpdate.shutdownNow();
        server.stop();
    }

    @Override
    public void triggerModelUpdate() {
        if (starting) {
            final Option<ContainerRoot> modelOption = NodeNetworkHelper.updateModelWithNetworkProperty(this);
            if (modelOption.isDefined()) {
                new Thread() {
                    public void run() {
                        getModelService().unregisterModelListener(getModelListener());
                        getModelService().atomicUpdateModel(modelOption.get());
                        getModelService().registerModelListener(getModelListener());
                    }
                }.start();
            }
            starting = false;
        } else {
            Group group = getModelElement();
            for (ContainerNode subNode : group.getSubNodesForJ()) {
                if (!subNode.getName().equals(this.getNodeName())) {
                    internalPush(getModelService().getLastModel(), subNode.getName(), this.getNodeName());
                }
            }
        }
    }

    @Override
    public void push(ContainerRoot model, String targetNodeName) {
        List<String> ips = KevoreePropertyHelper.getStringNetworkProperties(model, targetNodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
        Option<Integer> portOption = KevoreePropertyHelper.getIntPropertyForGroup(model, this.getName(), "port", true, targetNodeName);
        int PORT = 8000;
        if (portOption.isDefined()) {
            PORT = portOption.get();
        }
        boolean sent = false;
        for (String ip : ips) {
            logger.debug("try to send model on url=>" + "http://" + ip + ":" + PORT + "/model/current");
            if (sendModel(model, "http://" + ip + ":" + PORT + "/model/current")) {
                sent = true;
                break;
            }
        }
        if (!sent) {
            logger.debug("try to send model on url=>" + "http://127.0.0.1:" + PORT + "/model/current");
            if (!sendModel(model, "http://127.0.0.1:" + PORT + "/model/current")) {
                logger.error("Unable to push a model on " + targetNodeName);
            }
        }
    }


    private void internalPush(ContainerRoot model, String targetNodeName, String sender) {
        List<String> ips = KevoreePropertyHelper.getStringNetworkProperties(model, targetNodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
        Option<Integer> portOption = KevoreePropertyHelper.getIntPropertyForGroup(model, this.getName(), "port", true, targetNodeName);
        int PORT = 8000;
        if (portOption.isDefined()) {
            PORT = portOption.get();
        }
        boolean sent = false;
        for (String ip : ips) {
            logger.debug("try to send model on url=>" + "http://" + ip + ":" + PORT + "/model/current?nodesrc=" + sender);
            if (sendModel(model, "http://" + ip + ":" + PORT + "/model/current?nodesrc=" + sender)) {
                sent = true;
                break;
            }
        }
        if (!sent) {
            logger.debug("try to send model on url=>" + "http://127.0.0.1:" + PORT + "/model/current?nodesrc=" + sender);
            if (!sendModel(model, "http://127.0.0.1:" + PORT + "/model/current?nodesrc=" + sender)) {
                logger.debug("Unable to push a model on " + targetNodeName);
            }
        }
    }

    private boolean sendModel(ContainerRoot model, String urlPath) {

        String urlPath2 = urlPath;
        if(urlPath2.contains("?")){
            urlPath2 = urlPath2.replace("?","/zip?");
        } else {
            urlPath2  = urlPath2 + "/zip";
        }
        if (!sendModel(model, urlPath2, true)) {
            return sendModel(model, urlPath, false);
        } else {
            return true;
        }

    }

    private boolean sendModel(ContainerRoot model, String urlPath, Boolean zip) {
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            if (zip) {
                KevoreeXmiHelper.saveCompressedStream(outStream, model);
            } else {
                KevoreeXmiHelper.saveStream(outStream, model);
            }
            outStream.flush();
            logger.debug("Try URL PATH "+urlPath);
            URL url = new URL(urlPath);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setDoOutput(true);
            conn.getOutputStream().write(outStream.toByteArray());
            conn.getOutputStream().close();
            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = rd.readLine();
            while (line != null) {
                line = rd.readLine();
            }
            rd.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    @Override
    public ContainerRoot pull(String targetNodeName) {

        List<String> ips = KevoreePropertyHelper.getStringNetworkProperties(this.getModelService().getLastModel(), targetNodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
        Option<Integer> portOption = KevoreePropertyHelper.getIntPropertyForGroup(this.getModelService().getLastModel(), this.getName(), "port", true, targetNodeName);
        int PORT = 8000;
        if (portOption.isDefined()) {
            PORT = portOption.get();
        }
        for (String ip : ips) {
            logger.debug("try to pull model on url=>" + "http://" + ip + ":" + PORT + "/model/current");
            ContainerRoot model = pullModel("http://" + ip + ":" + PORT + "/model/current");
            if (model != null) {
                return model;
            }
        }
        ContainerRoot model = pullModel("http://127.0.0.1:" + PORT + "/model/current");
        if (model == null) {
            logger.debug("Unable to pull a model on " + targetNodeName);
            return null;
        } else {
            return model;
        }
    }

    private ContainerRoot pullModel(String urlPath) {
        ContainerRoot model = null;
        model = pullModel(urlPath+"/zip",true);
        if(model == null){
            model = pullModel(urlPath,false);
        }
        return model;
    }

    private ContainerRoot pullModel(String urlPath,Boolean zip) {
        try {
            URL url = new URL(urlPath);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(2000);
            InputStream inputStream = conn.getInputStream();
            if(zip){
                return KevoreeXmiHelper.loadCompressedStream(inputStream);
            } else {
                return KevoreeXmiHelper.loadStream(inputStream);
            }
        } catch (IOException e) {
            return null;
        }
    }

}
