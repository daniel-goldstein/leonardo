package org.broadinstitute.dsde.workbench.leonardo.dao

import akka.http.scaladsl.model.StatusCode
import cats.effect.Async
import cats.mtl.Ask
import cats.syntax.all._
import io.circe._
import org.broadinstitute.dsde.workbench.leonardo.AppContext
import org.broadinstitute.dsde.workbench.leonardo.model.LeoException
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization
import org.typelevel.log4cats.StructuredLogger

import scala.util.control.NoStackTrace

object HttpHailBatchDriverDAO {
  implicit val statusDecoder: Decoder[HailStatusCheckResponse] = Decoder.instance { d =>
    for {
      ok <- d.downField("ok").as[Boolean]
    } yield HailStatusCheckResponse(ok)
  }
}

class HttpHailBatchDriverDAO[F[_]](httpClient: Client[F])(implicit
  logger: StructuredLogger[F],
  F: Async[F],
  metrics: OpenTelemetryMetrics[F]
) extends HailBatchDriverDAO[F]
    with Http4sClientDsl[F] {

  override def getStatus(baseUri: Uri, authHeader: Authorization)(implicit
    ev: Ask[F, AppContext]
  ): F[Boolean] =
    for {
      _ <- metrics.incrementCounter("hail/status")
      hailBatchStatusUri = baseUri / "batch-driver"
      res <- httpClient.status(
        Request[F](
          method = Method.GET,
          uri = hailBatchStatusUri,
          headers = Headers(authHeader)
        )
      )
    } yield res.isSuccess

  private def onError(response: Response[F])(implicit ev: Ask[F, AppContext]): F[Throwable] =
    for {
      ctx <- ev.ask
      body <- response.bodyText.compile.foldMonoid
      _ <- logger.error(ctx.loggingCtx)(s"Failed to get status from the Hail Batch Driver. Body: $body")
      _ <- metrics.incrementCounter("hail/errorResponse")
    } yield HailBatchDriverStatusCheckException(ctx.traceId, body, response.status.code)
}

// API response models
final case class HailBatchDriverStatusCheckResponse(ok: Boolean) extends AnyVal

final case class HailBatchDriverStatusCheckException(traceId: TraceId, msg: String, code: StatusCode)
    extends LeoException(message = s"Hail batch driver error: $msg", statusCode = code, traceId = Some(traceId))
    with NoStackTrace