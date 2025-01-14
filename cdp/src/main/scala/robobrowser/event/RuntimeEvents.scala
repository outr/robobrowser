package robobrowser.event

import reactify.Channel

case class RuntimeEvents(e: Events) {
  val consoleAPICalled: Channel[ConsoleAPICalledEvent] = e.channel("Runtime.consoleAPICalled")
  val exceptionThrown: Channel[ExceptionThrownEvent] = e.channel("Runtime.exceptionThrown")
  val executionContextCreated: Channel[ExecutionContextCreatedEvent] = e.channel("Runtime.executionContextCreated")
  val executionContextDestroyed: Channel[ExecutionContextDestroyedEvent] = e.channel("Runtime.executionContextDestroyed")
  val executionContextsCleared: Channel[ExecutionContextsClearedEvent] = e.channel("Runtime.executionContextsCleared")
}