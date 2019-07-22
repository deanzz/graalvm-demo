package graalvm.demo.node.transformers

import akka.NotUsed
import akka.stream.scaladsl.Flow
import graalvm.demo.common.UserDefinedFilter
import graalvm.demo.common.utils.{FilterUtil, RecordUtil}
import graalvm.demo.filter.{ConditionRelationType, FilterParam}
import graalvm.demo.node.pipeline.flow.StatelessConditionFlow
import org.apache.avro.generic.IndexedRecord

class SimpleStatelessFilter(val param: FilterParam) extends UserDefinedFilter[FilterParam, NotUsed]{

  override def condition(): IndexedRecord => Boolean = r => {
      param.fieldFilters.foldLeft(param.relation == ConditionRelationType.AND) {
        case (isTrue, f) =>
          val value = RecordUtil.getStringFieldValue(r, f.field).getOrElse("")
          val condRes = f.conditionList.map {
            c =>
              FilterUtil.filter(c.condition, c.values, value)
          }.reduce(_ || _)
          param.relation match {
            case ConditionRelationType.AND => isTrue && condRes
            case ConditionRelationType.OR => isTrue || condRes
          }
      }
  }

  override def createFlow(): Flow[IndexedRecord, IndexedRecord, NotUsed] = Flow.fromGraph(StatelessConditionFlow(condition()))

}
