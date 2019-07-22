package graalvm.demo.common

//import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

import akka.actor.ExtendedActorSystem
import akka.serialization.BaseSerializer
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

/**
  * Created by wanglei on 16-11-15.
  */
class ScalaProtobufSerializer(val system: ExtendedActorSystem) extends BaseSerializer {

  override def includeManifest = true

  private val cachedAccessors = new ConcurrentHashMap[Class[_], GeneratedMessageCompanion[_ <: GeneratedMessage]]()

  def bindingFactoryFor(c: Class[_]): GeneratedMessageCompanion[_ <: GeneratedMessage] = {
    val obj = cachedAccessors.get(c)
    if (obj != null) {
      obj
    }
    else {
      val cl = c.getClassLoader
      val compClazz = cl.loadClass(c.getName + "$")
      val fld = compClazz.getField("MODULE$")
      //assert(Modifier.isStatic(fld.getModifiers), "MODULE$ field was not static")
      val companion = fld.get(null).asInstanceOf[GeneratedMessageCompanion[_ <: GeneratedMessage]]
      cachedAccessors.put(c, companion)
      companion
    }
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]) = {
    manifest match {
      case None => throw new Exception("can't deserialize something, no manifest")
      case Some(c) =>
        val factory = bindingFactoryFor(c)
        factory.parseFrom(bytes).asInstanceOf[AnyRef]
    }
  }

  override def toBinary(o: AnyRef) = {
    o match {
      case m: GeneratedMessage =>
        m.toByteArray
      case _ =>
        throw new IllegalArgumentException(s"can't serialize objects that do not inherit from ${classOf[GeneratedMessage].getCanonicalName}")
    }
  }
}
