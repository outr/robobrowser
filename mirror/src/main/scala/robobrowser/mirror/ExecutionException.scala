package robobrowser.mirror

/** A rejected or uncertain local execution command. */
final class ExecutionException(val code:String,message:String,val unknown:Boolean=false) extends IllegalStateException(message)
