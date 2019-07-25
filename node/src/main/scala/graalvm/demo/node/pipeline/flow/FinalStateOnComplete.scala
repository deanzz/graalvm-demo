package graalvm.demo.node.pipeline.flow

import java.io.ByteArrayOutputStream

import akka.{Done, NotUsed}
import akka.actor.ActorRef
import akka.stream.{AbruptStageTerminationException, Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler, StageLogging}
import com.google.protobuf.ByteString
import graalvm.demo.node.{AvroRecord, TaskComplete, TaskStatus}
import org.apache.avro.generic.{GenericDatumWriter, IndexedRecord}
import org.apache.avro.io.EncoderFactory

import scala.collection.mutable
import scala.concurrent.Future

class FinalStateOnComplete(pid: String, step: Long, leader: ActorRef)
  extends GraphStage[FlowShape[IndexedRecord, IndexedRecord]] {

  val in = Inlet[IndexedRecord]("StatisticSink.in")
  val out = Outlet[IndexedRecord]("StatisticSink.out")
  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler with StageLogging {

      var count = 0L
      var cursor = 0L
      var startTime = 0L
      var finishTime = 0L
      var usedTime = 0L
      val outputStream = new ByteArrayOutputStream()
      val encoder = EncoderFactory.get.binaryEncoder(outputStream, null)
      var writer: GenericDatumWriter[IndexedRecord] = _
      val result = mutable.MutableList.empty[AvroRecord]

      var completionSignalled = false

      override def onPush(): Unit = {
        if (startTime == 0L)
          startTime = System.currentTimeMillis()
        val ele = grab(in)
        push(out, ele)
        cursor += 1L
        val r = generateAvroRecord(ele)
        //log.info(s"onPush got $r")
        result += r
        /*
        if (count == step) {
          finishTime = System.currentTimeMillis()
          usedTime = finishTime - startTime
          log.info("Final sink {} message processed, pid is {}, speed is {}/ms", cursor, pid, cursor / usedTime)
          count = 0L
        }
        */
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFailure(cause: Throwable): Unit = {
        log.info(s"upstream failure. cause: $cause")
        leader ! TaskComplete(pid, TaskStatus.FAILED, cause.getLocalizedMessage, cursor)
        completionSignalled = true
        failStage(cause)
      }

      override def onUpstreamFinish(): Unit = {
        finishTime = System.currentTimeMillis()
        usedTime = finishTime - startTime
        log.info("Final sink {} message processed, pid is {}, speed is {}/ms", cursor, pid, cursor / usedTime)
        leader ! TaskComplete(pid, TaskStatus.COMPLETE, "", cursor, result)
        completionSignalled = true
        completeStage()
      }

      override def postStop(): Unit = {
        log.info(s"FinalStateOnComplete. receive post stop msg. pid: $pid, completionSignalled: $completionSignalled")
        if (!completionSignalled) {
          leader ! TaskComplete(pid, TaskStatus.TERMINATED, "Unexpected stop message", cursor)
        }
      }

      setHandlers(in, out, this)

      private def generateAvroRecord(record: IndexedRecord): AvroRecord = {
        if (record == null) {
          return AvroRecord()
        }
        if (writer == null) {
          log.info(s"record.getSchema:\n${record.getSchema}")
          writer = new GenericDatumWriter[IndexedRecord](record.getSchema)
        }
        writer.write(record, encoder)
        encoder.flush
        val avroRecord = AvroRecord(record.getSchema.toString(), ByteString.copyFrom(outputStream.toByteArray))
        outputStream.reset()
        avroRecord
      }
    }

}

object FinalStateOnComplete {
  def createFlow(pid: String, step: Long, leader: ActorRef): Flow[IndexedRecord, IndexedRecord, NotUsed] =
    Flow.fromGraph(new FinalStateOnComplete(pid, step, leader))

  def createSink(
                     pid: String,
                     step: Long,
                     leader: ActorRef
                   ): Sink[IndexedRecord, Future[Done]] =
    Flow.fromGraph(new FinalStateOnComplete(pid, step, leader)).toMat(Sink.ignore)(Keep.right)
}