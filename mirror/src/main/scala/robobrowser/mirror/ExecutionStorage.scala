package robobrowser.mirror

import fabric.*
import fabric.io.JsonFormatter
import java.nio.charset.StandardCharsets.UTF_8

/** Origin-scoped snapshots with optimistic concurrency. Only acknowledged snapshots are durable. */
private[mirror] final class ExecutionStorage(initial:Json,persist:Json=>Unit) {
  private var origins=initial.asObj.value.toMap
  private val empty=obj("revision"->num(0),"local"->obj(),"databases"->arr(),"sessions"->obj())
  def snapshot:Json=synchronized {obj(origins.toList*)}
  def read(origin:String):Json=synchronized {origins.getOrElse(origin,empty)}
  def write(origin:String,c:Json):Json=synchronized {
    val current=read(origin)
    val namespace=c("namespace").asString
    require(namespace.matches("[A-Za-z0-9_-]{1,80}"),"Invalid storage namespace")
    def strings(value:Json):Unit={require(value.asObj.value.size<=10000,"Too many storage keys");value.asObj.value.foreach{case(k,v)=>require(k.length<=65536&&v.isInstanceOf[Str],"Invalid storage value")}}
    strings(c("local"));strings(c("session"))
    require(c("databases").asVector.size<=128,"Too many databases")
    val sessions=current("sessions").asObj.value.toMap.updated(namespace,c("session"))
    require(sessions.size<=128,"Too many session storage namespaces")
    val candidate=obj("revision"->current("revision"),"local"->c("local"),"databases"->c("databases"),"sessions"->obj(sessions.toList*))
    if(candidate==current)return current // A lost acknowledgement is safe to retry.
    require(c("revision")==current("revision"),"Storage conflict: another executor changed this origin; pending local data was retained")
    val next=obj((candidate.asObj.value.toList.filterNot(_._1=="revision"):+("revision"->num(current("revision").asLong+1)))* )
    val updated=origins.updated(origin,next)
    val all=obj(updated.toList*)
    require(JsonFormatter.Compact(all).getBytes(UTF_8).length<=12*1024*1024,"Persistent browser storage exceeds 12 MiB")
    persist(all) // Commit in memory only after the profile's atomic write succeeds.
    origins=updated
    next
  }
}
