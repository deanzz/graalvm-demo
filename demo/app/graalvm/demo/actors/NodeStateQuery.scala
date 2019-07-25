package graalvm.demo.actors

import java.io.ByteArrayOutputStream

import akka.actor.{Actor, ActorLogging, Address, AddressFromURIString}
import graalvm.demo.actors.PipelineRegistry.{GetPipeServers, NodeState}
import graalvm.demo.actors.PipelineStateForQuery.{GetEnoughResourceNode, GetNodeResources, GetTaskResult, NodeJoined, NodeRemoved, NodeResource}
import graalvm.demo.common.CustomJsonEncoder
import graalvm.demo.node.{AvroRecord, GetNodeServerStates, NodeServerResource, NodeServerState, NodeStateChanged, TaskState, TaskStatus}
import javax.inject.Inject
import org.apache.avro.Schema
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter, IndexedRecord}
import org.apache.avro.io.DecoderFactory

class NodeStateQuery @Inject()() extends Actor with ActorLogging {
  val nodes = scala.collection.mutable.Map.empty[Address, NodeState]
  val taskResult = scala.collection.mutable.Map.empty[String, (Seq[String], Long)]
  implicit val ord = new Ordering[NodeState] {
    def compare(p1: NodeState, p2: NodeState) = {
      if (p1.load == p2.load) {
        if (p1.free.cpuNum == p2.free.cpuNum) {
          if (p1.free.memInMB > p2.free.memInMB) 1 else -1
        }
        else if (p1.free.cpuNum > p2.free.cpuNum) 1
        else -1
      }
      else if (p1.load > p2.load) -1
      else 1
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"PipelineStateForQuery started on path ${self.path}")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.error(reason, s"PipelineStateForQuery restarted on path ${self.path}")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"PipelineStateForQuery stopped on path ${self.path}")
  }

  override def receive: Receive = {
    case GetNodeServerStates =>
      sender() ! nodes

    case GetPipeServers =>
      sender() ! nodes.keySet.toSeq

    case GetNodeResources =>
      val resources = nodes.values.map(p => NodeResource(p.node.path.address.toString, p.load, p.total.cpuNum, p.total.memInMB, p.free.cpuNum, p.free.memInMB)).toSeq
      sender() ! resources

    case GetEnoughResourceNode =>
      val result = if (nodes.nonEmpty){
        val max = nodes.values.max
        log.info(s"Choose available pipeline that having enough resource: ${max.node.path.address}, load: ${max.load}, freeCpu: ${max.free.cpuNum}, freeMem: ${max.free.memInMB}MB")
        Some(max)
      } else None
      sender() ! result

    case state: NodeServerState =>
      nodes += sender().path.address -> NodeState(
        sender(),
        collection.mutable.Map(state.pipes.toSeq: _*),
        state.getTotal,
        state.getFree,
        state.load
      )

    case NodeStateChanged(addressStr, totalCpuNum, totalMemInMB, freeCpuNum, freeMemInMB, currLoad) =>
      val address = AddressFromURIString(addressStr)
      if (nodes.contains(address)) {
        val oldState = nodes(address)
        val newState = oldState.copy(total = NodeServerResource(totalCpuNum, totalMemInMB), free = NodeServerResource(freeCpuNum, freeMemInMB), load = currLoad)
        nodes += address -> newState
        log.info(s"PipeServerStateChanged,\n${nodes.values.map(p => s"${p.node.path.address}: ${p.load}, ${p.free.cpuNum}/${p.total.cpuNum}, ${p.free.memInMB}MB/${p.total.memInMB}MB").mkString("\n")}")
      } else {
        log.warning(s"pipeline server $addressStr not found")
      }

    case state: TaskState =>
      state.status match {
        case TaskStatus.RUNNING =>
          if (nodes.contains(sender().path.address)) {
            nodes(sender().path.address).tasks.put(state.id, state)
          } else {
            nodes.put(
              sender().path.address,
              NodeState(sender(), collection.mutable.Map(state.id -> state), NodeServerResource(), NodeServerResource())
            )
          }
        case TaskStatus.COMPLETE =>
          if (nodes.contains(sender().path.address)) {
            val t = nodes(sender().path.address).tasks(state.id)
            val res = state.result.map(record2json)
            val startTime = state.getTask.startTime
            val usedTime = System.currentTimeMillis() - startTime
            log.info(s"Task ${t.id} complete, usedTime = ${usedTime}ms")
            taskResult += t.id -> (res, usedTime)
            nodes(sender().path.address).tasks.remove(state.id)
          }

        case TaskStatus.TERMINATED =>
          if (nodes.contains(sender().path.address)) {
            nodes(sender().path.address).tasks.put(state.id, state)
          }

        case TaskStatus.FAILED =>
          if (nodes.contains(sender().path.address)) {
            nodes(sender().path.address).tasks.put(state.id, state)
          }
      }

    case NodeRemoved(address) =>
      log.info(s"pipeline server $address removed")
      nodes -= address

    case NodeJoined(address, state) =>
      log.info(s"pipeline server $address rejoined")
      nodes += address -> state

    case GetTaskResult(id) =>
      sender() ! taskResult.getOrElse(id, (Seq.empty[String], 0L))

  }

  private def record2json(r: AvroRecord) = {
    val schema = new Schema.Parser().parse(r.schema)
    val datumReader = new GenericDatumReader[IndexedRecord](schema)
    val decoder = DecoderFactory.get().binaryDecoder(new SeekableByteArrayInput(r.record.toByteArray), null)
    val record = datumReader.read(null, decoder)
    recordToJsonStringWithCustomEncoder(record)
  }

  private def recordToJsonStringWithCustomEncoder(record: IndexedRecord): String = {
    val datumWriter = new GenericDatumWriter[IndexedRecord](record.getSchema)
    val os = new ByteArrayOutputStream
    val encoder = new CustomJsonEncoder(record.getSchema, os)
    datumWriter.write(record, encoder)
    encoder.flush()
    os.toString()
  }

}

object PipelineStateForQuery {

  case class NodeRemoved(address: Address)

  case class NodeJoined(address: Address, state: NodeState)

  case object GetEnoughResourceNode

  case object GetNodeResources

  case class NodeResource(address: String, load: Double, totalCpu: Int, totalMemInMB: Int, freeCpu: Int, freeMemInMB: Int)

  case class GetTaskResult(id: String)
  case class TaskResult(jsonSeq: Seq[String])

}