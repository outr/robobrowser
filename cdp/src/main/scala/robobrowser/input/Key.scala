package robobrowser.input

sealed trait Key {
  def id: Int
  val char: Option[Char] = None
}

object Key {
  // Standard character keys
  case class Letter(value: Char) extends Key {
    require(value.isLetter, "Letter must be a valid alphabetic character")
    val id: Int = value.toUpper.toInt
    val isUpper: Boolean = value.isUpper

    override val char: Option[Char] = Some(value)
  }

  case class Digit(value: Int) extends Key {
    require(value >= 0 && value <= 9, "Digit must be between 0 and 9")
    val id: Int = 48 + value

    override val char: Option[Char] = Some(value.toString.charAt(0))
  }

  case object Space extends Key {
    val id: Int = 32

    override val char: Option[Char] = Some(' ')
  }
  case class Punctuation(value: Char) extends Key {
    val id: Int = value.toInt

    override val char: Option[Char] = Some(value)
  }
  case object Enter extends Key { val id: Int = 13 }
  case object Backspace extends Key { val id: Int = 8 }
  case object Tab extends Key {
    val id: Int = 9

    override val char: Option[Char] = Some('\t')
  }

  // Special keys
  case object Shift extends Key { val id: Int = 16 }
  case object Control extends Key { val id: Int = 17 }
  case object Alt extends Key { val id: Int = 18 }
  case object Meta extends Key { val id: Int = 91 } // Windows key or Command key
  case object Escape extends Key { val id: Int = 27 }
  case object ArrowUp extends Key { val id: Int = 38 }
  case object ArrowDown extends Key { val id: Int = 40 }
  case object ArrowLeft extends Key { val id: Int = 37 }
  case object ArrowRight extends Key { val id: Int = 39 }
  case object Delete extends Key { val id: Int = 46 }
  case object Insert extends Key { val id: Int = 45 }
  case object Home extends Key { val id: Int = 36 }
  case object End extends Key { val id: Int = 35 }
  case object PageUp extends Key { val id: Int = 33 }
  case object PageDown extends Key { val id: Int = 34 }

  // Function keys
  case object F1 extends Key { val id: Int = 112 }
  case object F2 extends Key { val id: Int = 113 }
  case object F3 extends Key { val id: Int = 114 }
  case object F4 extends Key { val id: Int = 115 }
  case object F5 extends Key { val id: Int = 116 }
  case object F6 extends Key { val id: Int = 117 }
  case object F7 extends Key { val id: Int = 118 }
  case object F8 extends Key { val id: Int = 119 }
  case object F9 extends Key { val id: Int = 120 }
  case object F10 extends Key { val id: Int = 121 }
  case object F11 extends Key { val id: Int = 122 }
  case object F12 extends Key { val id: Int = 123 }

  private val FunctionKeys: List[Key] = List(F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12)

  private val Named: List[(String, Key)] = List(
    "Enter" -> Enter, "Tab" -> Tab, "Backspace" -> Backspace, "Escape" -> Escape, "Delete" -> Delete,
    "Insert" -> Insert, "Home" -> Home, "End" -> End, "PageUp" -> PageUp, "PageDown" -> PageDown,
    "ArrowUp" -> ArrowUp, "ArrowDown" -> ArrowDown, "ArrowLeft" -> ArrowLeft, "ArrowRight" -> ArrowRight,
    "Shift" -> Shift, "Control" -> Control, "Alt" -> Alt, "Meta" -> Meta, "Space" -> Space
  ) ++ FunctionKeys.zipWithIndex.map { case (k, i) => s"F${i + 1}" -> k }

  /** The key a name stands for: a DOM key name ("Enter", "ArrowLeft", "F5", "Escape" — case ignored, "Esc" too) or a
    * single character. */
  def named(name: String): Option[Key] =
    Named.collectFirst { case (n, k) if n.equalsIgnoreCase(name) => k }
      .orElse(Option.when(name.equalsIgnoreCase("Esc"))(Escape))
      .orElse(Option.when(name.length == 1)(scala.util.Try(text(name).head).toOption).flatten)

  /** The DOM `key` and `code` values a browser reports for `key`, as `Input.dispatchKeyEvent` takes them. */
  def domNames(key: Key): (String, String) = key match {
    case Letter(c) => (c.toString, s"Key${c.toUpper}")
    case Digit(d) => (d.toString, s"Digit$d")
    case Space => (" ", "Space")
    case Punctuation(c) => (c.toString, "")
    case Shift => ("Shift", "ShiftLeft")
    case Control => ("Control", "ControlLeft")
    case Alt => ("Alt", "AltLeft")
    case Meta => ("Meta", "MetaLeft")
    case other => Named.collectFirst { case (n, k) if k == other => (n, n) }.getOrElse(("", ""))
  }

  // Converts a string into a list of keys
  def text(text: String): List[Key] = text.toList.map {
    case c if c.isDigit => Digit(c.asDigit)
    case c if c.isLetter => Letter(c)
    case ' ' => Space
    case '\n' => Enter
    case '\t' => Tab
    case c if "!@#$%^&*()-_=+[]{};:'\",.<>?/\\|`~".contains(c) => Punctuation(c)
    case c => throw UnsupportedKeyException(c)
  }
}