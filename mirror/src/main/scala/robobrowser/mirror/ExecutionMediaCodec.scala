package robobrowser.mirror

import java.nio.file.{Files,Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

private[mirror] trait ExecutionMediaDecoder extends AutoCloseable {
  def convert(bytes:Array[Byte],metadata:(Double,String)=>Unit,append:Array[Byte]=>Unit):Unit
}
private[mirror] object ExecutionMediaCodec {case object NotNeeded extends RuntimeException("No unsupported HEVC video track")}

/** Sandboxed conversion of bounded, already fetched media bytes. No decoder network access. */
private[mirror] final class ExecutionMediaCodec extends ExecutionMediaDecoder {
  @volatile private var process:Process=null
  @volatile private var closed=false
  private val limit=16*1024*1024
  private def run(args:List[String],log:Path,seconds:Int,progress:()=>Unit=()=>()):Unit={
    require(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux"),"Host codec fallback requires a supported decoder sandbox")
    val directory=log.getParent.toAbsolutePath
    val libraries=List("/lib","/lib64").flatMap { name=>
      val path=Path.of(name)
      if(Files.isSymbolicLink(path))List("--symlink",Files.readSymbolicLink(path).toString,name)
      else if(Files.exists(path))List("--ro-bind",name,name)else Nil
    }
    val loader=if(Files.exists(Path.of("/etc/ld.so.cache")))List("--ro-bind","/etc/ld.so.cache","/etc/ld.so.cache")else Nil
    val command=List("bwrap","--unshare-all","--die-with-parent","--new-session","--cap-drop","ALL","--ro-bind","/usr","/usr"):::libraries:::loader:::
      List("--proc","/proc","--dev","/dev","--tmpfs","/tmp","--bind",directory.toString,"/work","--chdir","/work","--clearenv","--setenv","PATH","/usr/local/bin:/usr/bin",
        // Avoid per-thread glibc arenas exhausting the address-space budget before actual decoder memory does.
        "--setenv","MALLOC_ARENA_MAX","2","--",
        "prlimit","--as=1073741824","--fsize=20971520",s"--cpu=${seconds+2}","--"):::args.map(a=>if(a.startsWith(directory.toString+"/"))"/work/"+Path.of(a).getFileName.toString else a)
    require(!closed,"Media recovery closed")
    val p=new ProcessBuilder(command.asJava).redirectErrorStream(true).redirectOutput(log.toFile).start();process=p
    try{
      val deadline=System.nanoTime()+seconds.toLong*1000000000L
      while(!p.waitFor(50,TimeUnit.MILLISECONDS)){progress();require(System.nanoTime()<deadline,s"${args.head} exceeded ${seconds}s media conversion budget")}
      require(p.exitValue()==0,s"${args.head} failed (exit ${p.exitValue()}): "+Files.readString(log).takeRight(500));progress()
    }
    finally{if(p.isAlive)p.destroyForcibly();process=null}
  }

  def convert(bytes:Array[Byte],metadata:(Double,String)=>Unit,append:Array[Byte]=>Unit):Unit=synchronized {
    require(!closed,"Media recovery closed");require(bytes.length<=limit,"Media exceeds 16 MiB")
    val directory=Files.createTempDirectory("robobrowser-execution-media-")
    try {
      val input=directory.resolve("input");val output=directory.resolve("output.mp4");val log=directory.resolve("process.log")
      Files.write(input,bytes)
      run(List("ffprobe","-v","error","-protocol_whitelist","file,crypto,data","-show_entries","stream=codec_name,codec_type:format=duration","-of","json",input.toString),log,5)
      val info=fabric.io.JsonParser(Files.readString(log));if(!info("streams").asVector.exists(s=>s.get("codec_type").exists(_.asString=="video")&&s.get("codec_name").exists(_.asString=="hevc")))throw ExecutionMediaCodec.NotNeeded
      val duration=info("format")("duration").asString.toDouble;require(duration>0&&duration<=120,"Media exceeds two-minute limit")
      val audio=info("streams").asVector.exists(_.get("codec_type").exists(_.asString=="audio"))
      metadata(duration,if(audio)"video/mp4; codecs=\"avc1.4D4029, mp4a.40.2\""else"video/mp4; codecs=\"avc1.4D4029\"")
      var offset=0L
      def publish():Unit=if(Files.exists(output)){
        val size=Files.size(output);require(size<=limit,"Media output exceeds 16 MiB")
        if(size>offset){val file=new java.io.RandomAccessFile(output.toFile,"r");try{file.seek(offset);while(offset<size){val b=new Array[Byte](math.min(65536L,size-offset).toInt);file.readFully(b);append(b);offset+=b.length}}finally file.close()}
      }
      val decoderThreads=Runtime.getRuntime.availableProcessors().min(4).max(1).toString
      run(List("ffmpeg","-nostdin","-v","error","-protocol_whitelist","file,crypto,data","-threads",decoderThreads,"-filter_threads","2","-i",input.toString,"-map","0:v:0","-map","0:a:0?","-vf","scale=w='min(1280,iw)':h=-2","-c:v","libx264","-preset","veryfast","-crf","22","-threads","2","-pix_fmt","yuv420p","-profile:v","main","-level:v","4.1","-g","30","-keyint_min","30","-sc_threshold","0","-c:a","aac","-profile:a","aac_low","-movflags","empty_moov+frag_keyframe+default_base_moof","-flush_packets","1","-fs",limit.toString,output.toString),log,60,()=>publish())
      require(!closed&&Files.size(output)<limit,"Media recovery closed or output too large")
      require(offset==Files.size(output),"Incomplete media output")
    }finally{val files=Files.list(directory);try files.iterator().asScala.foreach(Files.deleteIfExists)finally files.close();Files.deleteIfExists(directory)}
  }
  def close():Unit={closed=true;Option(process).foreach(_.destroyForcibly())}
}
