package taurus.util.akka.persistence.mongo

import akka.contrib.persistence.mongodb.CanSuffixCollectionNames

class PersistenceCollectionSuffixBuilder extends CanSuffixCollectionNames {

  override def getSuffixFromPersistenceId(persistenceId: String): String =
    persistenceId
      .replaceAll("online-prediction-dispatcher", "opd") //To make collection name not more than limited by MongoDB

  override def validateMongoCharacters(input: String): String = {
    //See https://docs.mongodb.com/manual/reference/limits/#naming-restrictions
    val forbidden = List('/', '\\', '.', ' ', '\"', '$', '*', '<', '>', ':', '|', '?')

    input.collect {
      case c if forbidden.contains(c) => '_'
      case c                          => c
    }
  }

}
