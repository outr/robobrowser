package robobrowser.select

sealed trait Selector {
  def query: String
}

object Selector {
  case object All extends Selector {
    override lazy val query: String = "*"
  }
  case class Class(className: String) extends Selector {
    override lazy val query: String = s".$className"
  }
  case class Descendant(parent: Selector, child: Selector) extends Selector {
    override lazy val query: String = s"${parent.query} ${child.query}"
  }
  case class Id(id: String) extends Selector {
    override lazy val query: String = s"#$id"
  }
  case class Tag(tag: String) extends Selector {
    override lazy val query: String = tag
  }

  /**
   * Multiple different selectors
   */
  case class Multiple(selectors: List[Selector]) extends Selector {
    override lazy val query: String = selectors.map(_.query).mkString(", ")
  }

  /**
   * Combined selectors to clarify as a single selector
   */
  case class Combined(selectors: List[Selector]) extends Selector {
    override lazy val query: String = selectors.map(_.query).mkString
  }

  case class Adjacent(before: Selector, after: Selector) extends Selector {
    override lazy val query: String = s"${before.query} + ${after.query}"
  }

  case class Following(before: Selector, after: Selector) extends Selector {
    override lazy val query: String = s"${before.query} ~ ${after.query}"
  }

  case class Child(parent: Selector, child: Selector) extends Selector {
    override lazy val query: String = s"${parent.query} > ${child.query}"
  }
}