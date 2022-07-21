package org.broadinstitute.dsde.workbench.leonardo
package dao

import cats.effect.Async
import cats.implicits._
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.azure.RelayNamespace
import org.broadinstitute.dsde.workbench.leonardo.config.HttpWsmDaoConfig
import org.broadinstitute.dsde.workbench.leonardo.dao.WsmDecoders._
import org.broadinstitute.dsde.workbench.leonardo.dao.WsmEncoders._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.{`Content-Type`, Authorization}
import org.typelevel.ci.CIString
import org.typelevel.log4cats.StructuredLogger

class HttpWsmDao[F[_]](httpClient: Client[F], config: HttpWsmDaoConfig)(implicit
  logger: StructuredLogger[F],
  F: Async[F],
  metrics: OpenTelemetryMetrics[F]
) extends WsmDao[F]
    with Http4sClientDsl[F] {

  val defaultMediaType = `Content-Type`(MediaType.application.json)

  override def createIp(request: CreateIpRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[CreateIpResponse] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOr[CreateIpResponse](
        Request[F](
          method = Method.POST,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${request.workspaceId.value.toString}/resources/controlled/azure/ip"
                )
            ),
          entity = request,
          headers = headers(authorization, ctx.traceId, true)
        )
      )(onError)
    } yield res

  override def createDisk(request: CreateDiskRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[CreateDiskResponse] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOr[CreateDiskResponse](
        Request[F](
          method = Method.POST,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${request.workspaceId.value.toString}/resources/controlled/azure/disks"
                )
            ),
          entity = request,
          headers = headers(authorization, ctx.traceId, true)
        )
      )(onError)
    } yield res

  override def createNetwork(request: CreateNetworkRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[CreateNetworkResponse] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOr[CreateNetworkResponse](
        Request[F](
          method = Method.POST,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${request.workspaceId.value.toString}/resources/controlled/azure/network"
                )
            ),
          entity = request,
          headers = headers(authorization, ctx.traceId, true)
        )
      )(onError)
    } yield res

  override def createVm(request: CreateVmRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[CreateVmResult] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOr[CreateVmResult](
        Request[F](
          method = Method.POST,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${request.workspaceId.value.toString}/resources/controlled/azure/vm"
                )
            ),
          entity = request,
          headers = headers(authorization, ctx.traceId, true)
        )
      )(onError)
    } yield res

  override def getWorkspace(workspaceId: WorkspaceId, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[Option[WorkspaceDescription]] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOptionOr[WorkspaceDescription](
        Request[F](
          method = Method.GET,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(s"/api/workspaces/v1/${workspaceId.value.toString}")
            ),
          headers = headers(authorization, ctx.traceId, false)
        )
      )(onError)
    } yield res

  override def deleteVm(request: DeleteWsmResourceRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[Option[DeleteWsmResourceResult]] =
    deleteHelper(request, authorization, "vm")

  override def deleteDisk(request: DeleteWsmResourceRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[Option[DeleteWsmResourceResult]] =
    deleteHelper(request, authorization, "disks")

  override def deleteIp(request: DeleteWsmResourceRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[Option[DeleteWsmResourceResult]] =
    deleteHelper(request, authorization, "ip")

  override def deleteNetworks(request: DeleteWsmResourceRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[Option[DeleteWsmResourceResult]] =
    deleteHelper(request, authorization, "network")

  override def getCreateVmJobResult(request: GetJobResultRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[GetCreateVmJobResult] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOr[GetCreateVmJobResult](
        Request[F](
          method = Method.GET,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${request.workspaceId.value.toString}/resources/controlled/azure/vm/create-result/${request.jobId.value}"
                )
            ),
          headers = headers(authorization, ctx.traceId, false)
        )
      )(onError)
    } yield res

  override def getDeleteVmJobResult(request: GetJobResultRequest, authorization: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[Option[GetDeleteJobResult]] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOptionOr[GetDeleteJobResult](
        Request[F](
          method = Method.GET,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${request.workspaceId.value.toString}/resources/controlled/azure/vm/delete-result/${request.jobId.value}"
                )
            ),
          headers = headers(authorization, ctx.traceId, false)
        )
      )(onError)
    } yield res

  def getRelayNamespace(workspaceId: WorkspaceId,
                        region: com.azure.core.management.Region,
                        authorization: Authorization
  )(implicit
    ev: Ask[F, AppContext]
  ): F[Option[RelayNamespace]] =
    for {
      ctx <- ev.ask
      resp <- httpClient.expectOr[GetRelayNamespace](
        Request[F](
          method = Method.GET,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${workspaceId.value}/resources"
                )
            )
            .withMultiValueQueryParams(Map("resource" -> List("AZURE_RELAY_NAMESPACE"))),
          headers = headers(authorization, ctx.traceId, false)
        )
      )(onError)
    } yield resp.resources.collect {
      case r if r.resourceAttributes.relayNamespace.region == region =>
        r.resourceAttributes.relayNamespace.namespaceName
    }.headOption

  private def deleteHelper(req: DeleteWsmResourceRequest, authorization: Authorization, resource: String)(implicit
    ev: Ask[F, AppContext]
  ): F[Option[DeleteWsmResourceResult]] =
    for {
      ctx <- ev.ask
      res <- httpClient.expectOptionOr[DeleteWsmResourceResult](
        Request[F](
          method = Method.POST,
          uri = config.uri
            .withPath(
              Uri.Path
                .unsafeFromString(
                  s"/api/workspaces/v1/${req.workspaceId.value.toString}/resources/controlled/azure/${resource}/${req.resourceId.value.toString}"
                )
            ),
          entity = req.deleteRequest,
          headers = headers(authorization, ctx.traceId, true)
        )
      )(onError)
    } yield res

  private def onError(response: Response[F])(implicit ev: Ask[F, AppContext]): F[Throwable] =
    for {
      context <- ev.ask
      body <- response.bodyText.compile.foldMonoid
      _ <- logger.error(context.loggingCtx)(s"WSM call failed: $body")
      _ <- metrics.incrementCounter("wsm/errorResponse")
    } yield WsmException(context.traceId, body)

  def headers(authorization: Authorization, traceId: TraceId, withBody: Boolean): Headers = {
    val requestId = Header.Raw(CIString("X-Request-ID"), traceId.asString)
    if (withBody)
      Headers(authorization, defaultMediaType, requestId)
    else
      Headers(authorization, requestId)
  }
}
