package org.broadinstitute.dsde.workbench.leonardo
package http

import org.broadinstitute.dsde.workbench.azure.{AzureAppRegistrationConfig, ClientId, ClientSecret, ManagedAppTenantId}
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName.ServiceName
import org.broadinstitute.dsde.workbench.google2.ZoneName
import org.broadinstitute.dsde.workbench.leonardo.config.{CoaAppConfig, HttpWsmDaoConfig, PersistentDiskConfig}
import org.broadinstitute.dsde.workbench.leonardo.http.service.{
  AzureRuntimeDefaults,
  CustomScriptExtensionConfig,
  VMCredential
}
import org.broadinstitute.dsde.workbench.leonardo.monitor.{LeoMetricsMonitorConfig, PollMonitorConfig}
import org.broadinstitute.dsde.workbench.leonardo.util.{AzurePubsubHandlerConfig, TerraAppSetupChartConfig}
import org.broadinstitute.dsp._
import org.http4s.Uri
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ConfigReaderSpec extends AnyFlatSpec with Matchers {
  it should "read config file correctly" in {
    val config = ConfigReader.appConfig
    val expectedConfig = AppConfig(
      TerraAppSetupChartConfig(ChartName("/leonardo/terra-app-setup"), ChartVersion("0.0.8")),
      PersistentDiskConfig(
        DiskSize(30),
        DiskType.Standard,
        BlockSize(4096),
        ZoneName("us-central1-a"),
        DiskSize(250),
        Vector("bogus")
      ),
      AzureConfig(
        AzurePubsubHandlerConfig(
          Uri.unsafeFromString("https://sam.test.org:443"),
          Uri.unsafeFromString("https://localhost:8000"),
          "terradevacrpublic.azurecr.io/welder-server",
          "6648f5c",
          PollMonitorConfig(1 seconds, 10, 1 seconds),
          PollMonitorConfig(1 seconds, 20, 1 seconds),
          AzureRuntimeDefaults(
            "Azure Ip",
            "ip",
            "Azure Network",
            "network",
            "subnet",
            CidrIP("192.168.0.0/16"),
            CidrIP("192.168.0.0/24"),
            "Azure Disk",
            "Azure Vm",
            AzureImage(
              "microsoft-dsvm",
              "ubuntu-2004",
              "2004-gen2",
              "22.04.27"
            ),
            CustomScriptExtensionConfig(
              "vm-custom-script-extension",
              "Microsoft.Azure.Extensions",
              "CustomScript",
              "2.1",
              true,
              List(
                "https://raw.githubusercontent.com/DataBiosphere/leonardo/4ff00726e7ec507f03e2d6049a7ff56aea7bbbfc/http/src/main/resources/init-resources/azure_vm_init_script.sh"
              )
            ),
            "terradevacrpublic.azurecr.io/terra-azure-relay-listeners:3a932af",
            VMCredential(username = "username", password = "password")
          )
        ),
        HttpWsmDaoConfig(Uri.unsafeFromString("https://localhost:8000")),
        AzureAppRegistrationConfig(ClientId(""), ClientSecret(""), ManagedAppTenantId("")),
        CoaAppConfig(
          ChartName("/leonardo/cromwell-on-azure"),
          ChartVersion("0.2.213"),
          ReleaseNameSuffix("coa-rls"),
          NamespaceNameSuffix("coa-ns"),
          KsaName("coa-ksa"),
          List(
            ServiceConfig(ServiceName("cbas"), KubernetesServiceKindName("ClusterIP")),
            ServiceConfig(ServiceName("cbas-ui"), KubernetesServiceKindName("ClusterIP"), Some(ServicePath("/"))),
            ServiceConfig(ServiceName("wds"), KubernetesServiceKindName("ClusterIP")),
            ServiceConfig(ServiceName("cromwell"), KubernetesServiceKindName("ClusterIP"))
          ),
          instrumentationEnabled = false
        ),
        AadPodIdentityConfig(
          Namespace("aad-pod-identity"),
          Release("aad-pod-identity"),
          ChartName("aad-pod-identity/aad-pod-identity"),
          ChartVersion("4.1.14"),
          Values("operationMode=managed")
        ),
        List.empty
      ),
      OidcAuthConfig(
        Uri.unsafeFromString("https://fake"),
        org.broadinstitute.dsde.workbench.oauth2.ClientId("fakeClientId"),
        Some(org.broadinstitute.dsde.workbench.oauth2.ClientSecret("fakeClientSecret")),
        org.broadinstitute.dsde.workbench.oauth2.ClientId("legacyClientSecret")
      ),
      DrsConfig(
        "https://drshub.dsde-dev.broadinstitute.org/api/v4/drs/resolve"
      ),
      LeoMetricsMonitorConfig(true, 5 minutes, true)
    )

    config shouldBe expectedConfig
  }
}
