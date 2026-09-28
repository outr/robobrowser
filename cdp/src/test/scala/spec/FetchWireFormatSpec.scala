package spec

import fabric.*
import fabric.rw.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.fetch.{ErrorReason, RequestPattern, RequestStage, ResourceType}

/**
 * The Fetch domain's enumerated values go to Chrome as the bare strings the protocol names — an error reason, a
 * resource type, a request stage — which Chrome rejects in any other form.
 */
class FetchWireFormatSpec extends AnyWordSpec with Matchers {
  "Fetch enumerations" should {
    "write as the protocol's strings" in {
      ErrorReason.BlockedByClient.asInstanceOf[ErrorReason].json shouldBe str("BlockedByClient")
      ResourceType.XHR.asInstanceOf[ResourceType].json shouldBe str("XHR")
      RequestStage.Response.asInstanceOf[RequestStage].json shouldBe str("Response")
      RequestPattern(resourceType = Some(ResourceType.Document), requestStage = Some(RequestStage.Request)).json shouldBe
        obj("urlPattern" -> str("*"), "resourceType" -> str("Document"), "requestStage" -> str("Request"))
    }
    "read back from them" in {
      str("BlockedByClient").as[ErrorReason] shouldBe ErrorReason.BlockedByClient
      str("CSPViolationReport").as[ResourceType] shouldBe ResourceType.CSPViolationReport
    }
  }
}
