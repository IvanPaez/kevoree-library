package org.kevoree.library.arduinoNodeType;

import org.kevoree.*;
import org.kevoree.api.service.core.checker.CheckerViolation;
import org.kevoree.cloner.ModelCloner;
import org.kevoree.extra.kserial.KevoreeSharedCom;
import org.kevoree.framework.AbstractNodeType;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.kompare.JavaSePrimitive;
import org.kevoree.kompare.KevoreeKompareBean;
import org.kevoree.library.arduinoNodeType.generator.KevoreeCGenerator;
import org.kevoree.library.arduinoNodeType.util.ArduinoResourceHelper;
import org.kevoree.library.arduinoNodeType.utils.ComSender;
import org.kevoree.tools.marShell.ast.Script;
import org.kevoree.tools.marShellTransform.AdaptationModelWrapper;
import org.kevoree.tools.marShellTransform.KevScriptWrapper;
import org.kevoreeAdaptation.AdaptationModel;
import org.kevoreeAdaptation.AdaptationPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wayoda.ang.libraries.CodeManager;
import org.wayoda.ang.libraries.Core;
import org.wayoda.ang.project.ArduinoBuildEnvironment;
import org.wayoda.ang.project.Sketch;
import org.wayoda.ang.project.Target;
import org.wayoda.ang.project.TargetDirectoryService;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

@org.kevoree.annotation.NodeType
@org.kevoree.annotation.Library(name = "Arduino")
@org.kevoree.annotation.DictionaryType({
        @org.kevoree.annotation.DictionaryAttribute(name = "boardTypeName", defaultValue = "uno", optional = true, vals = {"uno", "atmega328", "mega2560"}),
        @org.kevoree.annotation.DictionaryAttribute(name = "incremental", defaultValue = "true", optional = true, vals = {"true", "false"}),
        @org.kevoree.annotation.DictionaryAttribute(name = "pmem", defaultValue = "eeprom", optional = true, vals = {"eeprom", "sd"}),
        @org.kevoree.annotation.DictionaryAttribute(name = "psize", defaultValue = "MAX", optional = true)
})
@org.kevoree.annotation.PrimitiveCommands(values = {"StartThirdParty", "UpdateType", "UpdateDeployUnit", "AddType", "AddDeployUnit", "AddThirdParty", "RemoveType", "RemoveDeployUnit", "UpdateInstance", "UpdateBinding", "UpdateDictionaryInstance", "AddInstance", "RemoveInstance", "AddBinding", "RemoveBinding", "AddFragmentBinding", "RemoveFragmentBinding", "StartInstance", "StopInstance", "StartThirdParty"}, value = {})
public class ArduinoNode extends AbstractNodeType {
    private static final Logger logger = LoggerFactory.getLogger(ArduinoNode.class);

    public ArduinoGuiProgressBar progress = null;
    public File newdir = null;
    private ArduinoChecker localChecker = null;

    protected Boolean forceUpdate = false;

    public void setForceUpdate(Boolean f) {
        forceUpdate = f;
    }

    @org.kevoree.annotation.Start
    public void startNode() {
        localChecker = new ArduinoChecker(getNodeName());
        ArduinoResourceHelper.setBs(getBootStrapperService());
    }

    @org.kevoree.annotation.Stop
    public void stopNode() {
        ArduinoResourceHelper.setBs(null);
    }

    @Override
    public AdaptationModel kompare(ContainerRoot current, ContainerRoot target) {
        return null; //NOT TO BE USED WITH KEVOREE CORE
    }

    @Override
    public org.kevoree.api.PrimitiveCommand getPrimitive(AdaptationPrimitive adaptationPrimitive) {
        return null;//NOT TO BE USED WITH KEVOREE CORE
    }

    //@Override
    public void push(final String targetNodeName, final ContainerRoot root, String boardPortName) throws IOException {

        //new Thread() {

        //@Override
        //public void run() {
        progress = new ArduinoGuiProgressBar();
        JFrame frame = new JFrame("Arduino model push");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(progress);
        frame.pack();
        frame.setVisible(true);

        //SEARCH ARDUINO HOME
        /*ArduinoHomeFinder.checkArduinoHome();
        ArduinoDefaultLibraryManager.copyDefaultLibrary();*/

        progress.beginTask("Build diff model", 10);
        KevoreeKompareBean kompare = new KevoreeKompareBean();

        newdir = new File(System.getProperty("java.io.tmpdir") + File.separator + "arduinoGenerated" + targetNodeName);
        newdir.delete();
        newdir.mkdirs();
        /*if (!newdir.exists()) {
            newdir.mkdir();
        }*/

        ContainerRoot lastVersionModel = KevoreeFactory.eINSTANCE().createContainerRoot();

        int lastVersion = 0;
        //Try to find previous version
        if (getDictionary().get("incremental") != null && getDictionary().get("incremental").equals("true")) {
            logger.info("Incremental search");
            /*
            File lastModelFile = null;
            for (File f : newdir.listFiles()) {
                if (f.getName().endsWith(".kev")) {
                    try {
                        String nameWithoutExtention = f.getName().substring(0, f.getName().lastIndexOf("."));
                        String version = nameWithoutExtention.substring(nameWithoutExtention.lastIndexOf("_") + 1);
                        Integer nversion = Integer.parseInt(version);
                        if (nversion > lastVersion) {
                            lastVersion = nversion;
                            lastModelFile = f;
                        }
                    } catch (Exception e) {
                    }
                }
            }
            try {

                lastVersionModel = KevoreeXmiHelper.load(lastModelFile.getAbsolutePath());
            } catch (Exception e) {
            }  */

            //TODO INSERT GETTER CODE
            //INIT GOOD MODEL IN LAST VERSION MODEL

            lastVersionModel = ArduinoModelGetHelper.getCurrentModel(root, targetNodeName, boardPortName);


        } else {
            //CLEAR PREVIOUS SAVED MODEL
            for (File f : newdir.listFiles()) {
                if (f.getName().endsWith(".kev")) {
                    f.delete();
                }
            }

        }


        if (lastVersionModel == null || lastVersionModel.getNodes().size() == 0) {
            logger.info("No Previous Model , Init one from targetModel");
            ModelCloner cloner = new ModelCloner();
            lastVersionModel = cloner.clone(root);
            for (ContainerNode node : lastVersionModel.getNodesForJ()) {
                node.removeAllComponents();
                node.removeAllHosts();
            }
            lastVersionModel.removeAllMBindings();
            lastVersionModel.removeAllGroups();
        }

        /* */

        tempRoot = root;

        ModelCloner cc = new ModelCloner();
        ContainerRoot cloned = cc.clone(root);
        cloned.removeAllGroups();


        AdaptationModel kompareModel = kompare.kompare(lastVersionModel, cloned, targetNodeName);
       /* System.out.println(kompareModel.getAdaptationsForJ().size());
        for (AdaptationPrimitive p : kompareModel.getAdaptationsForJ()) {
            System.out.println("ad =>"+p.getRef());
        }    */


        progress.endTask();

        if (kompareModel.getAdaptationsForJ().size() > 0) {
            progress.beginTask("Prepare model generation", 20);

            File newdirTarget = new File(newdir.getAbsolutePath() + File.separator + "target");
            org.kevoree.library.arduinoNodeType.FileHelper.createAndCleanDirectory(newdirTarget);
            TargetDirectoryService.rootPath = newdirTarget.getAbsolutePath();
            outputPath = newdir.getAbsolutePath();
            logger.debug("outDir=" + outputPath);
            progress.endTask();
            progress.beginTask("Compute firmware update", 30);
            try {

                if (deploy(kompareModel, targetNodeName, boardPortName)) {
                    progress.endTask();
                } else {
                    progress.failTask();
                }
            } catch (Exception e) {
                progress.failTask();
//                    e.printStackTrace();
                logger.error("Error appears when we compute the firmware update", e);
            }
            progress.beginTask("Save model for incremental deployment", 100);
            KevoreeXmiHelper.save(newdir.getAbsolutePath() + File.separator + targetNodeName + "_" + (lastVersion + 1) + ".kev", root);

            progress.endTask();
        }


        frame.setVisible(false);
        frame.dispose();


        //  }
        //}.start();


    }

    public String outputPath = "";


    private ContainerRoot tempRoot = null;

    public boolean deploy(AdaptationModel modelIn, String nodeName, String boardPortName) {
        boolean typeAdaptationFound = false;
        ContainerRoot rootModel = null;
        for (AdaptationPrimitive p : modelIn.getAdaptationsForJ()) {
            Boolean addType = p.getPrimitiveType().getName().equals(JavaSePrimitive.AddType());
            Boolean removeType = p.getPrimitiveType().getName().equals(JavaSePrimitive.RemoveType());
            Boolean updateType = p.getPrimitiveType().getName().equals(JavaSePrimitive.UpdateType());

            if (addType || removeType || updateType) {
                typeAdaptationFound = true;
               // rootModel = (ContainerRoot) ((TypeDefinition) p.getRef()).eContainer();
            } /*else {
                rootModel = tempRoot;
            }   */
            rootModel = tempRoot;
        }


        if (typeAdaptationFound || forceUpdate) {
            java.util.List<CheckerViolation> result = localChecker.check(rootModel);
            if (result.size() > 0) {
                for (CheckerViolation cv : result) {
                    logger.error("Checker Error = " + cv.getMessage());
                }
                return false;
            }

            KevoreeKompareBean kompare = new KevoreeKompareBean();

            ModelCloner cloner = new ModelCloner();
            ContainerRoot lastVersionModel = cloner.clone(rootModel);
            for (ContainerNode node : lastVersionModel.getNodesForJ()) {
                node.removeAllComponents();
                node.removeAllHosts();
            }
            lastVersionModel.removeAllMBindings();
            lastVersionModel.removeAllGroups();

            AdaptationModel model = kompare.kompare(lastVersionModel, rootModel, nodeName);

            //Must compute a dif from scratch model

            logger.debug("Type adaptation detected -> full firmware update needed !");
            //Step : Type Bundle preparation step

            // Bundle bundle = (Bundle) this.getDictionary().get(Constants.KEVOREE_PROPERTY_OSGI_BUNDLE());

            //if (bundle != null) {
            logger.debug("Install Type definitions");
            TypeBundleBootstrap.bootstrapTypeBundle(model, this);
            //} else {
            //    logger.warn("No OSGi runtime available");
            //}
            //Step : Generate firmware code to output path
            KevoreeCGenerator generator = new KevoreeCGenerator();


            PMemory pm = PMemory.EEPROM;
            if (this.getDictionary().get("pmem") != null) {
                String s = this.getDictionary().get("pmem").toString();
                if (s.toLowerCase().equals("eeprom")) {
                    pm = PMemory.EEPROM;
                }
                if (s.toLowerCase().equals("sd")) {
                    pm = PMemory.SD;
                }
            }
            String psize = "";
            if (this.getDictionary().get("psize") != null && this.getDictionary().get("psize") != "MAX") {
                psize = this.getDictionary().get("psize").toString();
            }


            generator.generate(model, nodeName, outputPath, getDictionary().get("boardTypeName").toString(), pm, psize, this);

//STEP 3 : Deploy by commnication channel
            progress.beginTask("Prepare compilation", 40);
            ArduinoCompilation arduinoCompilation = new ArduinoCompilation();
            ArduinoLink arduinoLink = new ArduinoLink();
            ArduinoArchive arduinoArchive = new ArduinoArchive();
            ArduinoPostCompilation arduinoPostCompilation = new ArduinoPostCompilation();
            ArduinoDeploy arduinoDeploy = new ArduinoDeploy();
            arduinoCompilation.prepareCommands();
            arduinoLink.prepareCommands();
            arduinoArchive.prepareCommands();
            arduinoPostCompilation.prepareCommands();
            arduinoDeploy.prepareCommands();
            ArduinoBuildEnvironment arduinoBuildEnvironment = ArduinoBuildEnvironment.getInstance();
            Target target = arduinoBuildEnvironment.getDefaultTargetList().getTarget(getDictionary().get("boardTypeName").toString());
            progress.endTask();
            try {
                progress.beginTask("Prepare sketch", 50);
                Sketch sketch = new Sketch(newdir);
                sketch.preprocess(target);
                progress.endTask();
                Core core = CodeManager.getInstance().getCore(target);
                //System.err.println("core :=> " + core + " -> " + target.getCore());
                arduinoCompilation.compileCore(sketch, target, core);
                progress.beginTask("Library processing", 60);

                for (org.wayoda.ang.libraries.Library library : sketch.getLibraries()) {
                    logger.debug("----Lib " + library.getName());
                    arduinoCompilation.compileLibrary(sketch, target, library, core);
                }
                progress.endTask();

                progress.beginTask("Firmware compilation", 70);
                arduinoCompilation.compileSketch(sketch, target, core, generator.context().getGenerator());
                arduinoArchive.archiveSketch(sketch, target);
                progress.endTask();

                progress.beginTask("Firmware linkage", 80);
                arduinoLink.linkSketch(sketch, target);
                arduinoPostCompilation.postCompileSketch(sketch, target);
                progress.endTask();

                String boardName = "";
                if (boardPortName != null && !boardPortName.equals("")) {
                    boardName = boardPortName;
                }

                if (boardName == null || boardName.equals("")) {
                    boardName = GuiAskForComPort.askPORT();
                }

                logger.debug("boardPortName=" + boardName);

                progress.beginTask("Upload to arduino board", 90);
                KevoreeSharedCom.lockPort(boardName);

                arduinoDeploy.uploadSketch(sketch, target, boardName);

                KevoreeSharedCom.unlockPort(boardName);

                progress.endTask();

            } catch (FileNotFoundException ex) {
                logger.error("", ex);
                progress.failTask();
            }

        } else {
            logger.debug("incremental update available -> try to generate KevScript !");
            Script baseScript = KevScriptWrapper.miniPlanKevScript(AdaptationModelWrapper.generateScriptFromAdaptModel(modelIn));
            String resultScript = KevScriptWrapper.generateKevScriptCompressed(baseScript, this.getNodeName());
            logger.debug(resultScript);
            String boardName = "";
            if (boardPortName != null && !boardPortName.equals("")) {
                boardName = boardPortName;
            }
            if (boardName == null || boardName.equals("")) {
                boardName = GuiAskForComPort.askPORT();
            }
            try {
                ComSender.ping(boardName);
                ComSender.send(resultScript, boardName);
            } catch (Exception e) {
//                e.printStackTrace();
                logger.error("", e);
            }


        }


        return true;
    }
}
