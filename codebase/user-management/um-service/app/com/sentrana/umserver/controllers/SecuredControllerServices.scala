package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.services.{ UserConverter, UserGroupQueryService, OrganizationQueryService, AuthenticationService }

/**
 * There are too much services required by ServerSecuredController to make every controller depend on them,
 * so we're going to pack them in this class
 *
 * Created by Paul Lysak on 28.06.16.
 */
@Singleton
class SecuredControllerServices @Inject() (
    val authenticationService: AuthenticationService,
    val orgQueryService:       OrganizationQueryService,
    val userGroupQueryService: UserGroupQueryService,
    val userConverter:         UserConverter
) {

}
