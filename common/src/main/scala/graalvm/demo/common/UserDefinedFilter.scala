package graalvm.demo.common

import akka.stream.scaladsl.Flow
import org.apache.avro.generic.IndexedRecord

/**
 * 
 *
 * @author wanglei
 * @since 2019-05-17 10:52
 */
trait UserDefinedFilter[PARAM, MAT] {

  def param: PARAM

  def condition(): IndexedRecord => Boolean

  def createFlow(): Flow[IndexedRecord, IndexedRecord, MAT]
}
