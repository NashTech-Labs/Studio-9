package baile.daocommons.filters

/**
 * Filter for querying db.
 */
trait Filter {

  /**
   * Returns this and and that filters combined with AND operation.
   */
  def and(that: Filter): Filter = And(this, that)

  def &&(that: Filter): Filter = and(that)

  /**
   * Returns this and that filters combined with AND operation.
   */
  def or(that: Filter): Filter = Or(this, that)

  def ||(that: Filter): Filter = or(that)

  /**
   * Returns this filter negated.
   */
  def unary_! : Filter = Not(this) // scalastyle:off disallow.space.before.token

  /**
   * Returns this filter with simplified AND, OR and NOT operations.
   */
  def simplify: Filter = this match {
    case Or(left, right) => (left.simplify, right.simplify) match {
      case (TrueFilter, _) | (_, TrueFilter) => TrueFilter
      case (FalseFilter, f) => f
      case (f, FalseFilter) => f
      case (l, r) => Or(l, r)
    }
    case And(left, right) => (left.simplify, right.simplify) match {
      case (FalseFilter, _) | (_, FalseFilter) => FalseFilter
      case (TrueFilter, f) => f
      case (f, TrueFilter) => f
      case (l, r) => And(l, r)
    }
    case Not(Not(filter)) => filter.simplify
    case _ => this
  }

}
