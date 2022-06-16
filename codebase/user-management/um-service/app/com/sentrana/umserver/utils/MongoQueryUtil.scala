package com.sentrana.umserver.utils

import java.util.regex.Pattern

import com.sentrana.umserver.shared.dtos.enums.SortOrder
import org.bson.conversions.Bson
import org.mongodb.scala.bson.collection.immutable.Document

/**
 * Created by Paul Lysak on 20.06.16.
 */
object MongoQueryUtil {
  import org.mongodb.scala.model.Filters._

  def buildSortDocOpt(sortParams: Map[String, SortOrder], availableFields: Set[String]): Option[Bson] = {
    val relevantSortParams = sortParams.filter({
      case (k, _) => availableFields.contains(k)
    })
    val sortDocs = relevantSortParams.map {
      case (k, v) =>
        equal(k, if (v == SortOrder.ASC) 1 else -1)
    }.toSeq
    if (sortDocs.isEmpty) None else Some(and(sortDocs: _*))
  }

  def buildFilterDoc(criteria: Map[String, Seq[String]], availableFields: Set[String]): Bson = {
    val relevantCriteria = criteria.filter({
      case (k, _) =>
        availableFields.contains(k) ||
          availableFields.contains(k.stripSuffix(F_NOT)) ||
          availableFields.contains(k.stripSuffix(F_PREFIX)) ||
          availableFields.contains(k.stripSuffix(F_CONTAINS)) ||
          availableFields.contains(k.stripSuffix(F_CASE_INSENSITIVE))
    })
    criteriaMapToDoc(relevantCriteria)
  }

  /**
   * When building criteria values are ORed, and then keys are ANDed
   *
   * @param criteria key-field, value-allowed values
   * @return
   */
  private def criteriaMapToDoc(criteria: Map[String, Seq[String]]): Bson = {
    val fieldFilters = (for ((field, values) <- criteria) yield {
      values.map(v => mkCriterion(field, v)) match {
        case Nil              => Nil
        case v if v.size == 1 => v
        case v                => Seq(or(v: _*))
      }
    }).toSeq.flatten
    fieldFilters match {
      case Nil      => Document()
      case f :: Nil => f
      case fs       => and(fs: _*)
    }
  }

  private def mkCriterion(k: String, v: String) = {
    if (k.endsWith(F_NOT))
      notEqual(k.stripSuffix(F_NOT), v)
    else if (k.endsWith(F_PREFIX))
      regex(k.stripSuffix(F_PREFIX), s"^${Pattern.quote(v)}", "i")
    else if (k.endsWith(F_CONTAINS))
      regex(k.stripSuffix(F_CONTAINS), Pattern.quote(v), "i")
    else if (k.endsWith(F_CASE_INSENSITIVE))
      regex(k.stripSuffix(F_CASE_INSENSITIVE), s"^${Pattern.quote(v)}$$", "i")
    else
      equal(k, v)
  }

  private val F_NOT = "_not"

  private val F_PREFIX = "_prefix"

  private val F_CONTAINS = "_contains"

  private val F_CASE_INSENSITIVE = "_case_insensitive"

}
