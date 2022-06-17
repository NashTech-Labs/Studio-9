package baile.services.dcproject

import java.time.Instant
import java.util.UUID

import baile.RandomGenerators.{ randomOf, randomPath, randomString }
import baile.daocommons.WithId
import baile.domain.common.Version
import baile.domain.dcproject.{ DCProject, DCProjectStatus }

object DCProjectRandomGenerator {

  def randomDCProject(
    ownerId: UUID = UUID.randomUUID(),
    name: String = randomString(),
    status: DCProjectStatus = randomOf(DCProjectStatus.Idle, DCProjectStatus.Building),
    packageName: Option[String] = randomOf(None, Some(randomString())),
    basePath: String = randomPath(),
    description: Option[String] = randomOf(None, Some(randomString())),
    latestPackageVersion: Option[Version] = None
  ): WithId[DCProject] = WithId(
    DCProject(
      ownerId = ownerId,
      status = status,
      name = name,
      packageName = packageName,
      basePath = basePath,
      description = description,
      created = Instant.now(),
      updated = Instant.now(),
      latestPackageVersion = latestPackageVersion
    ),
    randomString()
  )

}
