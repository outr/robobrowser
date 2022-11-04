//package com.outr.robobrowser
//
//import java.util
//
//import scala.jdk.CollectionConverters._
//
//class RoboBrowserBuilder[T <: RoboBrowser](val creator: Capabilities => T,
//                                           map: Map[String, Any] = Map.empty,
//                                           prefs: util.HashMap[String, Any] = new util.HashMap) extends Capabilities(map, prefs) {
//  override type C = RoboBrowserBuilder[T]
//
//  def create(): T = creator(this)
//
//  override def ++(that: Capabilities): RoboBrowserBuilder[T] = {
//    that.prefs.asScala.foreach {
//      case (key, value) => prefs.put(key, value)
//    }
//    new RoboBrowserBuilder[T](creator, this.map ++ that.map, prefs)
//  }
//
//  override def withCapabilities(pairs: (String, Any)*): RoboBrowserBuilder[T] = ++(Capabilities(pairs: _*))
//
//  def withCreator[R <: RoboBrowser](creator: Capabilities => R): RoboBrowserBuilder[R] = new RoboBrowserBuilder[R](creator, map, prefs)
//}