package robobrowser.event

import reactify.Channel

case class TargetEvents(e: Events) {
  val attachedToTarget: Channel[AttachedToTargetEvent] = e.channel("Target.attachedToTarget")
  val detachedFromTarget: Channel[DetachedFromTargetEvent] = e.channel("Target.detachedFromTarget")
}
