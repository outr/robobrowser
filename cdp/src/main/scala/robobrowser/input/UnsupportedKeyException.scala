package robobrowser.input

case class UnsupportedKeyException(char: Char) extends RuntimeException(s"Unsupported key: $char")