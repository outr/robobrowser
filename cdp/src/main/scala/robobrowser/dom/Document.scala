package robobrowser.dom

import fabric.rw._

case class Document(nodeId: Int,
                    nodeType: Int,
                    nodeName: String,
                    localName: Option[String] = None,
                    nodeValue: String,
                    parentId: Option[Int] = None,
                    backendNodeId: Int,
                    childNodeCount: Option[Int] = None,
                    children: Option[List[Document]] = None,
                    attributes: Option[List[String]] = None,
                    documentURL: Option[String] = None,
                    baseURL: Option[String] = None,
                    publicId: Option[String] = None,
                    systemId: Option[String] = None,
                    xmlVersion: Option[String] = None,
                    contentDocument: Option[Document] = None)

object Document {
  implicit val rw: RW[Document] = RW.gen
}