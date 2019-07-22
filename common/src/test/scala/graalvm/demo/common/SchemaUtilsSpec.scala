package graalvm.demo.common

import java.io.ByteArrayOutputStream

import graalvm.demo.common.utils.SchemaUtils
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.scalatest.{Matchers, WordSpec}

class SchemaUtilsSpec extends WordSpec with Matchers{

  "SchemaUtils" should {
    "get correct schema" in {
      val names = Vector("_id", "name")
      val types = Vector("string", "string")
      println(SchemaUtils(names, types, "demo"))
    }

/*    "Print json with long" in {
      val schema = SchemaBuilder.record("Test").fields().name("number").`type`("long").noDefault().endRecord()
      val record = new Record(schema)
      record.put(0, 1L)
      val output = new ByteArrayOutputStream()
      val jsonEncoder = EncoderFactory.get().jsonEncoder(schema, output)
      val writer = new GenericDatumWriter[Record](schema)
      writer.write(record, jsonEncoder)
      jsonEncoder.flush()
      println(output.toString)
    }*/
  }

}