package graalvm.demo.node.pipeline.flow

import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.{Attributes, FlowShape, Inlet, Outlet, Supervision}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.avro.generic.IndexedRecord

import scala.util.control.NonFatal

class StatelessConditionFlow(func: IndexedRecord => Boolean)
  extends GraphStage[FlowShape[IndexedRecord, IndexedRecord]] {
  val in: Inlet[IndexedRecord] = Inlet[IndexedRecord]("StatelessConditionFlow.in")
  val out: Outlet[IndexedRecord] = Outlet[IndexedRecord]("StatelessConditionFlow.out")
  override val shape = FlowShape(in, out)

  override def createLogic(
                            inheritedAttributes: Attributes
                          ): GraphStageLogic = {
    val logic: GraphStageLogic = new GraphStageLogic(shape) with OutHandler with InHandler {
      def decider: Supervision.Decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      override def onPush(): Unit =
        try {
          val elem = grab(in)
          if (func(elem)) {
            push(out, elem)
          } else {
            pull(in)
          }
        } catch {
          case NonFatal(ex) ⇒
            decider(ex) match {
              case Supervision.Stop ⇒ failStage(ex)
              case _ ⇒ pull(in)
            }
        }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = {
        completeStage()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        super.onUpstreamFailure(ex)
      }

      override def postStop(): Unit = {
        super.postStop()
      }

      setHandlers(in, out, this)
    }
    logic
  }
}

object StatelessConditionFlow {
  def apply(func: IndexedRecord => Boolean): StatelessConditionFlow = new StatelessConditionFlow(func)
}
