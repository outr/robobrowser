package com.outr.robobrowser

class RoboBrowserBuilder[T <: RoboBrowser](val creator: Capabilities => T,
                                           val map: Map[String, Any] = Map.empty) extends Capabilities {
  override type C = RoboBrowserBuilder[T]

  def create(): T = creator(this)

  override def ++(that: Capabilities): RoboBrowserBuilder[T] = new RoboBrowserBuilder[T](creator, this.map ++ that.map)

  override def withCapabilities(pairs: (String, Any)*): RoboBrowserBuilder[T] = ++(Capabilities(pairs: _*))

  def withCreator[R <: RoboBrowser](creator: Capabilities => R): RoboBrowserBuilder[R] = new RoboBrowserBuilder[R](creator, map)
}