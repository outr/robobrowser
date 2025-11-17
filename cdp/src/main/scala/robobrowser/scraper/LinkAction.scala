package robobrowser.scraper

sealed trait LinkAction

object LinkAction {
  case object Nothing extends LinkAction

  case object Include extends LinkAction

  case object Exclude extends LinkAction
}