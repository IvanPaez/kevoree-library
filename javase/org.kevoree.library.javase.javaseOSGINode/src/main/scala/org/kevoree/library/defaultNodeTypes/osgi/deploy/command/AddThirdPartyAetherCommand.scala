package org.kevoree.library.defaultNodeTypes.osgi.deploy.command

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

import org.osgi.framework.BundleException
import org.osgi.service.packageadmin.PackageAdmin
import org.slf4j.LoggerFactory
import org.kevoree.DeployUnit
import java.io.{FileInputStream, File}
import org.kevoree.library.defaultNodeTypes.jcl.deploy.context.KevoreeDeployManager
import org.kevoree.library.defaultNodeTypes.osgi.deploy.{KevoreeOSGIMapping, OSGIKevoreeDeployManager}
import org.kevoree.library.defaultNodeTypes.jcl.deploy.command.CommandHelper
import org.kevoree.api.PrimitiveCommand

case class AddThirdPartyAetherCommand(deployUnit: DeployUnit,bs : org.kevoree.api.Bootstraper) extends PrimitiveCommand {

  var logger = LoggerFactory.getLogger(this.getClass)
  var lastExecutionBundle : Option[org.osgi.framework.Bundle] = null


  def execute(): Boolean = {

    try {

      val arteFile: File = bs.resolveDeployUnit(deployUnit)
      lastExecutionBundle = Some(OSGIKevoreeDeployManager.getBundleContext.installBundle("file:///" + arteFile.getAbsolutePath, new FileInputStream(arteFile)));


      logger.debug("Install file :"+arteFile.getAbsolutePath)


      //lastExecutionBundle = Some(ctx.bundleContext.installBundle(url));
      val symbolicName: String = lastExecutionBundle.get.getSymbolicName
      KevoreeDeployManager.addMapping(new KevoreeOSGIMapping(CommandHelper.buildKEY(deployUnit), deployUnit.getClass.getName,deployUnit, lastExecutionBundle.get.getBundleId))
      // lastExecutionBundle.get.start
     // mustBeStarted = false
      true
    } catch {
      case e: BundleException if (e.getType == BundleException.DUPLICATE_BUNDLE_ERROR) => {
        logger.warn("ThirdParty conflict ! ")
      //  mustBeStarted = false
        true
      }
      case _@e => {
        logger.error("Deploy unit (" + deployUnit.getGroupName + ":" + deployUnit.getUnitName + ":" + deployUnit.getVersion + " at " + deployUnit.getUrl + ") Installation error => ", e)
        false
      }
    }
  }


  def undo() = {
    try {
      lastExecutionBundle match {
        case Some(bundle) => {
          //CLEAR CACHE
          KevoreeDeployManager.bundleMapping.filter(map => map.isInstanceOf[KevoreeOSGIMapping] && map.asInstanceOf[KevoreeOSGIMapping].bundleID == bundle.getBundleId).foreach {
            map =>
              KevoreeDeployManager.removeMapping(map)
          }
          bundle.stop()
          bundle.uninstall()
          val srPackageAdmin = OSGIKevoreeDeployManager.getBundleContext.getServiceReference(classOf[PackageAdmin].getName)
          val padmin: PackageAdmin = OSGIKevoreeDeployManager.getBundleContext.getService(srPackageAdmin).asInstanceOf[PackageAdmin]
          padmin.resolveBundles(Array(bundle))
        }
        case None => //NOTHING CAN BE DOING HERE
      }
    } catch {
      case _ =>
    }

    /* TODO CALL refreshPackage */
  }


}
