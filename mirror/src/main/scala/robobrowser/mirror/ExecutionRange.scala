package robobrowser.mirror

private[mirror] object ExecutionRange {
  def range(header:Option[String],length:Int):Option[(Int,Int)]={
    if(length<=0)return None
    header match {
      case None=>Some((0,length-1))
      case Some(value)=>
        val pattern="bytes=([0-9]*)-([0-9]*)".r
        value match {
          case pattern(a,b)=>scala.util.Try {
            require(a.nonEmpty||b.nonEmpty)
            val start=if(a.isEmpty)(length.toLong-b.toLong).max(0)else a.toLong
            val end=if(a.isEmpty||b.isEmpty)length.toLong-1 else b.toLong.min(length.toLong-1)
            require(start>=0&&start<length&&end>=start);(start.toInt,end.toInt)
          }.toOption
          case _=>None
        }
    }
  }
}
