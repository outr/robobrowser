package robobrowser

import robobrowser.comm.CDPQueryResult

trait TabSelector {
  def select(list: List[CDPQueryResult]): Option[CDPQueryResult]
}

object TabSelector {
  case object AlwaysCreateNew extends TabSelector {
    override def select(list: List[CDPQueryResult]): Option[CDPQueryResult] = None
  }

  case object FirstPage extends TabSelector {
    override def select(list: List[CDPQueryResult]): Option[CDPQueryResult] = list.find(_.`type` == "page")
  }
}