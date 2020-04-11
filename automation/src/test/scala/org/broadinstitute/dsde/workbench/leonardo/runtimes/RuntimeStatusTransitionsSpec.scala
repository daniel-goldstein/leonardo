package org.broadinstitute.dsde.workbench.leonardo.runtimes

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.leonardo._
import org.broadinstitute.dsde.workbench.service.RestException
import org.scalatest.{DoNotDiscover, ParallelTestExecution}

/**
 * This spec is for validating how Leonardo/Google handles cluster status transitions.
 *
 * Note these tests can take a long time so we don't test all edge cases, but these cases
 * should exercise the most commonly used paths through the system.
 */
@DoNotDiscover
class RuntimeStatusTransitionsSpec extends GPAllocFixtureSpec with ParallelTestExecution with LeonardoTestUtils {

  // these tests just hit the Leo APIs; they don't interact with notebooks via selenium
  "RuntimeStatusTransitionsSpec" - {

    implicit val ronToken: AuthToken = ronAuthToken

    "create, monitor, delete should transition correctly" in { billingProject =>
      logger.info("Starting RuntimeStatusTransitionsSpec: create, monitor, delete should transition correctly")

      val runtimeName = randomClusterName
      val runtimeRequest = defaultRuntimeRequest

      // create a runtime, but don't wait
      createNewRuntime(billingProject, runtimeName, runtimeRequest, monitor = false)

      // runtime status should be Creating
      val creatingRuntime = Leonardo.cluster.getRuntime(billingProject, runtimeName)
      creatingRuntime.status shouldBe ClusterStatus.Creating

      // can't create another runtime with the same name
      val caught = the[RestException] thrownBy createNewRuntime(billingProject, runtimeName, monitor = false)
      caught.message should include(""""statusCode":409""")

      // can't stop a Creating runtime
      val caught2 = the[RestException] thrownBy stopRuntime(billingProject, runtimeName, monitor = false)
      caught2.message should include(""""statusCode":409""")

      // wait for runtime to be running
      monitorCreateRuntime(billingProject, runtimeName, runtimeRequest, creatingRuntime)
      Leonardo.cluster.getRuntime(billingProject, runtimeName).status shouldBe ClusterStatus.Running

      // delete the runtime, but don't wait
      deleteRuntime(billingProject, runtimeName, monitor = false)

      // runtime status should be Deleting
      Leonardo.cluster.getRuntime(billingProject, runtimeName).status shouldBe ClusterStatus.Deleting

      // Call delete again. This should succeed, and not change the status.
      deleteRuntime(billingProject, runtimeName, monitor = false)
      Leonardo.cluster.getRuntime(billingProject, runtimeName).status shouldBe ClusterStatus.Deleting

      // Can't recreate while runtime is deleting
      val caught3 = the[RestException] thrownBy createNewRuntime(billingProject,
                                                                 runtimeName,
                                                                 runtimeRequest,
                                                                 monitor = false)
      caught3.message should include(""""statusCode":409""")

      // Wait for the runtime to be deleted
      monitorDeleteRuntime(billingProject, runtimeName)

      // New runtime can now be recreated with the same name
      // We monitor creation to make sure it gets successfully created in Google.
      withNewRuntime(billingProject, runtimeName, runtimeRequest, monitorCreate = true, monitorDelete = false)(noop)
    }

    "error'd runtimes should transition correctly" in { billingProject =>
      logger.info("Starting RuntimeStatusTransitionsSpec: error'd runtimes should transition correctly")

      // make an Error'd runtime
      withNewErroredRuntime(billingProject) { runtime =>
        // runtime should be in Error status
        runtime.status shouldBe ClusterStatus.Error

        // can't stop an Error'd runtime
        val caught = the[RestException] thrownBy stopRuntime(runtime.googleProject,
                                                             runtime.runtimeName,
                                                             monitor = false)
        caught.message should include(""""statusCode":409""")

        // can't recreate an Error'd runtime
        val caught2 = the[RestException] thrownBy createNewRuntime(runtime.googleProject,
                                                                   runtime.runtimeName,
                                                                   monitor = false)
        caught2.message should include(""""statusCode":409""")

        // can delete an Error'd runtime
      }
    }
    // Note: omitting stop/start and patch/update tests here because those are covered in more depth in NotebookClusterMonitoringSpec
  }

}
