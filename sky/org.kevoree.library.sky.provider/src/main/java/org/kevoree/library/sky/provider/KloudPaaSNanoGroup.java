package org.kevoree.library.sky.provider;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.KevoreeFactory;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.script.KevScriptEngine;
import org.kevoree.framework.*;
import org.kevoree.framework.Constants;
import org.kevoree.library.nanohttp.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 19/01/12
 * Time: 18:16
 *
 * @author Erwan Daubert
 * @version 1.0
 */

@Library(name = "SKY")
@GroupType
@DictionaryType({
		@DictionaryAttribute(name = "masterNode", optional = false),
		@DictionaryAttribute(name = "port", optional = true, fragmentDependant = true),
		@DictionaryAttribute(name = "ip", defaultValue = "0.0.0.0", optional = true, fragmentDependant = true),
		@DictionaryAttribute(name = "SSH_Public_Key", optional = true)
})
public class KloudPaaSNanoGroup extends AbstractGroupType {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private ContainerRoot userModel = KevoreeFactory.createContainerRoot();
	private NanoHTTPD server = null;

	private int port;

	private ExecutorService poolUpdate = Executors.newSingleThreadExecutor();

	@Start
	public void startRestGroup () throws IOException {
		poolUpdate = Executors.newSingleThreadExecutor();
		port = Integer.parseInt(this.getDictionary().get("port").toString());
		String address = this.getDictionary().get("ip").toString();
		logger.info("starting HTTP server on {}:{}", address, port);
		server = new NanoHTTPD(new InetSocketAddress(InetAddress.getByName(address), port)) {
			public Response serve (String uri, String method, Properties header, Properties parms, Properties files, InputStream body) {
				if (method.equals("POST")) {
					Boolean externalSender = true;
					Enumeration e = parms.propertyNames();
					while (e.hasMoreElements()) {
						String value = (String) e.nextElement();
						if (value.endsWith("nodesrc")) {
							externalSender = false;
							// we presume that if nodesrc is define so a node have set it and maybe the master node but as the master node doesn't appear on this local model, we cannot check the src
						}
					}
					ContainerRoot model = KevoreeXmiHelper.loadStream(body);
					//CHECK
					if (KloudHelper.check(model).isEmpty()) {
						if (KloudHelper.isIaaSNode(getModelService().getLastModel(), getName(), getNodeName())) {
							//ADD TO STACK
							logger.debug("A new user model is received, adding a task to process a deployment");

							IaaSUpdate jobUpdate = new IaaSUpdate(userModel, model);
							userModel = model;
							poolUpdate.submit(jobUpdate);
						} else {
							//FORWARD TO MASTER
							if (externalSender) {
								if (getDictionary().get("masterNode") != null) {
									for (String ipPort : KloudHelper.getMasterIP_PORT(getDictionary().get("masterNode").toString())) {
										logger.debug("send model on {}" + ipPort);
										KloudHelper.sendModel(model, "http://" + ipPort + "/model/current");
									}
								}
							}
						}
						return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, "<ack nodeName=\"" + getNodeName() + "\" />");
					} else {
						logger.debug("Unable to manage this kind of node as a Kloud node");
						return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, "<nack nodeName=\"" + getNodeName() + "\" />");
					}

				} else if (method.equals("GET")) {
					if (uri.endsWith("/model/current")) {
						String msg;
						if (KloudHelper.isIaaSNode(getModelService().getLastModel(), getName(), getNodeName())) {
							logger.debug("GET request about user model");
							Option<ContainerRoot> userModelOption = KloudReasoner.updateUserConfiguration(getName(), userModel, getModelService().getLastModel(), getKevScriptEngineFactory());
							if (userModelOption.isDefined()) {
								msg = KevoreeXmiHelper.saveToString(userModelOption.get(), false);
							} else {
								logger.debug("Unable to find a valid user model to send it");
								msg = "Unable to find a valid user model to send it";
							}
						} else if (KloudHelper.isPaaSNode(getModelService().getLastModel(), getName(), getNodeName())) {
							// if this instance is on top of PaaS node then we deploy the model on the node
							msg = KevoreeXmiHelper.saveToString(getModelService().getLastModel(), false);
						} else {
							logger.debug("Unable to manage this kind of node as a Kloud node");
							msg = "Unable to manage this kind of node as a Kloud node";
						}
						return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, msg);
					} else if (uri.endsWith("/release")) {
						if (KloudHelper.isIaaSNode(getModelService().getLastModel(), getName(), getNodeName())) {
							IaaSUpdate jobUpdate = new IaaSUpdate(userModel, KevoreeFactory.createContainerRoot());
							userModel = KevoreeFactory.createContainerRoot();
							poolUpdate.submit(jobUpdate);
							return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, "");
						}
					}
				}
				return new NanoHTTPD.Response(HTTP_BADREQUEST, MIME_XML, "ONLY GET OR POST METHOD SUPPORTED");
			}
		};

		if (KloudHelper.isPaaSNode(getModelService().getLastModel(), getName(), getNodeName())) {
			// configure ssh authorized keys for user nodes
			Object sshKeyObject = this.getDictionary().get("SSH_Public_Key");
			if (sshKeyObject != null) {
				// build directory if necessary
				File f = new File(System.getProperty("user.home") + File.separator + ".ssh");
				if ((f.exists() && f.isDirectory()) || (!f.exists() && f.mkdirs())) {
					// copy key
					FileNIOHelper.addStringToFile(sshKeyObject.toString(), new File(System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "authorized_keys"));
				}
			}
		}
	}

	@Stop
	public void stopRestGroup () {
		poolUpdate.shutdownNow();
		server.stop();

	}

	@Update
	public void update () throws IOException {
		if (!this.getDictionary().get("port").toString().equals("" + port)) {
			stopRestGroup();
			startRestGroup();
		}
	}


	@Override
	public boolean triggerPreUpdate (ContainerRoot currentModel, ContainerRoot proposedModel) {
		logger.debug("Trigger pre update");

		if (KloudHelper.isIaaSNode(currentModel, getName(), getNodeName()) && KloudHelper.isUserModel(proposedModel, getName(), getNodeName())) {
			logger.debug("A new user model is received, adding a task to process a deployment");

			IaaSUpdate jobUpdate = new IaaSUpdate(userModel, proposedModel);
			userModel = proposedModel;
			poolUpdate.submit(jobUpdate);

			return false;
		} else {
			logger.debug("nothing specific, update can be done");
			return true;
		}
	}

	@Override
	public void triggerModelUpdate () {
		if (userModel.getNodes().size() == 0) {
			if (KloudHelper.isPaaSNode(this.getModelService().getLastModel(), this.getName(), this.getNodeName())) {
				// pull the model to the master node
				if (getDictionary().get("masterNode") != null) {
					for (String ipPort : KloudHelper.getMasterIP_PORT(getDictionary().get("masterNode").toString())) {
						userModel = KloudHelper.pullModel("http://" + ipPort + "/model/current");
						if (userModel != null) {
							logger.debug("Try to apply a new model on PaaSNode coming from {}", this.getDictionary().get("masterNode").toString());
							this.getModelService().atomicUpdateModel(userModel);
							break;
						} else {
							userModel = KevoreeFactory.createContainerRoot();
							logger.warn("Unable to get user model on master node using" + ipPort);
						}
					}
				}
			}
		}
	}

	@Override
	public void push (ContainerRoot model, String targetNodeName) {
		List<String> ips = KevoreePropertyHelper.getStringNetworkProperties(model, targetNodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
		Option<Integer> portOption = KevoreePropertyHelper.getIntPropertyForGroup(model, this.getName(), "port", true, targetNodeName);
		if (portOption.isDefined()) {
			int PORT = portOption.get();
			String param = "";
			if (!getNodeName().equals("")) {
				param = "?nodesrc=" + getNodeName();
			}
			boolean sent = false;
			for (String ip : ips) {
				logger.debug("try to send model on url=>" + "http://" + ip + ":" + PORT + "/model/current" + param);
				if (KloudHelper.sendModel(model, "http://" + ip + ":" + PORT + "/model/current" + param)) {
					sent = true;
					break;
				}
			}

			if (!sent) {
				logger.debug("try to send model on url=>" + "http://127.0.0.1:" + PORT + "/model/current" + param);
				if (!KloudHelper.sendModel(model, "http://127.0.0.1:" + PORT + "/model/current" + param)) {
					logger.debug("Unable to push a model on {}", targetNodeName);
					logger.debug("try to send model using master node property");
					if (!KloudHelper.sendModel(model, this.getDictionary().get("masterNode").toString() + param)) {
						logger.debug("Unable to send model using master node property");
					}
				}
			}
		} else {
			if (getDictionary().get("masterNode") != null) {
				for (String ipPort : KloudHelper.getMasterIP_PORT(getDictionary().get("masterNode").toString())) {
					logger.debug("send model on {} using masterNode", ipPort);
					if (KloudHelper.sendModel(model, "http://" + ipPort + "/model/current")) {
						break;
					}
				}
			} else {
				logger.error("Unable to push a model on {} because there is not enough information to localize this node", targetNodeName);
			}
		}
	}

	/*private boolean sendModel (ContainerRoot model, String urlPath) {
		try {
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			KevoreeXmiHelper.saveStream(outStream, model);
			outStream.flush();
			URL url = new URL(urlPath);
			URLConnection conn = url.openConnection();
			conn.setConnectTimeout(3000);
			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(outStream.toString());
			wr.flush();
			// Get the response
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line = rd.readLine();
			while (line != null) {
				line = rd.readLine();
			}
			wr.close();
			rd.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}*/

	@Override
	public ContainerRoot pull (String targetNodeName) {

		List<String> ips = KevoreePropertyHelper.getStringNetworkProperties(this.getModelService().getLastModel(), targetNodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
		Option<Integer> portOption = KevoreePropertyHelper.getIntPropertyForGroup(this.getModelService().getLastModel(), this.getName(), "port", true, targetNodeName);
		int PORT = 8000;
		if (portOption.isDefined()) {
			PORT = portOption.get();
		}
		String param = "";
		if (getNodeName().equals("")) {
			param = "?nodesrc=" + getNodeName();
		}

		for (String ip : ips) {
			logger.debug("try to pull model on url=>" + "http://" + ip + ":" + PORT + "/model/current" + param);
			ContainerRoot model = KloudHelper.pullModel("http://" + ip + ":" + PORT + "/model/current" + param);
			if (model != null) {
				return model;
			}
		}
		ContainerRoot model = KloudHelper.pullModel("http://127.0.0.1:" + PORT + "/model/current" + param);
		if (model == null) {
			logger.debug("Unable to pull a model on " + targetNodeName);
			return null;
		} else {
			return model;
		}
	}

	/*private ContainerRoot pullModel (String urlPath) {
		try {
			URL url = new URL(urlPath);
			URLConnection conn = url.openConnection();
			conn.setConnectTimeout(2000);
			InputStream inputStream = conn.getInputStream();
			return KevoreeXmiHelper.loadStream(inputStream);
		} catch (IOException e) {
			return null;
		}
	}*/


	private class IaaSUpdate implements Runnable {

		private ContainerRoot currentModel = null;
		private ContainerRoot proposedModel = null;

		public IaaSUpdate (ContainerRoot currentModel, ContainerRoot proposedModel) {
			this.currentModel = currentModel;
			this.proposedModel = proposedModel;
		}

		@Override
		public void run () {
			if (KloudReasoner.needsNewDeployment(proposedModel, currentModel)) {
				logger.debug("A new Deployment must be done!");
				KevScriptEngine kengine = getKevScriptEngineFactory().createKevScriptEngine();
				if (KloudReasoner.processDeployment(proposedModel, currentModel, getModelService().getLastModel(), kengine, getName())) {
					for (int i = 0; i < 5; i++) {
						try {
							if (kengine.atomicInterpretDeploy()) {
								break;
							}
						} catch (Exception e) {
							logger.debug("Error while update user configuration, try number " + i);
						}
					}
				}

			}
			//ADD GROUP to user model
			logger.debug("update user configuration when user model must be forwarded");
			Option<ContainerRoot> userModelUpdated = KloudReasoner.updateUserConfiguration(getName(), proposedModel, getModelService().getLastModel(), getKevScriptEngineFactory());
			if (userModelUpdated.isDefined()) {
				for (ContainerNode userNode : currentModel.getNodesForJ()) {
					push(userModelUpdated.get(), userNode.getName());
				}
			} else {
				logger.error("Unable to update user configuration, with user group");
			}
		}
	}


}