package graalvm.demo.common

import akka.stream.scaladsl.Source
import org.apache.avro.Schema

/**
 * Description: User defined data source
 * Temporary exist in akka stream, life should end with stream completion
 * Remember to close it.
 *
 * @author wanglei
 * @since 2019-03-07 11:17
 * @version 1.0.0
 */
abstract class UserDefinedSource[PARAM, OUT, MAT] {

  /**
   * Remember to close the connection or session to release resource
   */
  def close()

  def create(param: PARAM): Source[OUT, MAT]

  def start(): Unit = ()

  def getSchema(param: PARAM): Option[Schema] = None

}
