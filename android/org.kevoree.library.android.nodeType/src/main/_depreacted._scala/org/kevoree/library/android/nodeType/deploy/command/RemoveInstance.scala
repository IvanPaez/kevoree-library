package org.kevoree.library.android.nodeType.deploy.command

/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import org.slf4j.LoggerFactory
import org.kevoree.framework.KevoreeGeneratorHelper
import org.kevoree.framework.osgi.KevoreeInstanceFactory
import org.kevoree.framework.aspects.KevoreeAspects._
import org.kevoree.{ContainerRoot, NodeType, Instance}
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.api.service.core.script.KevScriptEngineFactory
import org.kevoree.api.PrimitiveCommand
import org.kevoree.library.android.nodeType.deploy.context.KevoreeDeployManager

case class RemoveInstance(c: Instance, nodeName: String, modelservice: KevoreeModelHandlerService, kscript: KevScriptEngineFactory, bs: org.kevoree.api.Bootstraper) extends PrimitiveCommand {

  var logger = LoggerFactory.getLogger(this.getClass)

  def execute(): Boolean = {
    logger.debug("CMD REMOVE INSTANCE EXECUTION - " + c.getName + " - type - " + c.getTypeDefinition.getName);

    val bundles = KevoreeDeployManager.bundleMapping.filter({
      bm => bm.objClassName == c.getClass.getName && bm.name == c.getName
    }) ++ List()



    bundles.forall {
      mp =>

        val nodeType = c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot].getNodes.find(tn => tn.getName == nodeName).get.getTypeDefinition
        val nodeTypeName = c.getTypeDefinition.foundRelevantHostNodeType(nodeType.asInstanceOf[NodeType], c.getTypeDefinition) match {
          case Some(nt) => nt.getName
          case None => throw new Exception("Can foudn compatible nodeType for this instance on this node type ")
        }

        val activatorPackage = KevoreeGeneratorHelper.getTypeDefinitionGeneratedPackage(c.getTypeDefinition, nodeTypeName)
        val factoryName = activatorPackage + "." + c.getTypeDefinition.getName + "Factory"

        val node = c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot].getNodes.find(n => n.getName == nodeName).get
        val deployUnit = c.getTypeDefinition.foundRelevantDeployUnit(node)

        val clazz = bs.getKevoreeClassLoaderHandler.getKevoreeClassLoader(deployUnit).loadClass(factoryName)
        val clazzInstance = clazz.newInstance

        val kevoreeFactory = clazzInstance.asInstanceOf[KevoreeInstanceFactory]

        val activator = kevoreeFactory.remove(c.getName)
        activator.stop()

        true
    }
    KevoreeDeployManager.bundleMapping.filter(mb => bundles.contains(mb)).foreach {
      map =>
        KevoreeDeployManager.removeMapping(map)
    }
    true
  }

  def undo() {
    try {
      AddInstance(c, nodeName, modelservice, kscript, bs).execute()
      UpdateDictionary(c, nodeName).execute()
    } catch {
      case _ =>
    }

  }

}
