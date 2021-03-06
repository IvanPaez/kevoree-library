package org.kevoree.library.gossiperNetty

import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.kevoree.library.gossiperNetty.api.msg.KevoreeMessage.Message
import org.slf4j.LoggerFactory
import version.Gossip.{VersionedModel, VectorClockUUIDs}
import java.net.InetSocketAddress

class GossiperRequestSenderHandler (gossiperRequestSender: GossiperRequestSender) extends SimpleChannelUpstreamHandler {

  private val logger = LoggerFactory.getLogger (classOf[GossiperRequestSenderHandler])

  override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent) {
    val message = e.getMessage.asInstanceOf[Message]
    if (message.getContentClass.equals (classOf[VectorClockUUIDs].getName)) {
      gossiperRequestSender.initSecondStepAction (message, e.getRemoteAddress.asInstanceOf[InetSocketAddress] /*, e.getChannel*/)
    } /* else if (message.getContentClass.equals(classOf[VectorClockUUID].getName)) {
	  //var vectorClockUUID = RichString(message.getContent.toStringUtf8).fromJSON(classOf[VectorClockUUID])
	  gossiperRequestSender.initLastStepAction(message, e.getRemoteAddress, e.getChannel)
	}*/
    else if (message.getContentClass.equals (classOf[VersionedModel].getName)) {
      gossiperRequestSender.endGossipAction (message)
      //e.getChannel.close.awaitUninterruptibly
    }
  }

  override def exceptionCaught (ctx: ChannelHandlerContext, e: ExceptionEvent) {
    //NOOP

    try {
      logger.warn ("Communication failed between " + ctx.getChannel.getLocalAddress + " and " ,e.getCause)
    } catch {
      case _ =>
    }
    //e.getChannel.close.awaitUninterruptibly
  }
}
