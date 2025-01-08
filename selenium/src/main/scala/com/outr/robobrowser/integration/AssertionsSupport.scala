package com.outr.robobrowser.integration

trait AssertionsSupport {
  implicit class Assertions[T](value: T) {
    def should(comparison: Comparison[T]): Unit = comparison.compareWith(value)
    def shouldNot(comparison: Comparison[T]): Unit = comparison.compareNot(value)
  }

  def contain[T, S <: Seq[T]](items: T*): Comparison[S] = Comparison[S](
    f = value => items.forall(i => value.contains(i)),
    failMessage = value => s"[${value.mkString(", ")}] did not contain all of the values in [${items.mkString(", ")}]",
    failNotMessage = value => s"[${value.mkString(", ")}] contained all the values in [${items.mkString(", ")}]"
  )

  object be {
    def apply[T](expected: T, failMessageOverride: => String = null): Comparison[T] = Comparison[T](
      f = value => value == expected,
      failMessage = value => s"'$value' was not equal to '$expected'",
      failNotMessage = value => s"'$value' was equal to '$expected'",
      failMessageOverride = if (failMessageOverride == null) None else Some(() => failMessageOverride)
    )
    def <[T : Ordering](expected: T): Comparison[T] = {
      val ordering = implicitly[Ordering[T]]
      Comparison[T](
        f = value => ordering.lt(value, expected),
        failMessage = value => s"$value was not < $expected",
        failNotMessage = value => s"$value was < $expected"
      )
    }
    def >[T : Ordering](expected: T): Comparison[T] = {
      val ordering = implicitly[Ordering[T]]
      Comparison[T](
        f = value => ordering.gt(value, expected),
        failMessage = value => s"$value was not > $expected",
        failNotMessage = value => s"$value was > $expected"
      )
    }
  }
}
