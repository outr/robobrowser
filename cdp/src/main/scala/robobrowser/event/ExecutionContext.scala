package robobrowser.event

import fabric.rw.*

case class ExecutionContext(id: Int,
                            origin: String,
                            name: String,
                            uniqueId: String,
                            auxData: AuxData)

object ExecutionContext {
  implicit val rw: RW[ExecutionContext] = RW.gen
}