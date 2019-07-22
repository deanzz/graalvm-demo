package graalvm.demo.node.pipeline

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import graalvm.demo.filter.FilterParam
import graalvm.demo.node.{TransformerInfo, TransformerType}
import graalvm.demo.node.transformers.SimpleStatelessFilter
import org.apache.avro.generic.IndexedRecord

import scala.concurrent.ExecutionContext

object TransformerFactory {

  def create(id: String, leader: ActorRef, transformerInfo: TransformerInfo, parentSchema: Option[org.apache.avro.Schema])(
    implicit mat: ActorMaterializer,
    system: ActorSystem,
    ec: ExecutionContext
  ): (Flow[IndexedRecord, IndexedRecord, NotUsed], Option[org.apache.avro.Schema]) = {
    val log = Logging(system, this.getClass)
    log.info(s"TransformerInfo:\n$transformerInfo")
    transformerInfo.transformerType match {
      case TransformerType.STATELESS_FILTER =>
        val t = new SimpleStatelessFilter(transformerInfo.getParam.unpack[FilterParam])
        (t.createFlow(), parentSchema)
    }
  }
}
