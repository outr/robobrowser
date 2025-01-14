package com.outr.robobrowser.integration

import com.outr.robobrowser.RoboBrowser

case class IntegrationTestsInstance[Browser <: RoboBrowser](create: () => IntegrationTests[Browser])